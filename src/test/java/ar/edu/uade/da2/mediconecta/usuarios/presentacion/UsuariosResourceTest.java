package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;

/**
 * Un POST sin cuerpo llega como DTO nulo: tiene que ser 400 (via
 * DatosInvalidosMapper), no un NullPointerException que sale como 500.
 */
class UsuariosResourceTest {

    private final UsuariosResource recurso = new UsuariosResource();

    @Test
    void registrarSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.registrar(null));
    }

    @Test
    void loginSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.login(null));
    }
}
