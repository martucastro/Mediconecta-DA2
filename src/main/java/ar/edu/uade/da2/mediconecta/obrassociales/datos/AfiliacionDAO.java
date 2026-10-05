package ar.edu.uade.da2.mediconecta.obrassociales.datos;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class AfiliacionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(AfiliacionDePaciente afiliacion) {
        em.persist(afiliacion);
    }

    public AfiliacionDePaciente buscarPorPaciente(Long pacienteId) {
        List<AfiliacionDePaciente> resultado = em.createQuery(
                "SELECT a FROM AfiliacionDePaciente a WHERE a.pacienteId = :pacienteId",
                AfiliacionDePaciente.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }
}
