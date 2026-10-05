package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PrestacionTest {

    @Test
    void cadaPrestacionTieneSuArancelParticularConDosDecimales() {
        assertEquals(new BigDecimal("20000.00"), Prestacion.CONSULTA.getArancel());
        assertEquals(new BigDecimal("15000.00"), Prestacion.TELECONSULTA.getArancel());
    }
}
