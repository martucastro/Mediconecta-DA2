package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;

// ponytail: implementacion honesta mientras no hay operacion de reclamo en el
// legado (SCRUM-90, PR #10, no mergeado: el simulador SOAP solo tiene
// validarCobertura y autorizarPrestacion). Seguimiento, una vez que mergee:
// agregar presentarReclamo al simulador y al adapter, e implementar este
// puerto con el cliente SOAP real en vez de este stub.
/**
 * Unica implementacion disponible hoy de CanalDeReclamos: informa que el
 * canal no esta disponible. Nunca envia nada ni inventa un resultado exitoso.
 */
@ApplicationScoped
public class CanalDeReclamosPendiente implements CanalDeReclamos {

    @Override
    public ResultadoReclamo presentarReclamo(Long turnoId, Long pacienteId,
            String numeroAutorizacion, BigDecimal coberturaPorcentaje) {
        throw new CanalDeReclamosNoDisponibleException(
                "El canal de reclamos a la obra social todavia no esta disponible "
                        + "(pendiente SCRUM-90).");
    }
}
