package ar.edu.uade.da2.mediconecta.pagos.datos;

/**
 * Estado de un cobro contra la pasarela externa. PENDIENTE cubre el caso en que
 * la pasarela responde de forma asincrónica y todavía no hay resolución.
 *
 * COMPENSACION_PENDIENTE: la pasarela aprobó el cobro, la confirmación del
 * turno se revirtió después y el reembolso automático también falló
 * (CompensacionDeCobro). El dinero sigue cobrado y lo devuelve un
 * administrador.
 *
 * INCIERTO: el pedido de cobro salió hacia la pasarela, pero la respuesta no
 * llegó (timeout de lectura, conexión cortada). La pasarela pudo haber cobrado
 * y no hay id de transacción para reembolsar: un administrador lo concilia con
 * la pasarela. No es COMPENSACION_PENDIENTE porque ahí el cobro es seguro y el
 * reembolso se hace con PUT /api/pagos/{id}/reembolso, que acá no tiene con qué.
 */
public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    REEMBOLSADO,
    COMPENSACION_PENDIENTE,
    INCIERTO
}
