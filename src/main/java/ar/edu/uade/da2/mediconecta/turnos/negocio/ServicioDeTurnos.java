package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateful;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

/**
 * Componente stateful: mantiene el hold de un turno (turnoEnCursoId + Timer activo)
 * durante la conversación reservar -> confirmar/cancelar de un mismo cliente.
 * A diferencia de ServicioDeUsuarios (stateless), acá el estado entre llamadas importa.
 */
@Stateful
public class ServicioDeTurnos {

    private static final long DURACION_HOLD_MS = 5 * 60 * 1000;

    @Resource
    private TimerService timerService;

    @Inject
    private TurnoDAO turnoDAO;

    // Facade hacia ServicioDeUsuarios: ServicioDeTurnos orquesta la validación
    // del paciente antes de reservar. Si en el futuro se suman ServicioDePagos
    // o ServicioDeNotificaciones, este es el punto donde se orquestarían.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    private Long turnoEnCursoId;

    @PostConstruct
    public void iniciar() {
        System.out.println("[ServicioDeTurnos] Instancia creada: " + this);
    }

    @PreDestroy
    public void finalizar() {
        System.out.println("[ServicioDeTurnos] Instancia destruida: " + this);
    }

    public List<Turno> consultarDisponibilidad(Long profesionalId) {
        return turnoDAO.listarDisponiblesPorProfesional(profesionalId);
    }

    public Turno reservarTurno(Long turnoId, Long pacienteId) {
        Usuario paciente = servicioDeUsuarios.obtenerUsuario(pacienteId);
        if (paciente == null) {
            throw new IllegalArgumentException("Paciente inexistente: " + pacienteId);
        }

        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.DISPONIBLE) {
            throw new IllegalStateException("El turno no está disponible");
        }

        turno.setPaciente(paciente);
        turno.setEstado(EstadoTurno.EN_HOLD);
        turno.setInicioHold(LocalDateTime.now());
        turnoDAO.actualizar(turno);

        this.turnoEnCursoId = turnoId;
        cancelarTimerActivo();
        timerService.createSingleActionTimer(DURACION_HOLD_MS, new TimerConfig(turnoId, false));

        return turno;
    }

    public Turno confirmarTurno(Long turnoId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.EN_HOLD) {
            throw new IllegalStateException("El turno no tiene un hold activo");
        }
        turno.setEstado(EstadoTurno.CONFIRMADO);
        cancelarTimerActivo();
        return turnoDAO.actualizar(turno);
    }

    public Turno cancelarTurno(Long turnoId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null) {
            throw new IllegalStateException("El turno no existe");
        }
        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setPaciente(null);
        turno.setInicioHold(null);
        cancelarTimerActivo();
        return turnoDAO.actualizar(turno);
    }

    // Expone el estado conversacional que mantiene esta instancia stateful:
    // qué turno está reteniendo esta conversación en este momento.
    public Long getTurnoEnCursoId() {
        return turnoEnCursoId;
    }

    @Timeout
    public void expirarHold(Timer timer) {
        Long turnoId = (Long) timer.getInfo();
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno != null && turno.getEstado() == EstadoTurno.EN_HOLD) {
            turno.setEstado(EstadoTurno.DISPONIBLE);
            turno.setPaciente(null);
            turno.setInicioHold(null);
            turnoDAO.actualizar(turno);
            System.out.println("[ServicioDeTurnos] Hold expirado, turno " + turnoId + " liberado");
        }
    }

    private void cancelarTimerActivo() {
        for (Timer timer : timerService.getTimers()) {
            timer.cancel();
        }
    }
}
