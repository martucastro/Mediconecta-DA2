package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
 *
 * Las contrasenas no estan escritas en el codigo. Cada una se toma de una
 * variable de entorno y, si no esta definida, se genera al azar y se escribe
 * una sola vez en el log del arranque. Una contrasena fija en el fuente es la
 * misma en todas las instalaciones y queda publicada en el repositorio; una
 * generada existe solo en esa instalacion y en ese log.
 */
@Singleton
@Startup
public class SeedDeUsuariosIniciales {

    private static final Logger LOGGER =
            Logger.getLogger(SeedDeUsuariosIniciales.class.getName());

    private static final SecureRandom ALEATORIO = new SecureRandom();

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

        List<String> generadas = new ArrayList<>();

        crear("Administrador",
                variableDeEntornoODefecto("MEDICONECTA_ADMIN_EMAIL", "admin@mediconecta.com"),
                ServicioDeUsuarios.ROL_ADMINISTRADOR,
                contrasenaDe("MEDICONECTA_ADMIN_PASSWORD", generadas));
        crear("Profesional de prueba", "profesional@mediconecta.com",
                ServicioDeUsuarios.ROL_PROFESIONAL,
                contrasenaDe("MEDICONECTA_PROFESIONAL_PASSWORD", generadas));
        crear("Paciente de prueba", "paciente@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE,
                contrasenaDe("MEDICONECTA_PACIENTE_PASSWORD", generadas));

        avisarDeLasGeneradas(generadas);
    }

    /**
     * Devuelve la contrasena de la variable de entorno indicada, o una generada
     * al azar si no esta definida. Las generadas se acumulan para informarlas
     * juntas: es la unica oportunidad de conocerlas, porque lo que se guarda en
     * la base es el hash y de ahi no se vuelve.
     */
    private String contrasenaDe(String variable, List<String> generadas) {
        String configurada = variableDeEntorno(variable);
        if (configurada != null) {
            return configurada;
        }
        byte[] material = new byte[12];
        ALEATORIO.nextBytes(material);
        String generada = Base64.getUrlEncoder().withoutPadding().encodeToString(material);
        generadas.add(variable + " = " + generada);
        return generada;
    }

    private void avisarDeLasGeneradas(List<String> generadas) {
        if (generadas.isEmpty()) {
            return;
        }
        LOGGER.warning("Seed inicial: se generaron contrasenas al azar porque las variables "
                + "de entorno correspondientes no estaban definidas. Anotalas ahora, no se "
                + "vuelven a mostrar:"
                + System.lineSeparator() + "  " + String.join(System.lineSeparator() + "  ", generadas));
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
