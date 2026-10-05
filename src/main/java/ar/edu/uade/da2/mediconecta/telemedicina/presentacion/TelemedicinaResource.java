package ar.edu.uade.da2.mediconecta.telemedicina.presentacion;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.telemedicina.negocio.EnlaceDeSesion;
import ar.edu.uade.da2.mediconecta.telemedicina.negocio.ServicioDeTelemedicina;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Sala de video de un turno. Las dos operaciones devuelven solo el enlace de
 * quien pregunta; un usuario ajeno al turno recibe 403 (lo decide
 * ServicioDeTelemedicina, el mapper global lo traduce).
 */
@Path("/telemedicina/turno/{turnoId}")
@RequestScoped
public class TelemedicinaResource {

    @Inject
    private ServicioDeTelemedicina servicio;

    /**
     * Crea la sala del turno (o devuelve la que ya tiene) y responde con el
     * enlace de quien la pidio. Permite crearla sin pasar por la confirmacion
     * del turno; el enganche con confirmarTurno es el paso 2/2.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response crearSesion(@PathParam("turnoId") Long turnoId) {
        servicio.crearSesion(turnoId);
        return Response.status(Response.Status.CREATED)
                .entity(new EnlaceSesionDTO(servicio.obtenerEnlace(turnoId)))
                .build();
    }

    /**
     * El enlace de quien pregunta. 404 si el turno todavia no tiene sala.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response obtenerEnlace(@PathParam("turnoId") Long turnoId) {
        EnlaceDeSesion enlace = servicio.obtenerEnlace(turnoId);
        if (enlace == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "El turno " + turnoId + " no tiene sala de video."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return Response.ok(new EnlaceSesionDTO(enlace)).build();
    }
}
