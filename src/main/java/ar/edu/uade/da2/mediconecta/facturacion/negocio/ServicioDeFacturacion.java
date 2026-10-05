package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.facturacion.datos.EstadoReclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.ReclamoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.MapMessage;
import jakarta.jms.Queue;

/**
 * Patron Facade: unica puerta de entrada al componente de facturacion.
 *
 * No se engancha a ServicioDeTurnos.confirmarTurno ni al observador CDI
 * PuntosDeExtension.RECLAMO: se suscribe al topico TurnoConfirmado como un
 * segundo consumidor independiente (ver TurnoConfirmadoFacturacionMDB). Asi
 * facturacion nunca toca codigo de turnos y el reclamo solo se crea para
 * confirmaciones que ya confirmaron la transaccion (el mensaje se publica
 * adentro de esa transaccion). PuntosDeExtension.RECLAMO queda sin usar a
 * proposito; no se borra la constante de Luca en este PR.
 */
@Stateless
@PermitAll
public class ServicioDeFacturacion {

    private static final Logger LOGGER = Logger.getLogger(ServicioDeFacturacion.class.getName());

    // Tiene que coincidir con max-delivery-attempts en el paso 5 de
    // deploy/mediconecta-setup.cli: es el intento (JMSXDeliveryCount) a partir
    // del cual ya no quedan reintentos de Artemis y hay que dejar de relanzar.
    public static final int MAX_INTENTOS = 5;

    @Inject
    private ReclamoDAO reclamoDAO;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private CanalDeReclamos canal;

    // JMSContext inyectado por el contenedor: participa de la misma
    // transaccion JTA que registrarReclamo, igual que en
    // ServicioDeTurnos.publicarTurnoConfirmado. Si el metodo hace rollback, el
    // mensaje nunca sale.
    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/ReclamosFacturacion")
    private Queue colaReclamos;

    /**
     * Registra el reclamo de un turno recien confirmado, si corresponde.
     *
     * Se saltea si el turno no tiene cobertura autorizada (coberturaAutorizada
     * distinto de TRUE: null todavia no se consulto, false fue rechazada) y es
     * idempotente por turnoId: TurnoConfirmadoFacturacionMDB no es durable, pero
     * una reentrega del topico (o cualquier otra forma de que esto se llame dos
     * veces para el mismo turno) no tiene que crear un segundo reclamo.
     */
    public void registrarReclamo(Long turnoId) {
        Turno turno = servicioDeTurnos.obtenerTurno(turnoId);
        if (turno == null) {
            LOGGER.log(Level.WARNING,
                    "TurnoConfirmado para un turno inexistente, se ignora: {0}", turnoId);
            return;
        }
        if (!Boolean.TRUE.equals(turno.getCoberturaAutorizada())) {
            return;
        }
        if (reclamoDAO.buscarPorTurno(turnoId) != null) {
            return;
        }

        Reclamo reclamo = new Reclamo(turnoId, turno.getPaciente().getId(),
                turno.getNumeroAutorizacion(), turno.getCoberturaPorcentaje());
        reclamoDAO.guardar(reclamo);
        publicarReclamoEncolado(reclamo);
    }

    /**
     * Procesa un reclamo pendiente contra el canal de la obra social.
     *
     * intento es el JMSXDeliveryCount que trae el mensaje (lo traduce
     * ReclamoMDB, esta fachada no conoce JMS): cuantas veces Artemis ya intento
     * entregarlo, contando esta. Exito deja el reclamo ENVIADO. Una falla
     * transitoria registra el intento en una transaccion propia (para que
     * sobreviva el rollback) y relanza para que el contenedor haga rollback y
     * Artemis reentregue, salvo que este sea el ultimo intento disponible, en
     * cuyo caso pasa a EN_REVISION_MANUAL y no relanza: no tiene sentido pedirle
     * a Artemis una reentrega que la cola ya no va a dar (va a la DLQ). Un
     * rechazo definitivo de la obra social va directo a EN_REVISION_MANUAL,
     * tambien sin relanzar: reintentar un rechazo deterministico es inutil. Un
     * reclamo ya ENVIADO o EN_REVISION_MANUAL se ignora (reentrega idempotente).
     */
    public void procesarReclamo(Long reclamoId, int intento) {
        Reclamo reclamo = reclamoDAO.buscar(reclamoId);
        if (reclamo == null) {
            LOGGER.log(Level.WARNING,
                    "Mensaje de ReclamosFacturacion para un reclamo inexistente, se ignora: {0}",
                    reclamoId);
            return;
        }
        if (reclamo.getEstado() != EstadoReclamo.PENDIENTE) {
            return;
        }

        try {
            ResultadoReclamo resultado = canal.presentarReclamo(reclamo.getTurnoId(),
                    reclamo.getPacienteId(), reclamo.getNumeroAutorizacion(),
                    reclamo.getCoberturaPorcentaje());
            reclamo.setEstado(EstadoReclamo.ENVIADO);
            reclamo.setMonto(resultado.getMonto());
            reclamo.setNumeroPresentacion(resultado.getNumeroPresentacion());
            reclamo.setActualizadoEn(LocalDateTime.now());
        } catch (ReclamoRechazadoException e) {
            reclamoDAO.registrarIntentoFallido(reclamoId, e.getMessage(), true);
        } catch (CanalDeReclamosNoDisponibleException e) {
            boolean agotado = intento >= MAX_INTENTOS;
            reclamoDAO.registrarIntentoFallido(reclamoId, e.getMessage(), agotado);
            if (!agotado) {
                throw e;
            }
        }
    }

    /** Listado completo de reclamos, para el panel del administrador. */
    @RolesAllowed(ServicioDeUsuarios.ROL_ADMINISTRADOR)
    public List<Reclamo> listarReclamos() {
        return reclamoDAO.listarTodos();
    }

    /**
     * Publica en la cola el aviso de que hay un reclamo para procesar.
     *
     * Formato minimo (MapMessage, no JSON, igual que TurnoConfirmado): solo
     * reclamoId. ReclamoMDB resuelve todo lo demas leyendo el reclamo de la
     * base, asi que el mensaje no duplica datos que ya estan persistidos.
     */
    private void publicarReclamoEncolado(Reclamo reclamo) {
        try {
            MapMessage mensaje = jmsContext.createMapMessage();
            mensaje.setLong("reclamoId", reclamo.getId());
            jmsContext.createProducer().send(colaReclamos, mensaje);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
    }
}
