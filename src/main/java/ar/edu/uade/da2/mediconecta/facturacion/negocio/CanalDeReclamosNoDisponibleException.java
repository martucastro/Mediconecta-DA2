package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El canal hacia la obra social no respondio o fallo por un motivo ajeno al
 * reclamo (timeout, 5xx, red caida). Es transitorio: ServicioDeFacturacion lo
 * reintenta dejando que Artemis reentregue el mensaje, hasta agotar los
 * intentos configurados en mediconecta-setup.cli.
 */
@ApplicationException(rollback = true)
public class CanalDeReclamosNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CanalDeReclamosNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CanalDeReclamosNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
