package ar.edu.uade.da2.mediconecta.pagos.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Patrón DAO: aísla el acceso a datos de los pagos. Es lo único que toca el
 * EntityManager; la fachada de negocio no conoce JPA.
 */
@Stateless
public class PagoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Pago pago) {
        em.persist(pago);
    }

    public Pago actualizar(Pago pago) {
        return em.merge(pago);
    }

    public Pago buscarPorId(Long id) {
        return em.find(Pago.class, id);
    }

    public List<Pago> listarPorTurno(Long turnoId) {
        return em.createQuery(
                "SELECT p FROM Pago p WHERE p.turnoId = :turnoId ORDER BY p.fecha",
                Pago.class)
                .setParameter("turnoId", turnoId)
                .getResultList();
    }
}
