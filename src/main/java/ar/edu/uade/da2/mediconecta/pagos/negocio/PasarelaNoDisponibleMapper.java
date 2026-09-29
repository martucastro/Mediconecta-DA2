package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce PasarelaNoDisponibleException a HTTP 502.
 *
 * A diferencia de DatosInvalidosException y ConflictoDeNegocioException, esta
 * excepcion es especifica de pagos, asi que su mapper vive junto a ella en vez
 * de en el paquete raiz.
 */
@Provider
public class PasarelaNoDisponibleMapper implements ExceptionMapper<PasarelaNoDisponibleException> {

    @Override
    public Response toResponse(PasarelaNoDisponibleException excepcion) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
