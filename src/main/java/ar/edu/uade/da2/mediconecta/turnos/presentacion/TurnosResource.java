package ar.edu.uade.da2.mediconecta.turnos.presentacion;

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

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;

@Path("/turnos")
@RequestScoped
public class TurnosResource {

    // Decisión de diseño (no un descuido): sin scope explícito, CDI inyecta este
    // stateful bean como @Dependent, es decir que se crea una instancia nueva por
    // cada request HTTP y la conversación reservar -> confirmar/cancelar NO la
    // sostiene el bean en memoria. Es intencional: el hold es durable en la fila
    // de Turno (estado EN_HOLD + inicioHold en la DB), no conversacional en el
    // bean. Así el turno puede confirmarse desde otra pestaña, dispositivo, o
    // incluso tras un restart del servidor, algo que @SessionScoped no daría por
    // sí solo. El @Stateful + TimerService en ServicioDeTurnos sigue demostrando
    // ciclo de vida gestionado por el contenedor para la expiración del hold.
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
