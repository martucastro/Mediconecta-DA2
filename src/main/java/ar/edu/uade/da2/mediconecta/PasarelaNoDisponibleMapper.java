package ar.edu.uade.da2.mediconecta;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.pagos.negocio.PasarelaNoDisponibleException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce PasarelaNoDisponibleException a HTTP 502.
 *
 * La excepcion es de pagos, pero el mapper vive en el paquete raiz con los
 * demas: armar respuestas HTTP no es trabajo de la capa de negocio.
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
