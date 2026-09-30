package ar.edu.uade.da2.mediconecta.notificaciones.presentacion;

import ar.edu.uade.da2.mediconecta.notificaciones.negocio.ServicioDeNotificaciones;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

/**
 * Escucha el topico TurnoConfirmado. Solo traduce el MapMessage (JMS no es
 * negocio) y delega en ServicioDeNotificaciones, igual que un Resource
 * traduce HTTP y delega en un Servicio.
 *
 * Politica de redelivery: la que trae Artemis por defecto para este topico
 * (sin configuracion propia en mediconecta-setup.cli) - 10 reintentos con
 * backoff, y despues el mensaje va a la cola de mensajes muertos (DLQ). Si
 * el procesamiento falla (por ejemplo, la base no responde), la excepcion
 * sin capturar hace que el contenedor no confirme el mensaje, y Artemis lo
 * reintenta solo, sin que este componente tenga que programar nada.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TurnoConfirmado")
})
public class NotificacionMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(NotificacionMDB.class.getName());

    @Inject
    private ServicioDeNotificaciones servicio;

    @Override
    public void onMessage(Message mensaje) {
        try {
            MapMessage mapa = (MapMessage) mensaje;
            Long turnoId = mapa.getLong("turnoId");
            Long pacienteId = mapa.getLong("pacienteId");
            String fechaHora = mapa.getString("fechaHora");

            servicio.notificarTurnoConfirmado(turnoId, pacienteId, fechaHora);
        } catch (JMSException e) {
            LOG.log(Level.SEVERE, "Error leyendo el mensaje de TurnoConfirmado", e);
            // Sin capturar mas arriba: el contenedor no confirma el mensaje
            // y Artemis lo reintenta segun su politica de redelivery.
            throw new RuntimeException(e);
        }
    }
}