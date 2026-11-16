package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;

/**
 * Un POST sin cuerpo llega como request nulo: tiene que ser 400 (via
 * DatosInvalidosMapper), no un NullPointerException que sale como 500.
 */
class TurnosResourceTest {

    private final TurnosResource recurso = new TurnosResource();

    @Test
    void reservarSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.reservar(null));
    }

    @Test
    void abrirDisponibilidadSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.abrirDisponibilidad(null));
    }
}
