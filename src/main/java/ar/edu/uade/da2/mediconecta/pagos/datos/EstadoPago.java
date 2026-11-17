package ar.edu.uade.da2.mediconecta.pagos.datos;

/**
 * Estado de un cobro contra la pasarela externa. PENDIENTE cubre el caso en que
 * la pasarela responde de forma asincrónica y todavía no hay resolución.
 *
 * COMPENSACION_PENDIENTE: la pasarela aprobó el cobro, la confirmación del
 * turno se revirtió después y el reembolso automático también falló
 * (CompensacionDeCobro). El dinero sigue cobrado y lo devuelve un
 * administrador.
 */
public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    REEMBOLSADO,
    COMPENSACION_PENDIENTE
}
