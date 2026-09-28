package ar.edu.uade.da2.mediconecta.obrassociales.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class AutorizacionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(AutorizacionDePrestacion autorizacion) {
        em.persist(autorizacion);
    }

    public List<AutorizacionDePrestacion> listarPorPaciente(Long pacienteId) {
        return em.createQuery(
                "SELECT a FROM AutorizacionDePrestacion a WHERE a.pacienteId = :pacienteId"
                        + " ORDER BY a.fechaAutorizacion",
                AutorizacionDePrestacion.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
    }
}
