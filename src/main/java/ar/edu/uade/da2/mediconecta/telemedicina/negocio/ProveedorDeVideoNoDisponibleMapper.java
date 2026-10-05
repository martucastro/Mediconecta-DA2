package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce ProveedorDeVideoNoDisponibleException a HTTP 503, igual que
 * ObraSocialNoDisponibleMapper. Retry-After le sugiere al cliente cuanto
 * esperar antes de reintentar.
 *
 * La excepcion es especifica de telemedicina, asi que su mapper vive junto a
 * ella, como en pagos y obras sociales.
 */
@Provider
public class ProveedorDeVideoNoDisponibleMapper
        implements ExceptionMapper<ProveedorDeVideoNoDisponibleException> {

    @Override
    public Response toResponse(ProveedorDeVideoNoDisponibleException excepcion) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
