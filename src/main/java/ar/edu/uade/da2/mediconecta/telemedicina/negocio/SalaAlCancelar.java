package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnCancelacion;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Cancela la sala de video cuando el paciente cancela un turno confirmado de
 * telemedicina (SCRUM-132). Contraparte de SalaAlConfirmar.
 *
 * Corre dentro de la transaccion de cancelarTurno, con prioridad CIERRE_SALA:
 * antes del reembolso del copago, que es lo unico que el rollback no deshace.
 * El proveedor simulado no tiene una operacion para cerrar la sala (Jitsi la
 * crea recien cuando alguien entra), asi que alcanza con marcar la sesion.
 */
@ApplicationScoped
public class SalaAlCancelar {

    @Inject
    private ServicioDeTelemedicinaLocal servicioDeTelemedicina;

    @Transactional(TxType.MANDATORY)
    public void alCancelar(
            @Observes @Priority(PuntosDeExtension.CIERRE_SALA) TurnoEnCancelacion evento) {
        Turno turno = evento.getTurno();
        if (turno.getModalidad() != ModalidadTurno.TELEMEDICINA) {
            return;
        }
        servicioDeTelemedicina.cancelarSesion(turno.getId());
    }
}
