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
 * (sin configuracion propia en mediconecta-setup.cli) - hasta 10 reintentos,
 * sin demora entre uno y otro, y despues el mensaje va a la cola de mensajes
 * muertos (DLQ). Si el procesamiento falla (por ejemplo, la base no
 * responde), la excepcion sin capturar hace que el contenedor no confirme el
 * mensaje, y Artemis lo reintenta solo, sin que este componente tenga que
 * programar nada.
 *
 * Un mensaje mal formado (no es un MapMessage, le faltan claves o no se puede
 * leer) es distinto: reintentarlo nunca lo va a arreglar, asi que se descarta
 * con un aviso en el log en vez de pasar por los 10 reintentos.
 *
 * La suscripcion es no durable a proposito: quien publica el evento
 * (ServicioDeTurnos) vive en el mismo WAR, asi que el consumidor nunca esta
 * apagado mientras el publicador funciona.
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
        if (!(mensaje instanceof MapMessage)) {
            LOG.log(Level.WARNING, "TurnoConfirmado: se descarta un mensaje que no es un MapMessage ({0}).",
                    mensaje == null ? "null" : mensaje.getClass().getName());
            return;
        }

        MapMessage mapa = (MapMessage) mensaje;
        Long turnoId;
        Long pacienteId;
        String fechaHora;
        try {
            if (!mapa.itemExists("turnoId") || !mapa.itemExists("pacienteId") || !mapa.itemExists("fechaHora")) {
                LOG.warning("TurnoConfirmado: se descarta un mensaje al que le faltan turnoId, pacienteId o fechaHora.");
                return;
            }
            turnoId = mapa.getLong("turnoId");
            pacienteId = mapa.getLong("pacienteId");
            fechaHora = mapa.getString("fechaHora");
        } catch (JMSException | NumberFormatException e) {
            LOG.log(Level.WARNING, "TurnoConfirmado: se descarta un mensaje que no se puede leer.", e);
            return;
        }

        // Fuera del try: si el servicio falla (base caida, etc.) la excepcion sube,
        // el contenedor no confirma el mensaje y Artemis lo reintenta.
        servicio.notificarTurnoConfirmado(turnoId, pacienteId, fechaHora);
    }
}
