package ar.edu.uade.da2.mediconecta.usuarios;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PasswordUtil {

    public static String hash(String contrasenaPlana) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(contrasenaPlana.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Error al hashear la contraseña", e);
        }
    }

    public static boolean verificar(String contrasenaPlana, String hashGuardado) {
        String hashCalculado = hash(contrasenaPlana);
        return hashCalculado.equals(hashGuardado);
    }
}