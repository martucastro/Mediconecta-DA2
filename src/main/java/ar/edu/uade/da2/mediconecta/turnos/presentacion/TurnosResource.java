package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.util.ArrayList;
import java.util.List;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
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
import jakarta.ws.rs.core.Response;

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
    // sí solo. La expiración del hold la gestiona el contenedor a través de
    // ExpiradorDeHolds, que es un @Singleton con TimerService.
    @Inject
    private ServicioDeTurnos servicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<TurnoDTO> consultarDisponibilidad(@QueryParam("profesionalId") Long profesionalId) {
        List<TurnoDTO> salida = new ArrayList<>();
        for (Turno turno : servicio.consultarDisponibilidad(profesionalId)) {
            salida.add(new TurnoDTO(turno));
        }
        return salida;
    }

    @POST
    @Path("/disponibilidad")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response abrirDisponibilidad(NuevaDisponibilidadRequest solicitud) {
        Turno turno = servicio.abrirDisponibilidad(solicitud.getFechaHora());
        return Response.status(Response.Status.CREATED)
                .entity(new TurnoDTO(turno))
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoDTO reservar(ReservaTurnoRequest reserva) {
        return new TurnoDTO(servicio.reservarTurno(reserva.getTurnoId()));
    }

    @PUT
    @Path("/{id}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoDTO confirmar(@PathParam("id") Long id) {
        return new TurnoDTO(servicio.confirmarTurno(id));
    }

    @PUT
    @Path("/{id}/cancelar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoDTO cancelar(@PathParam("id") Long id) {
        return new TurnoDTO(servicio.cancelarTurno(id));
    }
}
