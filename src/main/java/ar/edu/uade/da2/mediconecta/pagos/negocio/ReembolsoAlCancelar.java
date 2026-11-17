package ar.edu.uade.da2.mediconecta.pagos.negocio;

import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnCancelacion;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Devuelve el copago cuando el paciente cancela un turno confirmado (SCRUM-124).
 * Contraparte de CobroAlConfirmar: corre dentro de la transaccion de
 * cancelarTurno, asi que si la pasarela falla la cancelacion entera se revierte
 * (502) y el turno sigue CONFIRMADO. Va ultimo (REEMBOLSO_COPAGO) y con el
 * turno ya CANCELADO: ver ServicioDePagos.reembolsarCopagoDelTurno.
 */
@ApplicationScoped
public class ReembolsoAlCancelar {

    @Inject
    private ServicioDePagosLocal servicioDePagos;

    @Transactional(TxType.MANDATORY)
    public void alCancelar(
            @Observes @Priority(PuntosDeExtension.REEMBOLSO_COPAGO) TurnoEnCancelacion evento) {
        servicioDePagos.reembolsarCopagoDelTurno(evento.getTurno().getId());
    }
}
