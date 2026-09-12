package ar.edu.uade.da2.mediconecta;

import jakarta.ejb.EJBAccessException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Cuando @RolesAllowed rechaza una invocacion, el contenedor EJB lanza
 * EJBAccessException. Sin este mapper esa excepcion llega sin traducir a la
 * capa web y el cliente recibe un 500, que hace parecer un error del servidor
 * cuando en realidad la seguridad funciono correctamente.
 */
@Provider
public class AccesoDenegadoMapper implements ExceptionMapper<EJBAccessException> {

    @Override
    public Response toResponse(EJBAccessException excepcion) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity("No tiene permisos para realizar esta operacion")
                .type("text/plain")
                .build();
    }
}
