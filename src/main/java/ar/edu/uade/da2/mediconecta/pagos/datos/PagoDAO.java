package ar.edu.uade.da2.mediconecta.pagos.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
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

    /**
     * Persiste el pago en una transacción propia que se confirma de inmediato,
     * independiente de la del cobro.
     *
     * Se usa para registrar el intento antes de llamar a la pasarela: si esa
     * llamada falla y hace rollback del cobro, este INSERT ya está confirmado y el
     * rastro del pago (en estado PENDIENTE) sobrevive. Con la transacción única de
     * cobrar, el rollback se lo llevaría también.
     *
     * REQUIRES_NEW sólo tiene efecto porque la llamada cruza el proxy de este bean
     * desde ServicioDePagos; invocarlo dentro del mismo bean no activaría el
     * interceptor del contenedor.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Pago guardarEnNuevaTransaccion(Pago pago) {
        em.persist(pago);
        return pago;
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
