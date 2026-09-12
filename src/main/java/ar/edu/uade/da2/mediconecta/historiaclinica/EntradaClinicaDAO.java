package ar.edu.uade.da2.mediconecta.historiaclinica;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class EntradaClinicaDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(EntradaClinica entrada) {
        em.persist(entrada);
    }

    public EntradaClinica buscarPorId(Long id) {
        return em.find(EntradaClinica.class, id);
    }

    public List<EntradaClinica> listarPorHistoria(Long historiaId) {
        return em.createQuery(
                "SELECT e FROM EntradaClinica e WHERE e.historia.id = :historiaId "
                        + "ORDER BY e.fecha",
                EntradaClinica.class)
                .setParameter("historiaId", historiaId)
                .getResultList();
    }
}
