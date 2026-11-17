package ar.edu.uade.da2.mediconecta.pagos.negocio;

/**
 * Evento: la pasarela aprobo un cobro dentro de una transaccion que todavia no
 * se confirmo. Lo dispara ServicioDePagos.cobrar y lo observa
 * CompensacionDeCobro, solo si esa transaccion termina en rollback.
 *
 * Lleva ids y no la entidad Pago: el observador corre despues del rollback,
 * cuando el contexto de persistencia de la transaccion ya no existe.
 */
public record CobroAprobado(Long pagoId, Long turnoId, String idTransaccionExterna) {
}
