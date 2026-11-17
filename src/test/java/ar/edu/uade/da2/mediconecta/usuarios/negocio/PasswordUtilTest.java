package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    @Test
    void elHashTieneTresPartesYLaPrimeraEsNumerica() {
        String[] partes = PasswordUtil.hash("clave123").split(":");

        assertEquals(3, partes.length);
        assertDoesNotThrow(() -> Integer.parseInt(partes[0]));
    }

    @Test
    void laMismaContrasenaGeneraHashesDistintosPorElSalt() {
        assertNotEquals(PasswordUtil.hash("clave123"), PasswordUtil.hash("clave123"));
    }

    @Test
    void verificarConLaContrasenaCorrectaDevuelveTrue() {
        assertTrue(PasswordUtil.verificar("clave123", PasswordUtil.hash("clave123")));
    }

    @Test
    void verificarConContrasenaIncorrectaDevuelveFalse() {
        assertFalse(PasswordUtil.verificar("otra", PasswordUtil.hash("clave123")));
    }

    @Test
    void verificarConContrasenaNullODeHashNullDevuelveFalse() {
        assertFalse(PasswordUtil.verificar(null, PasswordUtil.hash("clave123")));
        assertFalse(PasswordUtil.verificar("clave123", null));
    }

    @Test
    void verificarConHashDeFormatoViejoDevuelveFalse() {
        String sha256SinSalt = "ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f";

        assertFalse(PasswordUtil.verificar("clave123", sha256SinSalt));
    }

    @Test
    void verificarConIteracionesNoNumericasDevuelveFalseSinLanzarExcepcion() {
        assertDoesNotThrow(() -> assertFalse(PasswordUtil.verificar("clave123", "abc:xxx:yyy")));
    }
}
