package ar.edu.uade.da2.mediconecta.pagos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La operación es válida en sus datos pero choca con el estado actual del
 * sistema (por ejemplo, reembolsar un pago que no está aprobado).
 * Se traduce a HTTP 409.
 */
@ApplicationException(rollback = true)
public class ConflictoDeNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictoDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
