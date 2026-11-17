package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.jws.WebMethod;
import jakarta.xml.ws.WebFault;

/**
 * Lo que agrega el endpoint sobre el padron: el numero de autorizacion y el
 * fault declarado en el contrato. El camino de los pedidos invalidos (SOAP
 * Fault de cliente con su detalle) necesita un runtime SAAJ y se verifica
 * contra el servidor desplegado, en el smoke test.
 */
class ObraSocialLegadoTest {

    private final ObraSocialLegado legado = new ObraSocialLegado();

    @Test
    void autorizarEmiteUnNumeroDeterministaCuandoAutoriza() throws PedidoInvalidoException {
        RespuestaCobertura respuesta = legado.autorizarPrestacion("30333444", "OS-2002", "CONSULTA");

        assertTrue(respuesta.isAutorizado());
        assertEquals("AUT-OS-2002-CONSULTA", respuesta.getNumeroAutorizacion());
    }

    @Test
    void autorizarNoEmiteNumeroSiElPlanNoCubre() throws PedidoInvalidoException {
        RespuestaCobertura respuesta = legado.autorizarPrestacion("30555666", "OS-4004", "CONSULTA");

        assertFalse(respuesta.isAutorizado());
        assertNull(respuesta.getNumeroAutorizacion());
    }

    @Test
    void validarCoberturaNuncaEmiteNumero() throws PedidoInvalidoException {
        RespuestaCobertura respuesta = legado.validarCobertura("30333444", "OS-2002", "CONSULTA");

        assertTrue(respuesta.isAutorizado());
        assertNull(respuesta.getNumeroAutorizacion());
    }

    @Test
    void presentarReclamoDevuelveElMontoReconocidoYUnNumeroDePresentacion() throws PedidoInvalidoException {
        RespuestaReclamo respuesta = legado.presentarReclamo("30333444", "OS-2002", "AUT-OS-2002-CONSULTA");

        assertEquals(new java.math.BigDecimal("14000.00"), respuesta.getMontoReconocido());
        assertEquals("PRES-AUT-OS-2002-CONSULTA", respuesta.getNumeroPresentacion());
    }

    // El WSDL publica un <fault> por operacion solo si el metodo lo declara:
    // CXF arma el contrato a partir de estas firmas.
    @Test
    void cadaOperacionDeclaraElFaultDePedidoInvalido() {
        assertNotNull(PedidoInvalidoException.class.getAnnotation(WebFault.class));
        List<Method> operaciones = Arrays.stream(ObraSocialLegado.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(WebMethod.class))
                .toList();

        assertEquals(3, operaciones.size());
        for (Method operacion : operaciones) {
            assertTrue(List.of(operacion.getExceptionTypes()).contains(PedidoInvalidoException.class),
                    operacion.getName() + " no declara PedidoInvalidoException");
        }
    }
}
