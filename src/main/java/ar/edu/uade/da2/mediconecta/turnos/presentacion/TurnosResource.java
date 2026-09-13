package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.turnos.negocio.DatosInvalidosException;
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
        return responder(() -> Response.status(Response.Status.CREATED)
                .entity(new TurnoDTO(servicio.abrirDisponibilidad(solicitud.getFechaHora())))
                .build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reservar(ReservaTurnoRequest reserva) {
        return responder(() -> Response.ok(
                new TurnoDTO(servicio.reservarTurno(reserva.getTurnoId()))).build());
    }

    @PUT
    @Path("/{id}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmar(@PathParam("id") Long id) {
        return responder(() -> Response.ok(new TurnoDTO(servicio.confirmarTurno(id))).build());
    }

    @PUT
    @Path("/{id}/cancelar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelar(@PathParam("id") Long id) {
        return responder(() -> Response.ok(new TurnoDTO(servicio.cancelarTurno(id))).build());
    }

    /**
     * Traduce las excepciones de negocio a codigos HTTP que digan la verdad.
     *
     * Sin esto, pedir un turno que ya no esta disponible llegaba al cliente como
     * un 500, que significa "el servidor se rompio" cuando en realidad el pedido
     * era el que no correspondia. Mismo criterio que usa HistoriaClinicaResource.
     *
     * La traduccion vive en la capa de presentacion, no en la de negocio: el
     * codigo HTTP es un detalle del transporte y el componente de negocio no
     * tiene por que saber que lo estan invocando por REST.
     */
    private Response responder(Operacion operacion) {
        try {
            return operacion.ejecutar();
        } catch (DatosInvalidosException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (ConflictoDeNegocioException e) {
            return error(Response.Status.CONFLICT, e.getMessage());
        }
    }

    private Response error(Response.Status estado, String mensaje) {
        return Response.status(estado)
                .entity(Map.of("error", mensaje))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @FunctionalInterface
    private interface Operacion {
        Response ejecutar();
    }
}
