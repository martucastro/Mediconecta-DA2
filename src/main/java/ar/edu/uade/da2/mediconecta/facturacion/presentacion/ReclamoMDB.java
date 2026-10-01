package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.facturacion.negocio.ServicioDeFacturacion;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

/**
 * Escucha la cola ReclamosFacturacion. Solo traduce el MapMessage (JMS no es
 * negocio) y delega en ServicioDeFacturacion, igual que NotificacionMDB
 * traduce TurnoConfirmado.
 *
 * Politica de redelivery: la de mediconecta-setup.cli paso 5 (hasta 5
 * intentos, backoff 2s..30s, despues ReclamosFacturacionDLQ). Una
 * CanalDeReclamosNoDisponibleException por debajo del maximo sale de este
 * metodo sin atrapar a proposito: el contenedor no confirma el mensaje y
 * Artemis lo reentrega solo. En el ultimo intento, o ante un rechazo
 * definitivo, ServicioDeFacturacion.procesarReclamo ya no relanza (deja el
 * reclamo en EN_REVISION_MANUAL), asi que el mensaje se confirma sin que este
 * metodo tenga que hacer nada especial.
 *
 * El unico catch de aca es para un mensaje malformado (no es un MapMessage, o
 * le falta el campo): se loguea y se descarta en vez de relanzar, para no
 * quedar reintentando para siempre un mensaje que nunca va a poder leer.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/ReclamosFacturacion")
})
public class ReclamoMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(ReclamoMDB.class.getName());

    @Inject
    private ServicioDeFacturacion servicio;

    @Override
    public void onMessage(Message mensaje) {
        Long reclamoId;
        int intento;
        try {
            MapMessage mapa = (MapMessage) mensaje;
            reclamoId = mapa.getLong("reclamoId");
            intento = mensaje.getIntProperty("JMSXDeliveryCount");
        } catch (JMSException | ClassCastException e) {
            LOG.log(Level.SEVERE, "Mensaje de ReclamosFacturacion malformado, se descarta", e);
            return;
        }

        // Sin capturar a proposito: una CanalDeReclamosNoDisponibleException con
        // reintentos disponibles tiene que salir de aca (ver javadoc de la clase)
        // para que el contenedor haga rollback y Artemis reentregue.
        servicio.procesarReclamo(reclamoId, intento);
    }
}
