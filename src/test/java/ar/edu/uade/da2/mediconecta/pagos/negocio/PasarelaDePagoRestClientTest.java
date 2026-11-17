package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import jakarta.ws.rs.core.Response;

/**
 * La traduccion de lo que contesta la pasarela al dominio. La llamada HTTP en
 * si se verifica contra el servidor desplegado (deploy/smoke-test.sh), igual
 * que ProveedorDeVideoRestClientTest.
 */
class PasarelaDePagoRestClientTest {

    @Test
    void unErrorDelClienteEsUnRechazoYNoUnaCaida() {
        // Un 4xx dice que la pasarela entendio el pedido y no lo acepta: el
        // resultado es conocido (no se cobro), asi que no es un 502 sino un
        // rechazo, que CobroAlConfirmar responde con 409.
        for (int status : new int[] {400, 402, 404, 409, 422}) {
            ResultadoPasarela resultado = PasarelaDePagoRestClient.interpretar(
                    Response.status(status).build(), "cobrar");

            assertEquals(EstadoPago.RECHAZADO, resultado.getEstado(), "HTTP " + status);
        }
    }

    @Test
    void unErrorDelServidorSigueSiendoPasarelaNoDisponible() {
        for (int status : new int[] {500, 502, 503, 504}) {
            assertThrows(PasarelaNoDisponibleException.class,
                    () -> PasarelaDePagoRestClient.interpretar(Response.status(status).build(), "cobrar"),
                    "HTTP " + status);
        }
    }

    @Test
    void unCobroAprobadoSinIdDeTransaccionNoSeAcepta() {
        // Sin id no hay como reembolsarlo despues: antes llegaba como APROBADO
        // con null y el reembolso fallaba con un NullPointerException.
        assertThrows(PasarelaNoDisponibleException.class,
                () -> PasarelaDePagoRestClient.traducir(new PagoExternoResponse(PagoExternoResponse.APPROVED, null)));
        assertThrows(PasarelaNoDisponibleException.class,
                () -> PasarelaDePagoRestClient.traducir(new PagoExternoResponse(PagoExternoResponse.APPROVED, " ")));
    }

    @Test
    void unCobroAprobadoConIdSeTraduce() {
        ResultadoPasarela resultado = PasarelaDePagoRestClient.traducir(
                new PagoExternoResponse(PagoExternoResponse.APPROVED, "tx_1"));

        assertEquals(EstadoPago.APROBADO, resultado.getEstado());
        assertEquals("tx_1", resultado.getIdTransaccionExterna());
    }

    @Test
    void laUrlSeLeeEnCadaLlamadaParaCambiarlaSinRedesplegar() {
        String anterior = System.getProperty(PasarelaDePagoRestClient.PROPIEDAD_URL);
        try {
            System.setProperty(PasarelaDePagoRestClient.PROPIEDAD_URL, "http://127.0.0.1:1/pagos");
            assertEquals("http://127.0.0.1:1/pagos", PasarelaDePagoRestClient.urlBase());

            System.clearProperty(PasarelaDePagoRestClient.PROPIEDAD_URL);
            assertEquals("http://localhost:8080/mediconecta/api/externo/pagos",
                    PasarelaDePagoRestClient.urlBase());
        } finally {
            if (anterior == null) {
                System.clearProperty(PasarelaDePagoRestClient.PROPIEDAD_URL);
            } else {
                System.setProperty(PasarelaDePagoRestClient.PROPIEDAD_URL, anterior);
            }
        }
    }
}
