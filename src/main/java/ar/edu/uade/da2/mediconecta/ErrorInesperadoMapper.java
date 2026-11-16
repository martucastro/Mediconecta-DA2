package ar.edu.uade.da2.mediconecta;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
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

    private static final int MAX_CAUSAS = 10;

    @Override
    public Response toResponse(Throwable excepcion) {
        // Las excepciones propias de JAX-RS (404, 415, etc.) ya traen su
        // respuesta armada: se dejan pasar tal cual.
        if (excepcion instanceof WebApplicationException webException) {
            return webException.getResponse();
        }

        // Un cuerpo que JSON-B no puede leer (JSON roto, un texto donde va un
        // numero, un enum o una fecha que no existen) llega como
        // ProcessingException de RESTEasy con una JsonbException como causa:
        // es un error del cliente, 400. Una ProcessingException por otra causa
        // (un cliente saliente que no conecta) es un error nuestro y sigue al 500.
        if (esErrorDeJsonb(excepcion)) {
            LOGGER.log(Level.INFO, "Cuerpo del pedido ilegible: {0}", excepcion.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error",
                            "El cuerpo del pedido no es JSON válido o tiene tipos incorrectos."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        LOGGER.log(Level.SEVERE, "Error no controlado procesando la peticion", excepcion);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error interno del servidor. Consulte el log para el detalle.")
                .type("text/plain")
                .build();
    }

    private static boolean esErrorDeJsonb(Throwable excepcion) {
        // Tope de saltos: una cadena de causas puede formar un ciclo (A -> B -> A)
        // y recorrerla sin limite colgaria el hilo del pedido.
        // ponytail: con RESTEasy la JsonbException esta a 1 o 2 saltos; si
        // apareciera mas abajo de MAX_CAUSAS, el pedido sale como 500.
        int saltos = 0;
        for (Throwable t = excepcion; t != null && saltos++ < MAX_CAUSAS; t = t.getCause()) {
            if (t instanceof JsonbException) {
                return true;
            }
        }
        return false;
    }
}
