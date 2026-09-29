package ar.edu.uade.da2.mediconecta;

import java.util.Map;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce DatosInvalidosException a HTTP 400.
 *
 * Antes cada Resource repetia este catch a mano; vive aca, junto a
 * AccesoDenegadoMapper y ErrorInesperadoMapper, porque es infraestructura
 * JAX-RS que atraviesa los cuatro componentes de negocio por igual.
 */
@Provider
public class DatosInvalidosMapper implements ExceptionMapper<DatosInvalidosException> {

    @Override
    public Response toResponse(DatosInvalidosException excepcion) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", excepcion.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
