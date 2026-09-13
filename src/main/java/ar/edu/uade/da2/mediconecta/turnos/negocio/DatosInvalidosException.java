package ar.edu.uade.da2.mediconecta.turnos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Lo que llego en el pedido no sirve: un turno inexistente, una fecha vacia.
 * Se traduce a 400, a diferencia del conflicto de estado que va a 409.
 */
@ApplicationException(rollback = true)
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
