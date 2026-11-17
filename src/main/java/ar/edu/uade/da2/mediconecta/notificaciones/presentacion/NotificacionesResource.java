package ar.edu.uade.da2.mediconecta.notificaciones.presentacion;

import ar.edu.uade.da2.mediconecta.notificaciones.negocio.ServicioDeNotificaciones;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoint opcional para ver las notificaciones desde Postman o el frontend,
 * sin depender del log ni de entrar a la base a mano.
 */
@Path("/notificaciones")
@RequestScoped
public class NotificacionesResource {

    @Inject
    private ServicioDeNotificaciones servicio;

    @GET
    @Path("/mias")
    @Produces(MediaType.APPLICATION_JSON)
    public List<NotificacionDTO> misNotificaciones() {
        return servicio.listarMias().stream()
                .map(NotificacionDTO::new)
                .collect(Collectors.toList());
    }
}