package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.identitystore.PasswordHash;

/**
 * Adaptador entre el esquema de hash que ya usa MediConecta (PasswordUtil,
 * SHA-256 en Base64) y lo que espera Jakarta Security.
 *
 * Sin esta clase el identity store validaría con Pbkdf2 —su algoritmo por
 * defecto— y ninguna de las contraseñas ya guardadas en la tabla usuarios
 * podría verificarse. No cambia cómo se almacenan: las hace compatibles con el
 * contrato del contenedor.
 */
@ApplicationScoped
public class HashDeContrasena implements PasswordHash {

    @Override
    public void initialize(Map<String, String> parameters) {
        // Sin parámetros configurables: el algoritmo lo define PasswordUtil.
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
