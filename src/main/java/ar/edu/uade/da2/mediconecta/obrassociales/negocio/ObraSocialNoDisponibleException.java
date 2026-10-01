package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La obra social no respondio, o no a tiempo. No es un error del paciente ni
 * del pedido: se puede reintentar mas tarde. Se traduce a HTTP 503.
 *
 * Es el error que ve el resto del sistema cuando el legado esta caido, en lugar
 * de una excepcion de JAX-WS o de red. El mensaje esta pensado para mostrarse
 * tal cual; el detalle tecnico queda en el log del adaptador y, como causa, en
 * la propia excepcion.
 */
@ApplicationException(rollback = true)
public class ObraSocialNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ObraSocialNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ObraSocialNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
