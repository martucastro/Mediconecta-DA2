package ar.edu.uade.da2.mediconecta;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce ObraSocialNoDisponibleException a HTTP 503.
 *
 * Sin este mapper, un legado caido saldria como 500, que es lo que el cliente
 * recibe ante un error nuestro. 503 dice lo que pasa: el servicio del que
 * dependemos no esta disponible y se puede reintentar.
 *
 * Vive en el paquete raiz con los demas mappers: armar respuestas HTTP es
 * trabajo de la presentacion, no de la capa de negocio de obras sociales.
 */
@Provider
public class ObraSocialNoDisponibleMapper implements ExceptionMapper<ObraSocialNoDisponibleException> {

    @Override
    public Response toResponse(ObraSocialNoDisponibleException excepcion) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
