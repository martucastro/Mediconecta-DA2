package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.facturacion.negocio.ServicioDeFacturacion;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

/**
 * Segundo suscriptor del topico TurnoConfirmado (el primero es
 * NotificacionMDB). No se engancha a ServicioDeTurnos.confirmarTurno ni al
 * observador CDI PuntosDeExtension.RECLAMO: ver el javadoc de
 * ServicioDeFacturacion para el porque.
 *
 * ServicioDeFacturacion.registrarReclamo necesita leer el turno a traves de
 * ServicioDeTurnos.obtenerTurno, que exige al caller uno de los roles
 * PACIENTE/PROFESIONAL/ADMINISTRADOR (@RolesAllowed de clase). Un MDB no
 * tiene un caller autenticado: @RunAs establece la identidad propagada de
 * este bean hacia las llamadas que hace, con el rol ADMINISTRADOR (el mismo
 * que ya usan los administradores para operar sobre cualquier turno). Sin
 * esta anotacion, la llamada a obtenerTurno falla con EJBAccessException.
 *
 * @PermitAll tambien hace falta, y no es descuido (mismo caso que
 * ServicioDeUsuarios, ver documento-tecnico.md seccion 6): en WildFly, con
 * default-missing-method-permissions-deny-access en true (el valor por
 * defecto del subsistema ejb3), un bean que lleva CUALQUIER anotacion de
 * jakarta.annotation.security (en este caso @RunAs) pero ningun
 * @RolesAllowed/@PermitAll explicito sobre el metodo invocado queda
 * denegado por defecto. Sin @PermitAll, Artemis nunca llega a entregar el
 * mensaje: falla con EJBAccessException "Invocation on method ... is not
 * allowed" sobre el propio onMessage, antes incluso de que @RunAs entre en
 * juego. Verificado en WildFly 41 (ver T3 en odd/tasks/scrum-97-facturacion.md).
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TurnoConfirmado")
})
@RunAs(ServicioDeUsuarios.ROL_ADMINISTRADOR)
@PermitAll
public class TurnoConfirmadoFacturacionMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(TurnoConfirmadoFacturacionMDB.class.getName());

    @Inject
    private ServicioDeFacturacion servicio;

    @Override
    public void onMessage(Message mensaje) {
        Long turnoId;
        try {
            MapMessage mapa = (MapMessage) mensaje;
            turnoId = mapa.getLong("turnoId");
        } catch (JMSException | ClassCastException e) {
            LOG.log(Level.SEVERE, "Mensaje de TurnoConfirmado malformado, se descarta", e);
            return;
        }

        servicio.registrarReclamo(turnoId);
    }
}
