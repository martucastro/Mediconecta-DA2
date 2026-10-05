package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Lo que agrega el endpoint sobre el padron: el numero de autorizacion. El
 * camino de los pedidos invalidos (SOAP Fault de cliente) necesita un runtime
 * SAAJ y se verifica contra el servidor desplegado, en el smoke test.
 */
class ObraSocialLegadoTest {

    private final ObraSocialLegado legado = new ObraSocialLegado();

    @Test
    void autorizarEmiteUnNumeroDeterministaCuandoAutoriza() {
        RespuestaCobertura respuesta = legado.autorizarPrestacion("30333444", "OS-2002", "CONSULTA");

        assertTrue(respuesta.isAutorizado());
        assertEquals("AUT-OS-2002-CONSULTA", respuesta.getNumeroAutorizacion());
    }

    @Test
    void autorizarNoEmiteNumeroSiElPlanNoCubre() {
        RespuestaCobertura respuesta = legado.autorizarPrestacion("30555666", "OS-4004", "CONSULTA");

        assertFalse(respuesta.isAutorizado());
        assertNull(respuesta.getNumeroAutorizacion());
    }

    @Test
    void validarCoberturaNuncaEmiteNumero() {
        RespuestaCobertura respuesta = legado.validarCobertura("30333444", "OS-2002", "CONSULTA");

        assertTrue(respuesta.isAutorizado());
        assertNull(respuesta.getNumeroAutorizacion());
    }

    @Test
    void presentarReclamoDevuelveElMontoReconocidoYUnNumeroDePresentacion() {
        RespuestaReclamo respuesta = legado.presentarReclamo("30333444", "OS-2002", "AUT-OS-2002-CONSULTA");

        assertEquals(new java.math.BigDecimal("14000.00"), respuesta.getMontoReconocido());
        assertEquals("PRES-AUT-OS-2002-CONSULTA", respuesta.getNumeroPresentacion());
    }
}
