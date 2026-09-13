package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import ar.edu.uade.da2.mediconecta.usuarios.negocio.HashDeContrasena;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;

/**
 * Mecanismo de autenticación del sistema, declarado en vez de programado.
 *
 * Estas dos anotaciones son toda la autenticación de MediConecta: el contenedor
 * intercepta cada request protegido, pide credenciales, las valida contra la
 * tabla usuarios y publica el rol del usuario, que es lo que después habilita a
 * @RolesAllowed a decidir.
 *
 * Vive en el paquete usuarios porque consulta la tabla que ese componente
 * posee: la autenticación es responsabilidad de ServicioDeUsuarios, no de un
 * componente aparte.
 *
 * callerQuery devuelve la contraseña hasheada y groupsQuery el rol. En
 * MediConecta cada usuario tiene un único rol, así que groupsQuery devuelve una
 * sola fila.
 */
@ApplicationScoped
@BasicAuthenticationMechanismDefinition(realmName = "MediConecta")
@DatabaseIdentityStoreDefinition(
        dataSourceLookup = "java:/MediConectaDS",
        callerQuery = "SELECT contrasenaHash FROM usuarios WHERE email = ?",
        groupsQuery = "SELECT rol FROM usuarios WHERE email = ?",
        hashAlgorithm = HashDeContrasena.class,
        priority = 10)
public class ConfiguracionDeSeguridad {
}
