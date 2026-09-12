package ar.edu.uade.da2.mediconecta.historiaclinica;

import jakarta.ejb.ApplicationException;

/**
 * Datos de entrada inválidos — se traduce a HTTP 400.
 *
 * Va anotada con @ApplicationException para que el contenedor la propague tal
 * cual hasta la capa de presentación. Sin esa anotación, una RuntimeException
 * que sale de un EJB se considera "system exception" y el contenedor la envuelve
 * en EJBException, con lo que el resource ya no puede distinguirla y devolvería 500.
 * rollback = true mantiene el comportamiento transaccional: la operación se deshace.
 */
@ApplicationException(rollback = true)
public class DatosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
