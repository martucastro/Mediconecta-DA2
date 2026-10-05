package ar.edu.uade.da2.mediconecta.notificaciones.presentacion;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;
import ar.edu.uade.da2.mediconecta.notificaciones.negocio.ServicioDeNotificaciones;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.ejb.EJBAccessException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Endpoint opcional para ver las notificaciones desde Postman o el frontend,
 * sin depender del log ni de entrar a la base a mano.
 */
@Path("/notificaciones")
@RequestScoped
public class NotificacionesResource {

    @Inject
    private ServicioDeNotificaciones servicio;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Context
    private SecurityContext contexto;

    @GET
    @Path("/mias")
    @Produces(MediaType.APPLICATION_JSON)
    public List<NotificacionDTO> misNotificaciones() {
        String email = contexto.getUserPrincipal().getName();
        Usuario usuario = servicioDeUsuarios.obtenerPorEmail(email);
        if (usuario == null) {
            throw new EJBAccessException("Usuario no encontrado.");
        }
        List<Notificacion> notificaciones = servicio.listarMias(usuario.getId());
        return notificaciones.stream()
                .map(NotificacionDTO::new)
                .collect(Collectors.toList());
    }
}