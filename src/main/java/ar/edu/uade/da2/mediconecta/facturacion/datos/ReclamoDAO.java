package ar.edu.uade.da2.mediconecta.facturacion.datos;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

/**
 * Patron DAO: aisla el acceso a datos de los reclamos. Es lo unico que toca
 * el EntityManager; la fachada de negocio no conoce JPA.
 */
@Stateless
public class ReclamoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Reclamo reclamo) {
        em.persist(reclamo);
    }

    public Reclamo buscar(Long id) {
        return em.find(Reclamo.class, id);
    }

    public Reclamo buscarPorTurno(Long turnoId) {
        try {
            return em.createQuery(
                    "SELECT r FROM Reclamo r WHERE r.turnoId = :turnoId", Reclamo.class)
                    .setParameter("turnoId", turnoId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<Reclamo> listarTodos() {
        return em.createQuery("SELECT r FROM Reclamo r ORDER BY r.creadoEn", Reclamo.class)
                .getResultList();
    }

    /**
     * Registra un intento fallido en una transaccion propia que se confirma de
     * inmediato, independiente de la del mensaje que se esta procesando.
     *
     * Se usa tanto para un fallo transitorio que todavia tiene reintentos (el
     * reclamo sigue PENDIENTE) como para el ultimo intento agotado o un
     * rechazo definitivo (el reclamo pasa a EN_REVISION_MANUAL): en los dos
     * casos el intento y el error tienen que sobrevivir aunque el llamador
     * vuelva a lanzar la excepcion y el contenedor haga rollback de su propia
     * transaccion. Mismo patron que PagoDAO.guardarEnNuevaTransaccion.
     *
     * REQUIRES_NEW solo tiene efecto porque la llamada cruza el proxy de este
     * bean desde ServicioDeFacturacion; invocarlo dentro del mismo bean no
     * activaria el interceptor del contenedor.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarIntentoFallido(Long id, String error, boolean agotado) {
        Reclamo reclamo = em.find(Reclamo.class, id);
        reclamo.setIntentos(reclamo.getIntentos() + 1);
        reclamo.setUltimoError(error);
        reclamo.setActualizadoEn(LocalDateTime.now());
        if (agotado) {
            reclamo.setEstado(EstadoReclamo.EN_REVISION_MANUAL);
        }
    }
}
