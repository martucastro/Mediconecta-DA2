package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Datos de entrada invalidos — se traduce a HTTP 400.
 *
 * Va anotada con @ApplicationException para que el contenedor la propague tal
 * cual hasta la capa de presentacion. Sin esa anotacion, una RuntimeException
 * que sale de un EJB se considera "system exception", el contenedor la envuelve
 * en EJBException, el catch del resource no la reconoce y el cliente termina
 * recibiendo un 500 por un error que en realidad es suyo.
 */
@ApplicationException(rollback = true)
public class DatosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
