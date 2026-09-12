package ar.edu.uade.da2.mediconecta.usuarios;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

@Stateless
public class ServicioDeUsuarios {

    @Inject
    private UsuarioDAO usuarioDAO;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Usuario registrarUsuario(String nombre, String email, String rol, String contrasenaPlana) {

        if (nombre == null || nombre.isBlank()
                || email == null || email.isBlank()
                || rol == null || rol.isBlank()
                || contrasenaPlana == null || contrasenaPlana.isBlank()) {
            throw new IllegalArgumentException("Todos los datos son obligatorios.");
        }

        Usuario existente = usuarioDAO.buscarPorEmail(email);
        if (existente != null) {
            throw new IllegalStateException("Ya existe un usuario registrado con ese email.");
        }

        String hash = PasswordUtil.hash(contrasenaPlana);
        Usuario nuevoUsuario = new Usuario(nombre, email, rol, hash);
        usuarioDAO.guardar(nuevoUsuario);
        return nuevoUsuario;
    }

    public Usuario autenticar(String email, String contrasenaPlana) {
        Usuario usuario = usuarioDAO.buscarPorEmail(email);
        if (usuario == null) {
            return null;
        }
        boolean coincide = PasswordUtil.verificar(contrasenaPlana, usuario.getContrasenaHash());
        return coincide ? usuario : null;
    }

    public Usuario obtenerUsuario(Long id) {
        return usuarioDAO.buscarPorId(id);
    }

    public List<Usuario> listarUsuarios() {
        return usuarioDAO.listarTodos();
    }
}