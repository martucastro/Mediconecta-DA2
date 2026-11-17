package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;

import jakarta.jws.WebMethod;
import jakarta.xml.soap.Detail;
import jakarta.xml.soap.DetailEntry;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.WebFault;
import jakarta.xml.ws.soap.SOAPFaultException;

/**
 * Lo que agrega el endpoint sobre el padron: el numero de autorizacion y el
 * fault declarado en el contrato. El SOAP Fault de un pedido invalido se arma
 * con SAAJ, que no tiene implementacion en el classpath de los tests: aca la
 * fabrica es un doble y se verifica que se pida el codigo Client y el detalle
 * declarado. El fault real en el cable lo verifica el smoke test.
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

    // SCRUM-129: WildFly entrega a CXF el SOAPFaultException y no la excepcion
    // declarada, asi que el detalle tiene que viajar dentro del propio fault.
    @Test
    void unPedidoInvalidoSaleComoFaultClientConElDetalleDeclarado() throws Exception {
        verificarFaultDelCliente(() -> legado.validarCobertura("1", "OS-9999", "CONSULTA"),
                "No existe el afiliado OS-9999");
        verificarFaultDelCliente(() -> legado.autorizarPrestacion("30333444", "OS-2002", "RAYOS"),
                "No existe la prestación RAYOS");
        verificarFaultDelCliente(() -> legado.presentarReclamo("30333444", "OS-2002", "AUT-FALSO"),
                "La autorización AUT-FALSO no corresponde al afiliado OS-2002");
    }

    private static void verificarFaultDelCliente(Executable llamada, String mensajeEsperado) throws Exception {
        SOAPFactory fabrica = mock(SOAPFactory.class);
        SOAPFault fault = mock(SOAPFault.class);
        Detail detalle = mock(Detail.class);
        DetailEntry pedidoInvalido = mock(DetailEntry.class);
        SOAPElement mensaje = mock(SOAPElement.class);
        when(fabrica.createFault(anyString(), any(QName.class))).thenReturn(fault);
        when(fault.addDetail()).thenReturn(detalle);
        when(detalle.addDetailEntry(any(QName.class))).thenReturn(pedidoInvalido);
        when(pedidoInvalido.addChildElement(new QName("message"))).thenReturn(mensaje);

        try (MockedStatic<SOAPFactory> estatica = mockStatic(SOAPFactory.class)) {
            estatica.when(SOAPFactory::newInstance).thenReturn(fabrica);
            SOAPFaultException e = assertThrows(SOAPFaultException.class, llamada);
            assertSame(fault, e.getFault());
        }

        verify(fabrica).createFault(mensajeEsperado,
                new QName("http://schemas.xmlsoap.org/soap/envelope/", "Client"));
        verify(mensaje).addTextNode(mensajeEsperado);
        verify(detalle).addDetailEntry(new QName("http://legado.obrasocial.example/", "PedidoInvalido"));
    }
}
