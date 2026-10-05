package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import java.time.LocalDateTime;

/**
 * Patron Adapter: el contrato que ServicioDeTelemedicina espera de cualquier
 * proveedor de video. Recibe y devuelve tipos de dominio; el formato del
 * proveedor queda del otro lado. Cambiar de proveedor es escribir otra
 * implementacion de esta interfaz.
 */
public interface ProveedorDeVideoAdapter {

    /**
     * Crea la sala de un turno.
     *
     * @throws ProveedorDeVideoNoDisponibleException si el proveedor no responde,
     *         responde con error o devuelve algo que no se puede usar.
     */
    SalaDeVideo crearSala(Long turnoId, LocalDateTime fechaHora);
}
