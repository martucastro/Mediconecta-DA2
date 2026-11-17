package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
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
    void unErrorDelClienteTransitorioEsPasarelaNoDisponible() {
        // R3-pasarela-4xx-como-rechazo: 408 y 429 dicen "ahora no", no "nunca".
        // No son un rechazo del pago (409) sino una pasarela no disponible (502).
        for (int status : new int[] {408, 429}) {
            assertThrows(PasarelaNoDisponibleException.class,
                    () -> PasarelaDePagoRestClient.interpretar(Response.status(status).build(), "cobrar"),
                    "HTTP " + status);
        }
    }

    @Test
    void unCobroQueSalioYNoTuvoRespuestaEsIncierto() {
        // R3-timeout-cobro-incierto: la pasarela pudo haber cobrado.
        for (Throwable causa : new Throwable[] {
                new SocketTimeoutException("Read timed out"),
                new SocketException("Connection reset")}) {
            assertInstanceOf(CobroInciertoException.class,
                    PasarelaDePagoRestClient.sinRespuesta(new ProcessingException(causa), true),
                    causa.toString());
        }
    }

    @Test
    void unPedidoQueNoSalioNoEsIncierto() {
        // Conexion rechazada, host desconocido o timeout de conexion: la pasarela
        // no recibio nada, asi que no pudo cobrar. Un reembolso sin respuesta
        // tampoco es un cobro incierto.
        for (Throwable causa : new Throwable[] {
                new ConnectException("Connection refused"),
                new UnknownHostException("pasarela"),
                new ConnectTimeoutException(),
                new SocketTimeoutException("Connect timed out")}) {
            PasarelaNoDisponibleException e = PasarelaDePagoRestClient.sinRespuesta(
                    new ProcessingException(new ProcessingException(causa)), true);
            assertEquals(PasarelaNoDisponibleException.class, e.getClass(), causa.toString());
        }
        assertEquals(PasarelaNoDisponibleException.class, PasarelaDePagoRestClient.sinRespuesta(
                new ProcessingException(new SocketTimeoutException("Read timed out")), false).getClass());
    }

    @Test
    void conLaPasarelaCaidaElCircuitoSeAbreYFallaSinLlamarla() throws Exception {
        Invocation.Builder pedido = mock(Invocation.Builder.class);
        when(pedido.post(any())).thenAnswer(invocacion -> Response.status(503).build());
        conCliente(pedido);

        for (int i = 0; i < 3; i++) {
            assertThrows(PasarelaNoDisponibleException.class, this::cobrar);
        }
        // Circuito abierto: falla al instante con la misma excepcion (502) y sin
        // llamar a la pasarela, tanto al cobrar como al reembolsar.
        assertThrows(PasarelaNoDisponibleException.class, this::cobrar);
        assertThrows(PasarelaNoDisponibleException.class, () -> adaptador.reembolsar("tx_1"));
        verify(pedido, times(3)).post(any());
    }

    @Test
    void unRechazoDelPagoNoAbreElCircuito() throws Exception {
        Invocation.Builder pedido = mock(Invocation.Builder.class);
        when(pedido.post(any())).thenAnswer(invocacion -> Response.status(402).build());
        conCliente(pedido);

        for (int i = 0; i < 5; i++) {
            assertEquals(EstadoPago.RECHAZADO, cobrar().getEstado());
        }
        verify(pedido, times(5)).post(any());
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

    // ---- ayudas ---------------------------------------------------------------

    /** Como org.apache.http.conn.ConnectTimeoutException, que no esta en el classpath de test. */
    static class ConnectTimeoutException extends InterruptedIOException {
        private static final long serialVersionUID = 1L;
    }

    private PasarelaDePagoRestClient adaptador;

    private ResultadoPasarela cobrar() {
        return adaptador.cobrar(new BigDecimal("6000.00"), "ARS", "tok_visa_demo");
    }

    /** El Client se arma en @PostConstruct; aca se reemplaza por un doble. */
    private void conCliente(Invocation.Builder pedido) throws Exception {
        Client cliente = mock(Client.class);
        WebTarget destino = mock(WebTarget.class);
        when(cliente.target(anyString())).thenReturn(destino);
        when(destino.path(anyString())).thenReturn(destino);
        when(destino.request(MediaType.APPLICATION_JSON)).thenReturn(pedido);
        adaptador = new PasarelaDePagoRestClient();
        Field campo = PasarelaDePagoRestClient.class.getDeclaredField("cliente");
        campo.setAccessible(true);
        campo.set(adaptador, cliente);
    }
}
