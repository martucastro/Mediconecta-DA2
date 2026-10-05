package ar.edu.uade.da2.mediconecta.turnos.datos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

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
                        + "AND t.estado = :estado",
                Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.DISPONIBLE)
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
                "SELECT t FROM Turno t WHERE t.estado = :estado "
                        + "AND t.inicioHold < :limite",
                Turno.class)
                .setParameter("estado", EstadoTurno.EN_HOLD)
                .setParameter("limite", limite)
                .getResultList();
    }

    /**
     * Turnos EN_HOLD o CONFIRMADO de un paciente: lo que necesita ver en su
     * home o agenda. Los DISPONIBLE no son "suyos" y los CANCELADO no le
     * interesan una vez pasado el momento de la cancelacion.
     */
    public List<Turno> listarPorPaciente(Long pacienteId) {
        return em.createQuery(
                "SELECT t FROM Turno t WHERE t.paciente.id = :pacienteId "
                        + "AND t.estado IN :estados ORDER BY t.fechaHora",
                Turno.class)
                .setParameter("pacienteId", pacienteId)
                .setParameter("estados", List.of(EstadoTurno.EN_HOLD, EstadoTurno.CONFIRMADO))
                .getResultList();
    }

    /**
     * Todos los turnos de un profesional, en cualquier estado, opcionalmente
     * acotados a un dia puntual para armar la agenda del dia.
     *
     * A diferencia de listarPorPaciente, aca no se filtra por estado: el
     * profesional necesita ver tambien los DISPONIBLE y CANCELADO de su propia
     * agenda, no solo los que tienen un paciente asociado.
     */
    public List<Turno> listarPorProfesional(Long profesionalId, LocalDate fecha) {
        String jpql = "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId";
        if (fecha != null) {
            jpql += " AND t.fechaHora >= :inicioDia AND t.fechaHora < :finDia";
        }
        jpql += " ORDER BY t.fechaHora";

        TypedQuery<Turno> query = em.createQuery(jpql, Turno.class)
                .setParameter("profesionalId", profesionalId);
        if (fecha != null) {
            query.setParameter("inicioDia", fecha.atStartOfDay());
            query.setParameter("finDia", fecha.plusDays(1).atStartOfDay());
        }
        return query.getResultList();
    }
}
