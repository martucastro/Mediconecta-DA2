package ar.edu.uade.da2.mediconecta.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SCRUM-134: las pantallas del frontend se sirven con index.html y status 200;
 * la API, el legado SOAP y los archivos siguen su camino con su status real.
 */
class RutasDelFrontendTest {

    private final RutasDelFrontend filtro = new RutasDelFrontend();
    private final HttpServletResponse respuesta = mock(HttpServletResponse.class);
    private final FilterChain cadena = mock(FilterChain.class);
    private final RequestDispatcher indice = mock(RequestDispatcher.class);

    private HttpServletRequest pedido(String metodo, String servletPath, String pathInfo) {
        HttpServletRequest pedido = mock(HttpServletRequest.class);
        when(pedido.getMethod()).thenReturn(metodo);
        when(pedido.getServletPath()).thenReturn(servletPath);
        when(pedido.getPathInfo()).thenReturn(pathInfo);
        when(pedido.getRequestDispatcher("/index.html")).thenReturn(indice);
        return pedido;
    }

    @ParameterizedTest
    @ValueSource(strings = { "/home", "/agenda", "/hold", "/historia", "/admin", "/historia/5" })
    void unaPantallaDelFrontendSeReenviaAIndexHtml(String ruta) throws Exception {
        HttpServletRequest pedido = pedido("GET", ruta, null);

        filtro.doFilter(pedido, respuesta, cadena);

        verify(indice).forward(pedido, respuesta);
        verifyNoInteractions(cadena);
    }

    // Sin pathInfo (columna vacia) el contenedor lo da como null.
    @ParameterizedTest
    @CsvSource({
            "/api, /turnos/mios",
            "/api, /nada",
            "/api,",
            "/legado/obrasocial,",
            "/assets/index-abc123.js,",
            "/assets/logo.svg,",
            "/favicon.ico,",
            "/index.html,",
            "/,",
    })
    void laApiElLegadoYLosArchivosConservanSuStatus(String servletPath, String pathInfo) throws Exception {
        HttpServletRequest pedido = pedido("GET", servletPath, pathInfo);

        filtro.doFilter(pedido, respuesta, cadena);

        verify(cadena).doFilter(pedido, respuesta);
        verifyNoInteractions(indice);
    }

    @Test
    void unPostAUnaPantallaNoEsNavegacionYSigueDeLargo() throws Exception {
        HttpServletRequest pedido = pedido("POST", "/home", null);

        filtro.doFilter(pedido, respuesta, cadena);

        verify(cadena).doFilter(pedido, respuesta);
        verifyNoInteractions(indice);
    }
}
