package ar.edu.uade.da2.mediconecta.facturacion.datos;

/**
 * Estado de un reclamo de facturacion a la obra social.
 *
 * PENDIENTE cubre tanto el reclamo recien creado como el que todavia tiene
 * reintentos disponibles. No hay un estado intermedio "reintentando": el
 * numero de intentos y el ultimo error alcanzan para describirlo.
 */
public enum EstadoReclamo {
    PENDIENTE,
    ENVIADO,
    EN_REVISION_MANUAL
}
