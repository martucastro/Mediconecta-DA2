package ar.edu.uade.da2.mediconecta.obrassociales.presentacion;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ServicioDeObrasSocialesLocal;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Capa de presentación del componente de obras sociales: recibe la petición
 * HTTP, delega en la fachada y traduce el resultado. No contiene reglas de
 * negocio.
 *
 * La autorización por rol se declara sobre la fachada ServicioDeObrasSociales
 * (registrar es de ADMINISTRADOR, consultar la propia es de PACIENTE). A nivel
 * de transporte, web.xml además exige estar autenticado para /api/afiliaciones.
 */
@Path("/afiliaciones")
@RequestScoped
public class AfiliacionesResource {

    @Inject
    private ServicioDeObrasSocialesLocal servicio;

    /**
     * Registra o corrige la afiliación de un paciente. Responde 200 en los dos
     * casos, porque la operación de la fachada es un alta o una corrección.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrar(RegistroAfiliacionRequest pedido) {
        if (pedido == null) {
            throw new DatosInvalidosException("Faltan los datos de la afiliación");
        }
        AfiliacionDePaciente afiliacion = servicio.registrarAfiliacion(
                pedido.getPacienteId(), pedido.getDni(), pedido.getNumeroAfiliado());
        return Response.ok(new AfiliacionDTO(afiliacion)).build();
    }

    /** La afiliación del paciente autenticado. 404 si no tiene obra social registrada. */
    @GET
    @Path("/mia")
    @Produces(MediaType.APPLICATION_JSON)
    public Response miAfiliacion() {
        AfiliacionDePaciente afiliacion = servicio.obtenerMiAfiliacion();
        if (afiliacion == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "El paciente no tiene una obra social registrada."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return Response.ok(new AfiliacionDTO(afiliacion)).build();
    }
}
