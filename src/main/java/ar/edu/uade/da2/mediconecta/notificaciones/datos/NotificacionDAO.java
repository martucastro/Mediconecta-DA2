package ar.edu.uade.da2.mediconecta.notificaciones.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class NotificacionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Notificacion notificacion) {
        em.persist(notificacion);
    }

    public boolean existePorTurno(Long turnoId) {
        Long cantidad = em.createQuery(
                "SELECT COUNT(n) FROM Notificacion n WHERE n.turnoId = :turnoId", Long.class)
                .setParameter("turnoId", turnoId)
                .getSingleResult();
        return cantidad > 0;
    }

    public List<Notificacion> buscarPorPaciente(Long pacienteId) {
        return em.createQuery(
                "SELECT n FROM Notificacion n WHERE n.pacienteId = :pacienteId ORDER BY n.fechaEnvio DESC",
                Notificacion.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
    }
}
