package ar.edu.uade.da2.mediconecta;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class ServicioDeUsuarios {

    @Inject
    private UsuarioDAO usuarioDAO;

    public Usuario registrarUsuario(String nombre, String email, String rol) {
        Usuario nuevoUsuario = new Usuario(nombre, email, rol);
        usuarioDAO.guardar(nuevoUsuario);
        return nuevoUsuario;
    }

    public Usuario obtenerUsuario(Long id) {
        return usuarioDAO.buscarPorId(id);
    }

    public List<Usuario> listarUsuarios() {
        return usuarioDAO.listarTodos();
    }
}