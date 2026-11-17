package ar.edu.uade.da2.mediconecta.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sirve index.html con status 200 en las pantallas del frontend (SCRUM-134).
 *
 * El frontend es una SPA: /home, /agenda o /admin no existen como archivos en
 * el servidor, las resuelve el router de React. Antes las cubria una
 * error-page de 404 en web.xml, que respondia index.html pero con status 404,
 * y cada recarga dejaba un error en la consola del navegador. Este filtro las
 * reenvia a index.html antes de que el contenedor las de por inexistentes.
 *
 * Siguen de largo, con su status real (404 incluido), la API (/api), el
 * legado SOAP (/legado) y todo lo que tenga extension (los .js, .css y .svg de
 * /assets, /favicon.ico). Solo se reenvian los GET: un POST a una pantalla no
 * es navegacion. Una ruta que tampoco es una pantalla la resuelve el router
 * del frontend con su propia pagina "no encontrada".
 *
 * Corre despues de los security-constraint de web.xml, que no cubren ninguna
 * ruta del frontend: no cambia quien puede acceder a que.
 */
@WebFilter("/*")
public class RutasDelFrontend extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest pedido, HttpServletResponse respuesta,
            FilterChain cadena) throws IOException, ServletException {
        if (esPantallaDelFrontend(pedido)) {
            pedido.getRequestDispatcher("/index.html").forward(pedido, respuesta);
        } else {
            cadena.doFilter(pedido, respuesta);
        }
    }

    private static boolean esPantallaDelFrontend(HttpServletRequest pedido) {
        // servletPath + pathInfo es la ruta ya decodificada y sin el contexto
        // (/mediconecta): "/home", o "/api" + "/turnos".
        String ruta = pedido.getServletPath()
                + (pedido.getPathInfo() == null ? "" : pedido.getPathInfo());
        String ultimoTramo = ruta.substring(ruta.lastIndexOf('/') + 1);
        return "GET".equals(pedido.getMethod())
                && ruta.length() > 1 // "/" ya lo resuelve el welcome-file
                && !ultimoTramo.contains(".")
                && !(ruta + "/").startsWith("/api/")
                && !(ruta + "/").startsWith("/legado/");
    }
}
