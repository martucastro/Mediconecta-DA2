package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnCancelacion;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Cancelar un turno confirmado no tiene que llegar a la obra social
 * (SCRUM-124). Corre dentro de la transaccion de cancelarTurno; ver
 * ServicioDeFacturacion.anularReclamo.
 */
@ApplicationScoped
public class ReclamoAlCancelar {

    @Inject
    private ServicioDeFacturacion servicioDeFacturacion;

    @Transactional(TxType.MANDATORY)
    public void alCancelar(
            @Observes @Priority(PuntosDeExtension.ANULACION_RECLAMO) TurnoEnCancelacion evento) {
        servicioDeFacturacion.anularReclamo(evento.getTurno().getId());
    }
}
