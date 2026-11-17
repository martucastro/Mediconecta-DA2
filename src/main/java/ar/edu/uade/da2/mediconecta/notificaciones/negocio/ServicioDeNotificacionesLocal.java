package ar.edu.uade.da2.mediconecta.notificaciones.negocio;

import java.util.List;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeNotificaciones (stateless).
 *
 * Es el único contrato que los consumidores conocen: NotificacionMDB, que
 * traduce el mensaje del tópico TurnoConfirmado, y NotificacionesResource. Los
 * roles y los atributos de transacción se declaran sobre la implementación;
 * aquí se documentan.
 *
 * Todas las operaciones usan el atributo de transacción por defecto
 * (REQUIRED).
 */
@Local
public interface ServicioDeNotificacionesLocal {

    /**
     * Registra y envía (hoy, simulado con un log) el recordatorio de un turno
     * confirmado. Es idempotente por turnoId: una reentrega del mismo mensaje
     * no genera un segundo recordatorio.
     *
     * Roles: sin restricción (@PermitAll), porque la invoca un MDB que no tiene
     * un caller autenticado. Transacción: REQUIRED; se une a la transacción de
     * la entrega del mensaje, así que si falla el guardado el mensaje se
     * reentrega. No lanza excepciones de negocio.
     */
    void notificarTurnoConfirmado(Long turnoId, Long pacienteId, String fechaHora);

    /**
     * Las notificaciones del paciente autenticado. El paciente sale del caller,
     * nunca de un parámetro.
     *
     * Roles: PACIENTE. Transacción: REQUIRED. Falla con EJBAccessException si
     * el caller no corresponde a un usuario registrado.
     */
    List<Notificacion> listarMias();
}
