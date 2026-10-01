package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La obra social rechazo el reclamo de forma definitiva (por ejemplo, la
 * autorizacion no es valida). A diferencia de
 * CanalDeReclamosNoDisponibleException, reintentar un rechazo deterministico
 * no tiene sentido: ServicioDeFacturacion lo manda directo a revision manual.
 */
@ApplicationException(rollback = true)
public class ReclamoRechazadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReclamoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
