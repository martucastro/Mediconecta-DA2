package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
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

    /**
     * Turnos del usuario autenticado: proximo turno del paciente en su home,
     * agenda (con o sin filtro de dia) del profesional.
     *
     * "fecha" llega como String y no como LocalDate: JAX-RS solo convierte
     * @QueryParam automaticamente a tipos con un constructor o un valueOf/
     * fromString(String), y LocalDate no tiene ninguno de los dos (tiene
     * parse(CharSequence), que no cuenta). Parsearlo a mano tambien permite
     * responder 400 con un mensaje claro si el formato no es YYYY-MM-DD, en
     * vez de que WildFly lo rechace con un error generico.
     */
    @GET
    @Path("/mios")
    @Produces(MediaType.APPLICATION_JSON)
    public Response misTurnos(@QueryParam("fecha") String fechaParam) {
        LocalDate fecha = parsearFecha(fechaParam);
        List<TurnoDTO> salida = new ArrayList<>();
        for (Turno turno : servicio.misTurnos(fecha)) {
            salida.add(new TurnoDTO(turno));
        }
        return Response.ok(salida).build();
    }

    @POST
    @Path("/disponibilidad")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response abrirDisponibilidad(NuevaDisponibilidadRequest solicitud) {
        Turno turno = servicio.abrirDisponibilidad(solicitud.getFechaHora(),
                leerModalidad(solicitud.getModalidad()), solicitud.getConsultorio());
        return Response.status(Response.Status.CREATED).entity(new TurnoDTO(turno)).build();
    }

    /**
     * Traduce el texto del cuerpo al enum. Es validacion de formato, asi que
     * vive en presentacion; si no viene, se deja null y el negocio aplica el
     * default PRESENCIAL.
     */
    private ModalidadTurno leerModalidad(String modalidad) {
        if (modalidad == null || modalidad.isBlank()) {
            return null;
        }
        try {
            return ModalidadTurno.valueOf(modalidad.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("Modalidad desconocida: " + modalidad
                    + ". Valores posibles: PRESENCIAL, TELEMEDICINA");
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reservar(ReservaTurnoRequest reserva) {
        return Response.ok(new TurnoDTO(servicio.reservarTurno(reserva.getTurnoId()))).build();
    }

    @PUT
    @Path("/{id}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmar(@PathParam("id") Long id) {
        return Response.ok(new TurnoDTO(servicio.confirmarTurno(id))).build();
    }

    @PUT
    @Path("/{id}/cancelar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelar(@PathParam("id") Long id) {
        return Response.ok(new TurnoDTO(servicio.cancelarTurno(id))).build();
    }

    private LocalDate parsearFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new DatosInvalidosException("Fecha invalida, se espera YYYY-MM-DD: " + fecha);
        }
    }
}
