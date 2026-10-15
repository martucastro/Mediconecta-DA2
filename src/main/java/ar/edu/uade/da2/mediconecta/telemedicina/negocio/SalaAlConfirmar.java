package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnConfirmacion;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Crea la sala de video cuando se confirma un turno de telemedicina (SCRUM-95).
 *
 * Observa TurnoEnConfirmacion con prioridad SALA_DE_VIDEO, despues del cobro
 * del copago (COBRO_COPAGO), asi que ServicioDeTurnos no conoce a este
 * componente. Mismo estilo que CoberturaEnLaReserva en la reserva.
 *
 * La sala se crea al confirmar y nunca al reservar: un hold que vence no deja
 * salas colgadas. Un turno presencial sale en la primera linea, sin llamar al
 * proveedor ni escribir nada.
 *
 * Si el proveedor de video no responde, falla la confirmacion entera (decision
 * de SCRUM-95, ver docs/documento-tecnico.md, seccion 9.4): no se atrapa nada,
 * la ProveedorDeVideoNoDisponibleException (rollback=true) sale por el fire()
 * de confirmarTurno, el turno sigue EN_HOLD con su temporizador vivo, el
 * mensaje TurnoConfirmado no sale y el paciente recibe 503 y puede reintentar
 * mientras dure el hold.
 *
 * Corre en la llamada del propio paciente que confirma: crearSesion lo
 * reconoce como participante del turno, porque confirmarTurno ya verifico que
 * el hold es suyo.
 */
@ApplicationScoped
public class SalaAlConfirmar {

    @Inject
    private ServicioDeTelemedicina servicioDeTelemedicina;

    @Transactional(TxType.MANDATORY)
    public void alConfirmar(
            @Observes @Priority(PuntosDeExtension.SALA_DE_VIDEO) TurnoEnConfirmacion evento) {
        Turno turno = evento.getTurno();
        if (turno.getModalidad() != ModalidadTurno.TELEMEDICINA) {
            return;
        }
        servicioDeTelemedicina.crearSesion(turno.getId());
    }
}
