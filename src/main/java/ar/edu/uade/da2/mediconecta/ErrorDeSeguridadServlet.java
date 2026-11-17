package ar.edu.uade.da2.mediconecta;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;

/**
 * Cuerpo JSON para los 401 y 403 que arma el contenedor (SCRUM-132).
 *
 * Esos rechazos no pasan por JAX-RS ni por sus mappers: los produce la capa
 * web antes de llegar al recurso (AutenticacionBasica con responseUnauthorized,
 * o un auth-constraint de web.xml que no incluye el rol del caller), y Undertow
 * respondia con su pagina HTML de error. web.xml manda aca esos dos codigos con
 * error-page. El despacho de error descarta solo el cuerpo: el encabezado
 * WWW-Authenticate que haya puesto AutenticacionBasica (o su ausencia, para el
 * SPA) llega igual al cliente.
 */
@WebServlet("/errores/seguridad")
public class ErrorDeSeguridadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static final String MENSAJE_401 = "Se requiere iniciar sesión con credenciales válidas.";

    @Override
    protected void service(HttpServletRequest pedido, HttpServletResponse respuesta) throws IOException {
        Object codigo = pedido.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int estado;
        String mensaje;
        if (Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED).equals(codigo)) {
            estado = HttpServletResponse.SC_UNAUTHORIZED;
            mensaje = MENSAJE_401;
        } else if (Integer.valueOf(HttpServletResponse.SC_FORBIDDEN).equals(codigo)) {
            estado = HttpServletResponse.SC_FORBIDDEN;
            mensaje = AccesoDenegadoMapper.MENSAJE;
        } else {
            // Pedido directo a esta URL, sin un error que la haya traido.
            estado = HttpServletResponse.SC_NOT_FOUND;
            mensaje = "No existe el recurso pedido.";
        }

        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON);
        respuesta.setCharacterEncoding("UTF-8");
        // Los mensajes son constantes sin comillas: no hace falta escaparlos.
        respuesta.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
