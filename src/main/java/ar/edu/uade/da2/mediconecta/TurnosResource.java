package ar.edu.uade.da2.mediconecta;

import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/turnos")
@RequestScoped
public class TurnosResource {

    @Inject
    private ServicioDeTurnos servicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Turno> consultarDisponibilidad(@QueryParam("profesionalId") Long profesionalId) {
        return servicio.consultarDisponibilidad(profesionalId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Turno reservar(ReservaTurnoRequest reserva) {
        return servicio.reservarTurno(reserva.getTurnoId(), reserva.getPacienteId());
    }

    @PUT
    @Path("/{id}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public Turno confirmar(@PathParam("id") Long id) {
        return servicio.confirmarTurno(id);
    }

    @PUT
    @Path("/{id}/cancelar")
    @Produces(MediaType.APPLICATION_JSON)
    public Turno cancelar(@PathParam("id") Long id) {
        return servicio.cancelarTurno(id);
    }
}
