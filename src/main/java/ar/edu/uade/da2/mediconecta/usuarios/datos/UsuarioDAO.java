package ar.edu.uade.da2.mediconecta.usuarios.datos;

import java.util.List;

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
    
    // Sin distinguir mayusculas (SCRUM-132): el registro ya guarda en
    // minusculas, y LOWER tambien encuentra las filas anteriores a ese cambio.
    // ponytail: LOWER(email) no usa un indice comun; con muchos usuarios,
    // un indice funcional sobre lower(email).
    public Usuario buscarPorEmail(String email) {
        List<Usuario> resultado = em.createQuery(
                "SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)", Usuario.class)
                .setParameter("email", email)
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