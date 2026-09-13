package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.identitystore.PasswordHash;

/**
 * Adaptador entre el esquema de hash de MediConecta (PasswordUtil, PBKDF2 con
 * salt por usuario) y el contrato PasswordHash que espera Jakarta Security.
 *
 * Sin esta clase el identity store validaria con su implementacion Pbkdf2 por
 * defecto, que usa un formato de almacenamiento propio y distinto del nuestro,
 * asi que ninguna de las contrasenas ya guardadas en la tabla usuarios podria
 * verificarse. Delegar en PasswordUtil deja un unico lugar donde se decide como
 * se derivan y comparan las contrasenas: si manana sube el costo de iteraciones
 * o cambia el algoritmo, no hay una segunda copia de esa regla que actualizar.
 */
@ApplicationScoped
public class HashDeContrasena implements PasswordHash {

    @Override
    public void initialize(Map<String, String> parameters) {
        // Sin parametros configurables: el algoritmo lo define PasswordUtil.
    }

    @Override
    public String generate(char[] contrasena) {
        return PasswordUtil.hash(new String(contrasena));
    }

    @Override
    public boolean verify(char[] contrasena, String hashGuardado) {
        return PasswordUtil.verificar(new String(contrasena), hashGuardado);
    }
}
