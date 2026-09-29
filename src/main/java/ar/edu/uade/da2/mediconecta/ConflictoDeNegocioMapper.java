package ar.edu.uade.da2.mediconecta;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce ConflictoDeNegocioException a HTTP 409.
 *
 * Antes cada Resource repetia este catch a mano; vive aca, junto a
 * AccesoDenegadoMapper y ErrorInesperadoMapper, porque es infraestructura
 * JAX-RS que atraviesa los cuatro componentes de negocio por igual.
 */
@Provider
public class ConflictoDeNegocioMapper implements ExceptionMapper<ConflictoDeNegocioException> {

    @Override
    public Response toResponse(ConflictoDeNegocioException excepcion) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
