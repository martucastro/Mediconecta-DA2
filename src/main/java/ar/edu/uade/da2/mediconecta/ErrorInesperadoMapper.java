package ar.edu.uade.da2.mediconecta;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Red de contencion para cualquier excepcion que no tenga un mapeo propio.
 *
 * Sin esto el mensaje crudo viaja al cliente en el cuerpo del 500: en un error
 * de Hibernate eso incluye el nombre de la tabla, la definicion de las columnas
 * y el SQL del INSERT. El detalle queda en el log del servidor, que es donde
 * tiene que estar, y el cliente recibe solo un aviso generico.
 */
@Provider
public class ErrorInesperadoMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(ErrorInesperadoMapper.class.getName());

    @Override
    public Response toResponse(Throwable excepcion) {
        // Las excepciones propias de JAX-RS (404, 415, etc.) ya traen su
        // respuesta armada: se dejan pasar tal cual.
        if (excepcion instanceof WebApplicationException webException) {
            return webException.getResponse();
        }

        LOGGER.log(Level.SEVERE, "Error no controlado procesando la peticion", excepcion);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error interno del servidor. Consulte el log para el detalle.")
                .type("text/plain")
                .build();
    }
}
