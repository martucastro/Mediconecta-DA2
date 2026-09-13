package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Derivacion y verificacion de contrasenas con PBKDF2.
 *
 * Antes usaba SHA-256 directo, sin salt. Eso tenia dos problemas serios:
 *
 * 1. Sin salt, dos usuarios con la misma contrasena producen el mismo hash, y
 *    una tabla precomputada (rainbow table) las revierte sin esfuerzo.
 * 2. SHA-256 esta disenado para ser rapido, que es exactamente lo contrario de
 *    lo que se quiere para una contrasena: una GPU prueba miles de millones por
 *    segundo.
 *
 * PBKDF2 resuelve las dos: agrega un salt aleatorio por usuario y repite la
 * derivacion muchas veces para que probar cada candidata cueste caro.
 *
 * Formato guardado: iteraciones:salt:hash, los dos ultimos en Base64. Guardar
 * las iteraciones junto al hash permite subirlas en el futuro sin invalidar las
 * contrasenas ya existentes.
 */
public final class PasswordUtil {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SALT = 16;
    private static final int BITS_CLAVE = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(String contrasenaPlana) {
        byte[] salt = new byte[BYTES_SALT];
        ALEATORIO.nextBytes(salt);
        byte[] derivada = derivar(contrasenaPlana, salt, ITERACIONES);

        Base64.Encoder b64 = Base64.getEncoder();
        return ITERACIONES + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(derivada);
    }

    public static boolean verificar(String contrasenaPlana, String hashGuardado) {
        if (contrasenaPlana == null || hashGuardado == null) {
            return false;
        }
        String[] partes = hashGuardado.split(":");
        if (partes.length != 3) {
            // Formato viejo (SHA-256 sin salt) o dato corrupto: no se acepta.
            return false;
        }

        try {
            int iteraciones = Integer.parseInt(partes[0]);
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] salt = b64.decode(partes[1]);
            byte[] esperada = b64.decode(partes[2]);
            byte[] calculada = derivar(contrasenaPlana, salt, iteraciones);

            // Comparacion en tiempo constante: un equals comun corta apenas
            // encuentra el primer byte distinto, y ese tiempo filtra informacion.
            return MessageDigest.isEqual(esperada, calculada);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String contrasena, byte[] salt, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), salt, iteraciones, BITS_CLAVE);
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No se pudo derivar la contrasena", e);
        }
    }
}
