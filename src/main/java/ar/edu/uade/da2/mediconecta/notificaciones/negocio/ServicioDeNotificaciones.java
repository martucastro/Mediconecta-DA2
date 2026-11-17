package ar.edu.uade.da2.mediconecta.notificaciones.negocio;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;
import ar.edu.uade.da2.mediconecta.notificaciones.datos.NotificacionDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuariosLocal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * Arma y "envia" el recordatorio de un turno confirmado. El envio real
 * (email/SMS) todavia no existe: se simula con un log. El metodo enviar()
 * es el punto de extension pensado para un Strategy por canal el dia que
 * haya un proveedor real - cambia la implementacion de adentro, no la firma.
 *
 * @PermitAll de clase hace falta (mismo caso que ServicioDeFacturacion): en
 * WildFly, con default-missing-method-permissions-deny-access en true, el
 * @RolesAllowed de listarMias dejaria denegados los demas metodos, y
 * NotificacionMDB no podria llamar a notificarTurnoConfirmado.
 */
@Stateless
@PermitAll
public class ServicioDeNotificaciones implements ServicioDeNotificacionesLocal {

    private static final Logger LOG = Logger.getLogger(ServicioDeNotificaciones.class.getName());

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    @Inject
    private NotificacionDAO notificacionDAO;

    @Inject
    private ServicioDeUsuariosLocal servicioDeUsuarios;

    @Resource
    private SessionContext contexto;

    public void notificarTurnoConfirmado(Long turnoId, Long pacienteId, String fechaHora) {
        // JMS entrega "al menos una vez": el mismo evento puede llegar dos veces.
        // Si el turno ya tiene su recordatorio, la segunda entrega no hace nada.
        if (notificacionDAO.existePorTurno(turnoId)) {
            LOG.log(Level.INFO, "El turno {0} ya tiene su notificacion: se ignora el mensaje repetido.",
                    turnoId);
            return;
        }

        String mensaje = "Tu turno #" + turnoId + " quedo confirmado para el " + formatear(fechaHora) + ".";

        // Se registra antes de enviar: si el guardado falla, no sale ningun
        // aviso, y si el mensaje se reentrega, el registro ya existe y no se
        // vuelve a enviar.
        notificacionDAO.guardar(new Notificacion(turnoId, pacienteId, "LOG", mensaje));
        enviar(pacienteId, mensaje);
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

    private String formatear(String fechaHora) {
        if (fechaHora == null) {
            return "fecha sin informar";
        }
        try {
            return LocalDateTime.parse(fechaHora).format(FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            return fechaHora;
        }
    }

    /**
     * Las notificaciones del paciente autenticado. El paciente sale del caller
     * principal, nunca de un parametro.
     */
    @RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
    public List<Notificacion> listarMias() {
        String email = contexto.getCallerPrincipal().getName();
        Usuario paciente = servicioDeUsuarios.obtenerPorEmail(email);
        if (paciente == null) {
            throw new EJBAccessException("No se pudo resolver el usuario autenticado.");
        }
        return notificacionDAO.buscarPorPaciente(paciente.getId());
    }
}
