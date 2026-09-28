package ar.edu.uade.da2.mediconecta.pagos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La pasarela de pago externa no respondió o falló por un motivo ajeno al
 * pedido (timeout, 5xx, red caída). Se traduce a HTTP 502: el problema no es del
 * cliente ni un rechazo del pago, sino que el sistema externo no está disponible.
 */
@ApplicationException(rollback = true)
public class PasarelaNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PasarelaNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
