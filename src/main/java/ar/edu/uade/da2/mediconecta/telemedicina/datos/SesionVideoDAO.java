package ar.edu.uade.da2.mediconecta.telemedicina.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class SesionVideoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(SesionVideo sesion) {
        em.persist(sesion);
    }

    public SesionVideo actualizar(SesionVideo sesion) {
        return em.merge(sesion);
    }

    public SesionVideo buscarPorTurno(Long turnoId) {
        List<SesionVideo> resultado = em.createQuery(
                "SELECT s FROM SesionVideo s WHERE s.turnoId = :turnoId", SesionVideo.class)
                .setParameter("turnoId", turnoId)
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }
}
