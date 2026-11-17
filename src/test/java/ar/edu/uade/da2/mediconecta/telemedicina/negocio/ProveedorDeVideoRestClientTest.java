package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import jakarta.ws.rs.core.Response;

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

    @Test
    void unErrorDelClienteNoEsUnProveedorCaido() {
        // Un 4xx es un pedido que el proveedor entendio y no acepta: reintentar
        // no lo arregla, asi que no es un 503 con Retry-After sino un 409.
        for (int status : new int[] {400, 404, 409, 422}) {
            assertThrows(ConflictoDeNegocioException.class,
                    () -> ProveedorDeVideoRestClient.verificar(Response.status(status).build()),
                    "HTTP " + status);
        }
    }

    @Test
    void unErrorDelServidorSigueSiendoProveedorNoDisponible() {
        for (int status : new int[] {500, 503, 504}) {
            assertThrows(ProveedorDeVideoNoDisponibleException.class,
                    () -> ProveedorDeVideoRestClient.verificar(Response.status(status).build()),
                    "HTTP " + status);
        }
    }

    @Test
    void laUrlSeLeeEnCadaLlamadaParaCambiarlaSinRedesplegar() {
        String anterior = System.getProperty(ProveedorDeVideoRestClient.PROPIEDAD_URL);
        try {
            System.setProperty(ProveedorDeVideoRestClient.PROPIEDAD_URL, "http://127.0.0.1:1/salas");
            assertEquals("http://127.0.0.1:1/salas", ProveedorDeVideoRestClient.urlBase());
        } finally {
            if (anterior == null) {
                System.clearProperty(ProveedorDeVideoRestClient.PROPIEDAD_URL);
            } else {
                System.setProperty(ProveedorDeVideoRestClient.PROPIEDAD_URL, anterior);
            }
        }
    }

    private static SalaExternaResponse respuesta(String roomId, String hostUrl, String guestUrl) {
        SalaExternaResponse respuesta = new SalaExternaResponse();
        respuesta.setRoomId(roomId);
        respuesta.setHostUrl(hostUrl);
        respuesta.setGuestUrl(guestUrl);
        return respuesta;
    }
}
