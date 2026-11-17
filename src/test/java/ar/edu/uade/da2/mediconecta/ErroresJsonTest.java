package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.ejb.EJBAccessException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * SCRUM-132: todo error de la API sale como JSON {"error": "..."}, venga de
 * un mapper, de JAX-RS o del contenedor (los 401 y 403 de la capa web).
 */
class ErroresJsonTest {

    @Test
    void unAccesoDenegadoEs403EnJson() {
        Response respuesta = new AccesoDenegadoMapper().toResponse(new EJBAccessException("detalle interno"));

        assertEquals(403, respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", AccesoDenegadoMapper.MENSAJE), respuesta.getEntity());
    }

    @Test
    void unaExcepcionDeJaxRsSinCuerpoRecibeUnErrorJson() {
        Response respuesta = new ErrorInesperadoMapper().toResponse(new NotFoundException());

        assertEquals(404, respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "No existe el recurso pedido."), respuesta.getEntity());
    }

    @Test
    void unaExcepcionDeJaxRsConservaSusEncabezados() {
        // 405 trae Allow con los metodos validos: no hay que perderlo.
        Response respuesta = new ErrorInesperadoMapper().toResponse(new NotAllowedException("GET"));

        assertEquals(405, respuesta.getStatus());
        assertEquals("GET", respuesta.getHeaderString("Allow"));
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
    }

    @Test
    void unaExcepcionDeJaxRsConCuerpoPropioSePasaTalCual() {
        Response armada = Response.status(409).entity(Map.of("error", "propio")).build();

        assertSame(armada, new ErrorInesperadoMapper().toResponse(new jakarta.ws.rs.WebApplicationException(armada)));
    }

    @Test
    void elContenedorResponde401Y403EnJson() throws Exception {
        assertEquals("{\"error\":\"" + ErrorDeSeguridadServlet.MENSAJE_401 + "\"}", servletCon(401));
        assertEquals("{\"error\":\"" + AccesoDenegadoMapper.MENSAJE + "\"}", servletCon(403));
    }

    private static String servletCon(int estado) throws Exception {
        HttpServletRequest pedido = mock(HttpServletRequest.class);
        HttpServletResponse respuesta = mock(HttpServletResponse.class);
        StringWriter cuerpo = new StringWriter();
        when(pedido.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(estado);
        when(respuesta.getWriter()).thenReturn(new PrintWriter(cuerpo));

        new ErrorDeSeguridadServlet().service(pedido, respuesta);

        verify(respuesta).setStatus(estado);
        verify(respuesta).setContentType(MediaType.APPLICATION_JSON);
        return cuerpo.toString();
    }
}
