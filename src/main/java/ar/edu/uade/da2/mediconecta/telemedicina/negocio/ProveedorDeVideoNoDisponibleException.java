package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El proveedor de video no respondio, respondio con error o devolvio una sala
 * inutilizable. Se traduce a HTTP 503: el pedido es valido y se puede reintentar,
 * lo que falta es el sistema externo.
 *
 * rollback=true para que la transaccion de quien pidio la sala no confirme nada
 * a medias, y para que el contenedor la deje pasar sin envolverla en una
 * EJBException.
 */
@ApplicationException(rollback = true)
public class ProveedorDeVideoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProveedorDeVideoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ProveedorDeVideoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
