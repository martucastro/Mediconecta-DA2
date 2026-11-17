package ar.edu.uade.da2.mediconecta.facturacion.datos;

import java.math.BigDecimal;
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
 *
 * Los cambios de estado son UPDATE condicionales (WHERE estado = el
 * esperado) y devuelven si tocaron la fila, en vez de leer, cambiar la
 * entidad y guardarla (SCRUM-128). Asi el ReclamoMDB que presenta un reclamo
 * y la cancelacion del turno que lo anula no se pisan: si uno tiene la fila
 * tomada, el UPDATE del otro espera a que termine y vuelve a evaluar la
 * condicion sobre la fila ya confirmada (READ COMMITTED de PostgreSQL), y de
 * las dos transiciones gana una sola. Tomar el lock de la fila al leerla no
 * sirve: registrarIntentoFallido escribe la misma fila en REQUIRES_NEW con la
 * transaccion del mensaje abierta, y se esperaria a si mismo.
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
        // Solo si sigue PENDIENTE: si el turno se cancelo mientras tanto, el
        // reclamo ya esta ANULADO y no se lo devuelve a PENDIENTE.
        em.createQuery("UPDATE Reclamo r SET r.intentos = r.intentos + 1, r.ultimoError = :error, "
                + "r.actualizadoEn = :ahora, r.estado = :estado "
                + "WHERE r.id = :id AND r.estado = :pendiente")
                .setParameter("error", recortar(error))
                .setParameter("ahora", LocalDateTime.now())
                .setParameter("estado", agotado ? EstadoReclamo.EN_REVISION_MANUAL : EstadoReclamo.PENDIENTE)
                .setParameter("id", id)
                .setParameter("pendiente", EstadoReclamo.PENDIENTE)
                .executeUpdate();
    }

    /**
     * PENDIENTE a ENVIADO con lo que respondio la obra social, solo si sigue
     * PENDIENTE. false: lo anularon mientras se presentaba.
     */
    public boolean marcarEnviado(Long id, BigDecimal monto, String numeroPresentacion) {
        return em.createQuery("UPDATE Reclamo r SET r.estado = :enviado, r.monto = :monto, "
                + "r.numeroPresentacion = :numero, r.actualizadoEn = :ahora "
                + "WHERE r.id = :id AND r.estado = :pendiente")
                .setParameter("enviado", EstadoReclamo.ENVIADO)
                .setParameter("monto", monto)
                .setParameter("numero", numeroPresentacion)
                .setParameter("ahora", LocalDateTime.now())
                .setParameter("id", id)
                .setParameter("pendiente", EstadoReclamo.PENDIENTE)
                .executeUpdate() == 1;
    }

    /**
     * Pasa el reclamo de desde a hacia, con el motivo en ultimoError, solo si
     * sigue en desde. Devuelve si lo cambio.
     */
    public boolean cambiarEstado(Long id, EstadoReclamo desde, EstadoReclamo hacia, String motivo) {
        return em.createQuery("UPDATE Reclamo r SET r.estado = :hacia, r.ultimoError = :motivo, "
                + "r.actualizadoEn = :ahora WHERE r.id = :id AND r.estado = :desde")
                .setParameter("hacia", hacia)
                .setParameter("motivo", recortar(motivo))
                .setParameter("ahora", LocalDateTime.now())
                .setParameter("id", id)
                .setParameter("desde", desde)
                .executeUpdate() == 1;
    }

    private static String recortar(String texto) {
        return texto != null && texto.length() > Reclamo.MAX_ULTIMO_ERROR
                ? texto.substring(0, Reclamo.MAX_ULTIMO_ERROR) : texto;
    }
}
