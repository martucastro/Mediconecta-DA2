package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.pagos.negocio.CobroInciertoException;
import ar.edu.uade.da2.mediconecta.pagos.negocio.PasarelaNoDisponibleException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Contrato con el cliente (coleccion de Postman, carpeta 08, y smoke test): la
 * pasarela caida es 502, tambien con el circuito abierto o un cobro incierto.
 */
class PasarelaNoDisponibleMapperTest {

    private final PasarelaNoDisponibleMapper mapper = new PasarelaNoDisponibleMapper();

    @Test
    void traduceA502ConElMensajeEnElCuerpo() {
        Response respuesta = mapper.toResponse(new PasarelaNoDisponibleException("La pasarela no responde."));

        assertEquals(Response.Status.BAD_GATEWAY.getStatusCode(), respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "La pasarela no responde."), respuesta.getEntity());
    }

    @Test
    void unCobroInciertoTambienEs502() {
        Response respuesta = mapper.toResponse(new CobroInciertoException("Sin respuesta.", null));

        assertEquals(Response.Status.BAD_GATEWAY.getStatusCode(), respuesta.getStatus());
        assertEquals(Map.of("error", "Sin respuesta."), respuesta.getEntity());
    }
}
