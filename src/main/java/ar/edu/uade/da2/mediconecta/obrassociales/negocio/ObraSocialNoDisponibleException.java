package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La obra social no respondió, o no a tiempo. No es un error del paciente ni
 * del pedido: se puede reintentar más tarde.
 *
 * Es el error que ve el resto del sistema cuando el legado está caído, en lugar
 * de una excepción de JAX-WS o de red. El mensaje está pensado para mostrarse
 * tal cual; el detalle técnico queda en el log del adaptador.
 */
@ApplicationException(rollback = true)
public class ObraSocialNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ObraSocialNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
