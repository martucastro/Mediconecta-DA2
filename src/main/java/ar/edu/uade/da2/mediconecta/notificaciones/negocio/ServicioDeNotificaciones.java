package ar.edu.uade.da2.mediconecta.notificaciones.negocio;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;
import ar.edu.uade.da2.mediconecta.notificaciones.datos.NotificacionDAO;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * Arma y "envia" el recordatorio de un turno confirmado. El envio real
 * (email/SMS) todavia no existe: se simula con un log. El metodo enviar()
 * es el punto de extension pensado para un Strategy por canal el dia que
 * haya un proveedor real - cambia la implementacion de adentro, no la firma.
 */
@Stateless
public class ServicioDeNotificaciones {

    private static final Logger LOG = Logger.getLogger(ServicioDeNotificaciones.class.getName());

    @Inject
    private NotificacionDAO notificacionDAO;

    public void notificarTurnoConfirmado(Long turnoId, Long pacienteId, String fechaHora) {
        String mensaje = "Tu turno #" + turnoId + " quedo confirmado para el " + fechaHora + ".";

        enviar(pacienteId, mensaje);

        Notificacion registro = new Notificacion(turnoId, pacienteId, "LOG", mensaje);
        notificacionDAO.guardar(registro);
    }

    /**
     * Punto de extension: hoy simula el envio con un log, porque no hay
     * proveedor de email/SMS todavia. El dia que lo haya, esto se reemplaza
     * por una estrategia real por canal (EMAIL, SMS), sin tocar el resto
     * del componente.
     */
    private void enviar(Long pacienteId, String mensaje) {
        LOG.log(Level.INFO, "Notificacion simulada para paciente {0}: {1}",
                new Object[] { pacienteId, mensaje });
    }

    public List<Notificacion> listarMias(Long pacienteId) {
        return notificacionDAO.buscarPorPaciente(pacienteId);
    }
}