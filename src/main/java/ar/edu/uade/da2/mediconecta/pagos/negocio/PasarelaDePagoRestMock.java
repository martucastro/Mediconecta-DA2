package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementación del Adapter que simula la pasarela de pago externa REST.
 *
 * Existe para que el componente corra de punta a punta sin depender de una cuenta
 * de Stripe/Mercado Pago. La estructura es la misma que tendría el cliente real:
 * acá iría un cliente JAX-RS (ClientBuilder) que hace POST al endpoint de la
 * pasarela, mapea la respuesta JSON y traduce timeouts/5xx a
 * PasarelaNoDisponibleException. Reemplazar esta clase por esa implementación no
 * obliga a tocar ServicioDePagos, porque ambas cumplen PasarelaDePagoAdapter.
 *
 * Regla del mock: aprueba salvo que el token empiece con "rechazar" (para poder
 * demostrar el flujo de rechazo en la defensa).
 */
@ApplicationScoped
public class PasarelaDePagoRestMock implements PasarelaDePagoAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(PasarelaDePagoRestMock.class.getName());

    @Override
    public ResultadoPasarela cobrar(BigDecimal monto, String moneda, String tokenMedioDePago) {
        LOGGER.info(() -> "Pasarela (mock): cobro de " + monto + " " + moneda);

        if (tokenMedioDePago != null && tokenMedioDePago.startsWith("rechazar")) {
            return new ResultadoPasarela(EstadoPago.RECHAZADO, null);
        }
        return new ResultadoPasarela(EstadoPago.APROBADO, "tx_" + UUID.randomUUID());
    }

    @Override
    public ResultadoPasarela reembolsar(String idTransaccionExterna) {
        LOGGER.info(() -> "Pasarela (mock): reembolso de " + idTransaccionExterna);
        return new ResultadoPasarela(EstadoPago.REEMBOLSADO, idTransaccionExterna);
    }
}
