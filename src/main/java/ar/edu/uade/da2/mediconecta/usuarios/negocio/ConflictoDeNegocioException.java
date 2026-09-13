package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La operacion es valida en sus datos pero choca con el estado actual del
 * sistema — por ejemplo, un email que ya esta registrado. Se traduce a 409.
 */
@ApplicationException(rollback = true)
public class ConflictoDeNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictoDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
