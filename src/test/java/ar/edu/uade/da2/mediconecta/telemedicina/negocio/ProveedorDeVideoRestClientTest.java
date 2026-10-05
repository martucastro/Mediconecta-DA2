package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * La traduccion de la respuesta del proveedor al dominio. La llamada HTTP en si
 * se verifica contra el servidor desplegado (deploy/smoke-test.sh).
 */
class ProveedorDeVideoRestClientTest {

    @Test
    void elAnfitrionEsElProfesionalYElInvitadoElPaciente() {
        SalaDeVideo sala = ProveedorDeVideoRestClient.traducir(respuesta("r1", "host", "guest"));

        assertEquals("r1", sala.salaId());
        assertEquals("host", sala.enlaceProfesional());
        assertEquals("guest", sala.enlacePaciente());
    }

    @Test
    void unaRespuestaIncompletaEsUnaFallaDelProveedor() {
        assertThrows(ProveedorDeVideoNoDisponibleException.class,
                () -> ProveedorDeVideoRestClient.traducir(null));
        assertThrows(ProveedorDeVideoNoDisponibleException.class,
                () -> ProveedorDeVideoRestClient.traducir(respuesta("r1", "host", " ")));
        assertThrows(ProveedorDeVideoNoDisponibleException.class,
                () -> ProveedorDeVideoRestClient.traducir(respuesta(null, "host", "guest")));
    }

    private static SalaExternaResponse respuesta(String roomId, String hostUrl, String guestUrl) {
        SalaExternaResponse respuesta = new SalaExternaResponse();
        respuesta.setRoomId(roomId);
        respuesta.setHostUrl(hostUrl);
        respuesta.setGuestUrl(guestUrl);
        return respuesta;
    }
}
