package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import ar.edu.uade.da2.mediconecta.usuarios.negocio.HashDeContrasena;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;

/**
 * Mecanismo de autenticación del sistema, declarado en vez de programado
 * salvo por cómo se desafían las credenciales (ver AutenticacionBasica).
 *
 * @DatabaseIdentityStoreDefinition es toda la validación de MediConecta: el
 * contenedor valida las credenciales que le llegan contra la tabla usuarios y
 * publica el rol del usuario, que es lo que después habilita a @RolesAllowed
 * a decidir. El mecanismo Basic que antes se declaraba acá con
 * @BasicAuthenticationMechanismDefinition se reemplazó por AutenticacionBasica
 * (SCRUM-103): el estandar siempre agrega WWW-Authenticate en cada 401, lo que
 * hace que el navegador muestre su popup nativo de credenciales en vez de
 * dejar que el fetch del SPA maneje la respuesta.
 *
 * Vive en el paquete usuarios porque consulta la tabla que ese componente
 * posee: la autenticación es responsabilidad de ServicioDeUsuarios, no de un
 * componente aparte.
 *
 * callerQuery devuelve la contraseña hasheada y groupsQuery el rol. En
 * MediConecta cada usuario tiene un único rol, así que groupsQuery devuelve una
 * sola fila. Las dos normalizan el email recibido (TRIM y minúsculas, como el
 * registro y UsuarioDAO, SCRUM-132) y comparan la columna tal cual: con
 * LOWER(email) una fila vieja "ADMIN@x.com" de otra cuenta coincidiría junto
 * con "admin@x.com", y groupsQuery sumaría los roles de las dos. Una fila
 * vieja con mayúsculas deja de autenticar: falla cerrada, a propósito.
 */
@ApplicationScoped
@DatabaseIdentityStoreDefinition(
        dataSourceLookup = "java:/MediConectaDS",
        callerQuery = "SELECT contrasenaHash FROM usuarios WHERE email = LOWER(TRIM(?))",
        groupsQuery = "SELECT rol FROM usuarios WHERE email = LOWER(TRIM(?))",
        hashAlgorithm = HashDeContrasena.class,
        priority = 10)
public class ConfiguracionDeSeguridad {
}
