package ar.edu.uade.da2.mediconecta.pagos.datos;

/**
 * Estado de un cobro contra la pasarela externa. PENDIENTE cubre el caso en que
 * la pasarela responde de forma asincrónica y todavía no hay resolución.
 */
public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    REEMBOLSADO
}
