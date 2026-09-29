package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class ConflictoDeNegocioMapperTest {

    @Test
    void traduceA409ConElMensajeEnElCuerpo() {
        ConflictoDeNegocioMapper mapper = new ConflictoDeNegocioMapper();

        Response respuesta = mapper.toResponse(
                new ConflictoDeNegocioException("El turno ya no esta disponible"));

        assertEquals(Response.Status.CONFLICT.getStatusCode(), respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "El turno ya no esta disponible"), respuesta.getEntity());
    }
}
