package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class HistoriaClinicaDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(HistoriaClinica historia) {
        em.persist(historia);
    }

    public HistoriaClinica buscarPorId(Long id) {
        return em.find(HistoriaClinica.class, id);
    }

    public HistoriaClinica buscarPorPaciente(Long pacienteId) {
        List<HistoriaClinica> resultado = em.createQuery(
                "SELECT h FROM HistoriaClinica h WHERE h.pacienteId = :pacienteId",
                HistoriaClinica.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public List<HistoriaClinica> listarTodas() {
        return em.createQuery("SELECT h FROM HistoriaClinica h", HistoriaClinica.class)
                  .getResultList();
    }
}
