package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class ErrorInesperadoMapperTest {

    private final ErrorInesperadoMapper mapper = new ErrorInesperadoMapper();

    @Test
    void unCuerpoQueNoSePuedeLeerComoJsonEs400() {
        // Asi llega un JSON roto o con tipos incorrectos desde RESTEasy.
        Response respuesta = mapper.toResponse(new ProcessingException(
                "RESTEASY008200: JSON Binding deserialization error",
                new JsonbException("Error deserialize JSON value into type: class java.lang.Long.")));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "El cuerpo del pedido no es JSON válido o tiene tipos incorrectos."),
                respuesta.getEntity());
    }

    @Test
    void unaJsonbExceptionSueltaTambienEs400() {
        Response respuesta = mapper.toResponse(new JsonbException("Invalid token=EOF"));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respuesta.getStatus());
    }

    @Test
    void cualquierOtraExcepcionSigueSiendo500Generico() {
        Response respuesta = mapper.toResponse(new IllegalStateException("detalle interno"));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), respuesta.getStatus());
        assertEquals("Error interno del servidor. Consulte el log para el detalle.",
                respuesta.getEntity());
    }
}
