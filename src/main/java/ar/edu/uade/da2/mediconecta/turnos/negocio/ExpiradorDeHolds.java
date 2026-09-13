package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Singleton;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;

/**
 * Responsable de expirar los holds vencidos.
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
 * La division de responsabilidades queda ademas mas limpia: ServicioDeTurnos
 * mantiene el estado conversacional del hold, y este singleton se ocupa de la
 * expiracion programada, que es una responsabilidad del contenedor y no de la
 * conversacion con un cliente.
 */
@Singleton
@PermitAll
public class ExpiradorDeHolds {

    private static final Logger LOGGER = Logger.getLogger(ExpiradorDeHolds.class.getName());

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
    public void expirar(Timer timer) {
        Long turnoId = (Long) timer.getInfo();
        Turno turno = turnoDAO.buscarPorId(turnoId);

        if (turno != null && turno.getEstado() == EstadoTurno.EN_HOLD) {
            turno.setEstado(EstadoTurno.DISPONIBLE);
            turno.setPaciente(null);
            turno.setInicioHold(null);
            turnoDAO.actualizar(turno);
            LOGGER.info("Hold vencido: el turno " + turnoId + " vuelve a estar disponible.");
        }
    }
}
