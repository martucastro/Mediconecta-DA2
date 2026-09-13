package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Responsable de liberar los turnos cuyo hold vencio.
 *
 * Por que vive separado de ServicioDeTurnos y no adentro:
 *
 * La especificacion de Jakarta Enterprise Beans NO permite crear timers sobre un
 * stateful session bean. El javadoc de jakarta.ejb.TimerService lo dice de forma
 * explicita: el servicio de timers habilita a "stateless session beans, singleton
 * session beans, message-driven beans y entity beans 2.x". Los stateful quedan
 * afuera. Tener el TimerService adentro de ServicioDeTurnos compilaba, pero
 * fallaba en tiempo de ejecucion al crear el primer timer.
 *
 * Usa DOS mecanismos, y cada uno cubre lo que al otro se le escapa:
 *
 * 1. Un temporizador por turno, preciso: libera exactamente a los cinco minutos.
 *    Es el que demuestra el ciclo de vida gestionado por el contenedor.
 * 2. Un barrido periodico, durable: recorre la base cada minuto buscando holds
 *    vencidos. Cubre el caso que el temporizador no puede cubrir, porque no es
 *    persistente: si el servidor se reinicia con turnos retenidos, la fila
 *    sobrevive pero la tarea programada no.
 *
 * Sin el barrido, un reinicio dejaba turnos EN_HOLD para siempre.
 */
@Singleton
@PermitAll
public class ExpiradorDeHolds {

    private static final Logger LOGGER = Logger.getLogger(ExpiradorDeHolds.class.getName());

    public static final long DURACION_HOLD_MS = 5 * 60 * 1000;

    @Resource
    private TimerService timerService;

    @Inject
    private TurnoDAO turnoDAO;

    /**
     * Programa la liberacion automatica de un turno retenido.
     * El id del turno viaja como info del timer para poder identificarlo despues.
     */
    public void programar(Long turnoId, long duracionMs) {
        cancelar(turnoId);
        timerService.createSingleActionTimer(duracionMs, new TimerConfig(turnoId, false));
    }

    /**
     * Cancela unicamente el timer del turno indicado.
     *
     * getTimers() devuelve todos los timers asociados a ESTE BEAN, no a una
     * conversacion ni a un turno en particular. Cancelarlos todos liberaria los
     * holds de los demas pacientes, asi que hay que filtrar por el info que se
     * guardo al programarlos.
     */
    public void cancelar(Long turnoId) {
        for (Timer timer : timerService.getTimers()) {
            if (turnoId.equals(timer.getInfo())) {
                timer.cancel();
            }
        }
    }

    @Timeout
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void expirar(Timer timer) {
        liberar((Long) timer.getInfo(), "temporizador");
    }

    /**
     * Red de seguridad: recupera los holds que quedaron sin temporizador.
     *
     * persistent=false en el @Schedule porque el propio barrido se reprograma en
     * cada arranque del contenedor; guardarlo en el almacen de timers solo
     * duplicaria la tarea en cada redespliegue.
     */
    @Schedule(hour = "*", minute = "*", second = "0", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void barrerHoldsVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusNanos(DURACION_HOLD_MS * 1_000_000);
        List<Turno> vencidos = turnoDAO.listarHoldsVencidos(limite);
        for (Turno turno : vencidos) {
            liberar(turno.getId(), "barrido");
        }
    }

    private void liberar(Long turnoId, String origen) {
        Turno turno = turnoDAO.buscarParaActualizar(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.EN_HOLD) {
            return;
        }
        turno.setEstado(EstadoTurno.DISPONIBLE);
        turno.setPaciente(null);
        turno.setInicioHold(null);
        turnoDAO.actualizar(turno);
        LOGGER.info("Hold vencido (" + origen + "): el turno " + turnoId
                + " vuelve a estar disponible.");
    }
}
