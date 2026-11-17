package ar.edu.uade.da2.mediconecta.usuarios.datos;

import java.util.List;
import java.util.Locale;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class UsuarioDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Usuario usuario) {
        em.persist(usuario);
    }

    public Usuario buscarPorId(Long id) {
        return em.find(Usuario.class, id);
    }
    
    // Sin distinguir mayusculas (SCRUM-132): se normaliza el parametro, como
    // en el registro, y la columna se compara tal cual. Pasar tambien la
    // columna a minusculas haria coincidir una fila vieja "ADMIN@x.com" (otra
    // cuenta) junto con "admin@x.com". Una fila vieja con mayusculas deja de
    // encontrarse: falla cerrada, a proposito.
    public Usuario buscarPorEmail(String email) {
        List<Usuario> resultado = em.createQuery(
                "SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email == null ? null : email.trim().toLowerCase(Locale.ROOT))
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public List<Usuario> listarTodos() {
        return em.createQuery("SELECT u FROM Usuario u", Usuario.class)
                  .getResultList();
    }

    public List<Usuario> buscarPorRol(String rol) {
        return em.createQuery(
                "SELECT u FROM Usuario u WHERE u.rol = :rol ORDER BY u.nombre", Usuario.class)
                .setParameter("rol", rol)
                .getResultList();
    }
}