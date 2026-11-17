package ar.edu.uade.da2.mediconecta;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.usuarios.negocio.DemasiadosIntentosException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce DemasiadosIntentosException a HTTP 429 Too Many Requests.
 */
@Provider
public class DemasiadosIntentosMapper implements ExceptionMapper<DemasiadosIntentosException> {

    @Override
    public Response toResponse(DemasiadosIntentosException excepcion) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
