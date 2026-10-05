package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.math.BigDecimal;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ResultadoPresentacion;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ServicioDeObrasSociales;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Unica implementacion de CanalDeReclamos: presenta el reclamo ante la obra
 * social a traves de la fachada ServicioDeObrasSociales (SCRUM-90, ya
 * mergeado), y traduce sus excepciones a las del puerto de facturacion.
 *
 * CDI simple (@ApplicationScoped), no EJB: ServicioDeObrasSociales.presentarReclamo
 * ya es NOT_SUPPORTED, asi que no hay ninguna transaccion propia que proteger
 * aca, y un @Stateless hubiera dejado dos beans elegibles para CanalDeReclamos
 * mientras existiera CanalDeReclamosPendiente (ambiguedad de CDI); ahora que
 * la reemplaza, sigue siendo el tipo mas simple que resuelve el puerto.
 */
@ApplicationScoped
public class CanalDeReclamosSoap implements CanalDeReclamos {

    @Inject
    private ServicioDeObrasSociales servicioDeObrasSociales;

    @Override
    public ResultadoReclamo presentarReclamo(Long turnoId, Long pacienteId,
            String numeroAutorizacion, BigDecimal coberturaPorcentaje) {
        try {
            ResultadoPresentacion resultado = servicioDeObrasSociales.presentarReclamo(pacienteId, numeroAutorizacion);
            return new ResultadoReclamo(resultado.monto(), resultado.numeroPresentacion());
        } catch (DatosInvalidosException e) {
            throw new ReclamoRechazadoException(e.getMessage());
        } catch (ObraSocialNoDisponibleException e) {
            throw new CanalDeReclamosNoDisponibleException(e.getMessage(), e);
        }
    }
}
