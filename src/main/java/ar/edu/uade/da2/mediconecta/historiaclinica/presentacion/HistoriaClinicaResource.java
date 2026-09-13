package ar.edu.uade.da2.mediconecta.historiaclinica.presentacion;

import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.NuevaEntradaDTO;
import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.RegistrarConsultaDTO;
import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.ServicioDeHistoriaClinicaLocal;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Capa de presentación del componente: recibe la petición HTTP, delega en la
 * fachada y traduce el resultado. No contiene ninguna regla de negocio.
 *
 * Las anotaciones de seguridad (@RolesAllowed) van sobre estas operaciones
 * cuando se resuelva la autenticación; hoy el endpoint queda abierto.
 */
@Path("/historias")
@ApplicationScoped
public class HistoriaClinicaResource {

    @Inject
    private ServicioDeHistoriaClinicaLocal servicio;

    @POST
    @Path("/paciente/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response crear(@PathParam("pacienteId") Long pacienteId) {
        try {
            HistoriaClinica historia = servicio.crearHistoria(pacienteId);
            return Response.status(Response.Status.CREATED)
                    .entity(new HistoriaClinicaDTO(historia))
                    .build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (ConflictoDeNegocioException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/paciente/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response obtener(@PathParam("pacienteId") Long pacienteId) {
        try {
            HistoriaClinica historia = servicio.obtenerHistoriaDePaciente(pacienteId);
            if (historia == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("El paciente no tiene una historia clínica abierta")
                        .build();
            }
            return Response.ok(new HistoriaClinicaDTO(historia)).build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/paciente/{pacienteId}/entradas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response agregarEntrada(@PathParam("pacienteId") Long pacienteId,
                                   NuevaEntradaDTO datos) {
        try {
            EntradaClinica entrada = servicio.agregarEntrada(pacienteId, datos);
            return Response.status(Response.Status.CREATED)
                    .entity(new EntradaClinicaDTO(entrada))
                    .build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (ConflictoDeNegocioException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/paciente/{pacienteId}/consultas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrarConsulta(@PathParam("pacienteId") Long pacienteId,
                                      RegistrarConsultaDTO datos) {
        try {
            HistoriaClinica historia = servicio.registrarConsulta(pacienteId, datos);
            return Response.status(Response.Status.CREATED)
                    .entity(new HistoriaClinicaDTO(historia))
                    .build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (ConflictoDeNegocioException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/paciente/{pacienteId}/entradas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarEntradas(@PathParam("pacienteId") Long pacienteId) {
        try {
            List<EntradaClinicaDTO> entradas = servicio.listarEntradas(pacienteId)
                    .stream()
                    .map(EntradaClinicaDTO::new)
                    .collect(Collectors.toList());
            return Response.ok(entradas).build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
