package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.util.List;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeUsuarios (stateless).
 *
 * Es el único contrato que los consumidores conocen: UsuariosResource y las
 * fachadas de los demás componentes, que resuelven aquí al usuario autenticado
 * sin tocar la tabla de usuarios. Los roles y los atributos de transacción se
 * declaran sobre la implementación; aquí se documentan.
 *
 * Todas las operaciones usan el atributo de transacción por defecto
 * (REQUIRED): se unen a la transacción de quien las invoca o abren una propia.
 */
@Local
public interface ServicioDeUsuariosLocal {

    /**
     * Da de alta un usuario con la contraseña hasheada.
     *
     * Roles: público para registrar un PACIENTE; crear un PROFESIONAL o un
     * ADMINISTRADOR exige que el caller sea ADMINISTRADOR. Transacción:
     * REQUIRED.
     *
     * Falla con DatosInvalidosException si falta un dato, si el email no tiene
     * formato válido, si un texto excede su largo o si el rol no existe; con
     * EJBAccessException si quien crea un rol distinto de PACIENTE no es
     * administrador, y con ConflictoDeNegocioException si el email ya está
     * registrado.
     */
    Usuario registrarUsuario(String nombre, String email, String rol, String contrasenaPlana);

    /**
     * Verifica email y contraseña. Devuelve el usuario si coinciden, o null si
     * no existe o la contraseña es incorrecta; no lanza excepciones de negocio.
     *
     * Roles: público (lo usa el login). Transacción: REQUIRED.
     */
    Usuario autenticar(String email, String contrasenaPlana);

    /**
     * Lectura interna por id, para otros componentes (obras sociales, historia
     * clínica). Devuelve null si el usuario no existe.
     *
     * Roles: público, sin restricción, porque la usan otras fachadas que ya
     * aplicaron su propia regla. Transacción: REQUIRED.
     */
    Usuario obtenerUsuario(Long id);

    /**
     * Consulta de un usuario por id desde la API. Un administrador consulta a
     * cualquiera; los demás roles solo a sí mismos.
     *
     * Roles: PACIENTE, PROFESIONAL y ADMINISTRADOR. Transacción: REQUIRED.
     *
     * Falla con EJBAccessException si un no administrador pide un id ajeno o
     * inexistente (la misma respuesta en ambos casos, para no revelar qué ids
     * existen). Para un administrador, un id inexistente devuelve null.
     */
    Usuario consultarUsuario(Long id);

    /**
     * Busca un usuario por email, que es el nombre con el que el contenedor
     * identifica al caller autenticado. Devuelve null si el email está vacío o
     * no corresponde a ningún usuario.
     *
     * Roles: público. Transacción: REQUIRED.
     */
    Usuario obtenerPorEmail(String email);

    /**
     * Listado completo de usuarios.
     *
     * Roles: ADMINISTRADOR; cualquier otro caller recibe EJBAccessException.
     * Transacción: REQUIRED.
     */
    List<Usuario> listarUsuarios();

    /**
     * Profesionales registrados, para que un paciente elija con quién sacar
     * turno antes de iniciar sesión.
     *
     * Roles: público. Transacción: REQUIRED.
     */
    List<Usuario> listarProfesionales();
}
