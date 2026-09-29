package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class DatosInvalidosMapperTest {

    @Test
    void traduceA400ConElMensajeEnElCuerpo() {
        DatosInvalidosMapper mapper = new DatosInvalidosMapper();

        Response respuesta = mapper.toResponse(new DatosInvalidosException("Falta la fecha y hora de la franja"));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "Falta la fecha y hora de la franja"), respuesta.getEntity());
    }
}
