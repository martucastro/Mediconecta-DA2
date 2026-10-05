package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.math.BigDecimal;

/**
 * Patron Adapter (puerto): uniforma la presentacion de reclamos ante la obra
 * social. La fachada habla contra esta interfaz, no contra un proveedor
 * concreto. No recibe la entidad Reclamo para no filtrar JPA a traves del
 * puerto, igual que PasarelaDePagoAdapter no recibe Pago.
 */
public interface CanalDeReclamos {

    /**
     * Presenta el reclamo ante la obra social.
     *
     * @throws CanalDeReclamosNoDisponibleException si el canal no responde o
     *         falla por un motivo ajeno al reclamo (transitorio, reintentable).
     * @throws ReclamoRechazadoException si la obra social lo rechaza de forma
     *         definitiva (no reintentable).
     */
    ResultadoReclamo presentarReclamo(Long turnoId, Long pacienteId, String numeroAutorizacion,
            BigDecimal coberturaPorcentaje);
}
