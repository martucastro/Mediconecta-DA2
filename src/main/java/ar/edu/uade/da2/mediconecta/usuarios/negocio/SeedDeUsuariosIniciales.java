package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.datos.UsuarioDAO;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Siembra los usuarios iniciales cuando la base arranca vacia.
 *
 * Existe para romper un bloqueo real: registrarUsuario solo permite crear
 * PROFESIONAL o ADMINISTRADOR a quien ya esta autenticado como administrador,
 * y en una instalacion nueva no hay ninguno. Sin esta clase, una base limpia
 * deja el sistema inusable — no se puede crear un profesional, y sin
 * profesional no se puede abrir ninguna historia clinica.
 *
 * Es el unico lugar del sistema que escribe usuarios saltandose esa regla, y
 * puede hacerlo porque corre en el arranque del contenedor, antes de que exista
 * ningun pedido HTTP: no hay un caller a quien validarle permisos.
 *
 * Es @Singleton y no @Stateless porque tiene que existir una sola instancia y
 * ejecutarse una sola vez; @Startup hace que el contenedor la cree al desplegar
 * en vez de esperar a la primera invocacion.
 */
@Singleton
@Startup
public class SeedDeUsuariosIniciales {

    private static final Logger LOGGER =
            Logger.getLogger(SeedDeUsuariosIniciales.class.getName());

    private static final String CONTRASENA_POR_DEFECTO = "cambiar123";

    @Inject
    private UsuarioDAO usuarioDAO;

    /**
     * REQUIRES_NEW porque los callbacks de ciclo de vida de un Singleton corren
     * en un contexto transaccional no especificado: sin esto los insert podrian
     * quedar fuera de una transaccion.
     */
    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sembrar() {
        if (!usuarioDAO.listarTodos().isEmpty()) {
            LOGGER.info("Seed inicial: ya hay usuarios cargados, no se siembra nada.");
            return;
        }

        LOGGER.info("Seed inicial: base vacia, creando los usuarios iniciales.");

        String contrasenaAdmin = variableDeEntorno("MEDICONECTA_ADMIN_PASSWORD");
        if (contrasenaAdmin == null) {
            contrasenaAdmin = CONTRASENA_POR_DEFECTO;
            LOGGER.warning("Seed inicial: MEDICONECTA_ADMIN_PASSWORD no esta definida, "
                    + "se usa la contrasena por defecto. Cambiala antes de exponer el sistema.");
        }

        crear("Administrador", variableDeEntornoODefecto("MEDICONECTA_ADMIN_EMAIL",
                "admin@mediconecta.com"), ServicioDeUsuarios.ROL_ADMINISTRADOR, contrasenaAdmin);
        crear("Profesional de prueba", "profesional@mediconecta.com",
                ServicioDeUsuarios.ROL_PROFESIONAL, CONTRASENA_POR_DEFECTO);
        crear("Paciente de prueba", "paciente@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE, CONTRASENA_POR_DEFECTO);
    }

    private void crear(String nombre, String email, String rol, String contrasenaPlana) {
        Usuario usuario = new Usuario(nombre, email, rol, PasswordUtil.hash(contrasenaPlana));
        usuarioDAO.guardar(usuario);
        LOGGER.info("Seed inicial: creado " + email + " con rol " + rol + ".");
    }

    private String variableDeEntorno(String nombre) {
        String valor = System.getenv(nombre);
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private String variableDeEntornoODefecto(String nombre, String porDefecto) {
        String valor = variableDeEntorno(nombre);
        return valor == null ? porDefecto : valor;
    }
}
