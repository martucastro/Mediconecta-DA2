package ar.edu.uade.da2.mediconecta.turnos.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
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

    public List<Turno> listarDisponiblesPorProfesional(Long profesionalId) {
        return em.createQuery(
                "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                        + "AND t.estado = ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno.DISPONIBLE",
                Turno.class)
                .setParameter("profesionalId", profesionalId)
                .getResultList();
    }
}
