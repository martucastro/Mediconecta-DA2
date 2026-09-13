package ar.edu.uade.da2.mediconecta.turnos.datos;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Stateless
public class TurnoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Turno turno) {
        em.persist(turno);
    }

    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }

    public Turno buscarPorId(Long id) {
        return em.find(Turno.class, id);
    }

    /**
     * Busca el turno tomando un lock de escritura sobre la fila.
     *
     * Sin esto, dos pacientes que reservan el mismo turno en el mismo instante
     * leen ambos estado DISPONIBLE, ambos pasan la validacion y el segundo merge
     * pisa al primero: los dos creen tener el hold. El lock pesimista serializa
     * a los que compiten por la misma fila, asi que el segundo lee el estado ya
     * actualizado y su validacion falla como corresponde.
     *
     * Se usa solo en reservar, confirmar y cancelar. Las consultas de lectura
     * siguen usando buscarPorId sin lock, porque no deciden nada.
     */
    public Turno buscarParaActualizar(Long id) {
        return em.find(Turno.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public List<Turno> listarDisponiblesPorProfesional(Long profesionalId) {
        return em.createQuery(
                "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                        + "AND t.estado = ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno.DISPONIBLE",
                Turno.class)
                .setParameter("profesionalId", profesionalId)
                .getResultList();
    }

    /**
     * Turnos retenidos cuyo hold ya vencio.
     *
     * Lo usa el barrido de ExpiradorDeHolds para recuperar los holds que
     * quedaron huerfanos: el temporizador del contenedor no es persistente, asi
     * que un reinicio del servidor se lleva la tarea programada aunque la fila
     * sobreviva.
     */
    public List<Turno> listarHoldsVencidos(LocalDateTime limite) {
        return em.createQuery(
                "SELECT t FROM Turno t WHERE t.estado = "
                        + "ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno.EN_HOLD "
                        + "AND t.inicioHold < :limite",
                Turno.class)
                .setParameter("limite", limite)
                .getResultList();
    }
}
