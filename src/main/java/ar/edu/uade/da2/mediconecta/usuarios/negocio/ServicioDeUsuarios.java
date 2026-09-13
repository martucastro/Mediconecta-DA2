package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.datos.UsuarioDAO;

import java.util.List;
import java.util.Set;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

// @PermitAll a nivel de clase: WildFly deniega por defecto todo metodo sin
// permiso declarado en cuanto el bean tiene alguna anotacion de seguridad
// (default-missing-method-permissions-deny-access). Sin esto, anotar solo
// listarUsuarios rompe el registro y el login. Los metodos que necesitan
// restriccion la declaran individualmente y sobreescriben este permiso.
@PermitAll
@Stateless
public class ServicioDeUsuarios {

    public static final String ROL_PACIENTE = "PACIENTE";
    public static final String ROL_PROFESIONAL = "PROFESIONAL";
    public static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private static final Set<String> ROLES_VALIDOS =
            Set.of(ROL_PACIENTE, ROL_PROFESIONAL, ROL_ADMINISTRADOR);

    @Inject
    private UsuarioDAO usuarioDAO;

    // Permite saber quien invoca. Hace falta porque esta regla depende de que
    // rol se esta pidiendo, y eso @RolesAllowed no lo puede expresar.
    @Resource
    private SessionContext contexto;

    /**
     * Alta de usuario.
     *
     * El registro es publico, pero solo para pacientes: crear un PROFESIONAL o
     * un ADMINISTRADOR exige estar autenticado como administrador. Sin esta
     * regla cualquiera se autoasigna el rol mas alto, porque el valor de la
     * columna rol es literalmente el rol de seguridad que despues lee el
     * identity store.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Usuario registrarUsuario(String nombre, String email, String rol, String contrasenaPlana) {

        if (nombre == null || nombre.isBlank()
                || email == null || email.isBlank()
                || rol == null || rol.isBlank()
                || contrasenaPlana == null || contrasenaPlana.isBlank()) {
            throw new DatosInvalidosException("Todos los datos son obligatorios.");
        }

        if (!ROLES_VALIDOS.contains(rol)) {
            throw new DatosInvalidosException(
                    "Rol invalido: " + rol + ". Debe ser uno de " + ROLES_VALIDOS + ".");
        }

        if (!ROL_PACIENTE.equals(rol) && !contexto.isCallerInRole(ROL_ADMINISTRADOR)) {
            throw new EJBAccessException(
                    "Solo un administrador puede crear usuarios con rol " + rol + ".");
        }

        Usuario existente = usuarioDAO.buscarPorEmail(email);
        if (existente != null) {
            throw new ConflictoDeNegocioException("Ya existe un usuario registrado con ese email.");
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

    /**
     * Busca por email, que es el nombre con el que el contenedor identifica al
     * usuario autenticado. Lo necesitan otros componentes para resolver "quien
     * es el que esta haciendo este pedido" sin tocar la tabla de usuarios.
     */
    public Usuario obtenerPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return usuarioDAO.buscarPorEmail(email);
    }

    @RolesAllowed("ADMINISTRADOR")
    public List<Usuario> listarUsuarios() {
        return usuarioDAO.listarTodos();
    }
}