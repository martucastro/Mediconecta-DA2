package ar.edu.uade.da2.mediconecta.pagos.negocio;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;

/**
 * Respuesta de la pasarela ya traducida al dominio de MediConecta. El Adapter
 * devuelve esto, no el JSON crudo del proveedor: la fachada no conoce el formato
 * externo.
 */
public class ResultadoPasarela {

    private final EstadoPago estado;
    private final String idTransaccionExterna;

    public ResultadoPasarela(EstadoPago estado, String idTransaccionExterna) {
        this.estado = estado;
        this.idTransaccionExterna = idTransaccionExterna;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getIdTransaccionExterna() {
        return idTransaccionExterna;
    }
}
