package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class ObraSocialNoDisponibleMapperTest {

    @Test
    void traduceA503ConElMensajeEnElCuerpo() {
        ObraSocialNoDisponibleMapper mapper = new ObraSocialNoDisponibleMapper();

        Response respuesta = mapper.toResponse(
                new ObraSocialNoDisponibleException("El sistema de la obra social no respondió."));

        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "El sistema de la obra social no respondió."), respuesta.getEntity());
    }
}
