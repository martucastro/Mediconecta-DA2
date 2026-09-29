package ar.edu.uade.da2.mediconecta.comun.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Datos de entrada invalidos — se traduce a HTTP 400.
 *
 * Va anotada con @ApplicationException para que el contenedor la propague tal
 * cual hasta la capa de presentacion. Sin esa anotacion, una RuntimeException
 * que sale de un EJB se considera "system exception" y el contenedor la
 * envuelve en EJBException, con lo que el mapper ya no podria distinguirla y
 * el cliente recibiria un 500 por un error que en realidad es suyo.
 * rollback = true mantiene el comportamiento transaccional: la operacion se
 * deshace.
 *
 * Compartida por los cuatro componentes de negocio (usuarios, turnos,
 * historia clinica y pagos): antes cada uno tenia su propia copia identica.
 */
@ApplicationException(rollback = true)
public class DatosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
