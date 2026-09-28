package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;

/**
 * Patrón Adapter: uniforma la comunicación con la pasarela de pago externa
 * (sistema moderno vía REST). La fachada de negocio habla contra esta interfaz,
 * no contra un proveedor concreto; cambiar de Stripe a Mercado Pago, o del mock
 * al proveedor real, es cambiar la implementación sin tocar ServicioDePagos.
 */
public interface PasarelaDePagoAdapter {

    /**
     * Ejecuta el cobro contra la pasarela y traduce su respuesta al dominio.
     *
     * @throws PasarelaNoDisponibleException si la pasarela no responde o falla
     *         por un motivo ajeno al pedido (timeout, 5xx, red).
     */
    ResultadoPasarela cobrar(BigDecimal monto, String moneda, String tokenMedioDePago);

    /**
     * Solicita el reembolso de un cobro ya realizado, identificado por la
     * referencia que la pasarela devolvió al cobrar.
     */
    ResultadoPasarela reembolsar(String idTransaccionExterna);
}
