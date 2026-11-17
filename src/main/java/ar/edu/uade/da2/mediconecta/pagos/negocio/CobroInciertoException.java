package ar.edu.uade.da2.mediconecta.pagos.negocio;

/**
 * El pedido de cobro salió hacia la pasarela y la respuesta no llegó (timeout
 * de lectura, conexión cortada): no se sabe si la pasarela cobró.
 *
 * Para el cliente HTTP es lo mismo que una pasarela caída (502, por el mapper
 * de PasarelaNoDisponibleException) y también cuenta como falla para el
 * circuit breaker. ServicioDePagos la distingue para dejar el pago INCIERTO.
 */
public class CobroInciertoException extends PasarelaNoDisponibleException {

    private static final long serialVersionUID = 1L;

    public CobroInciertoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
