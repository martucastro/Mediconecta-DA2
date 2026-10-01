package ar.edu.uade.da2.mediconecta.obrassociales.negocio.soap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Cobertura;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Prestacion;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPFaultException;

/**
 * El Adapter hacia el legado SOAP, sin red: el port JAX-WS es un doble y
 * nuevoPuerto (la unica parte que necesita un runtime de JAX-WS) se reemplaza
 * en un spy. Lo que se verifica es la traduccion en los dos sentidos: que se
 * llame a la operacion correcta, que la respuesta SOAP se convierta en
 * Cobertura, y que cada tipo de falla termine en la excepcion de negocio que
 * corresponde.
 */
class SistemaDeObraSocialSoapTest {

    private static final String DNI = "30333444";
    private static final String AFILIADO = "OS-2002";
    private static final String NS_SOAP_11 = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String NS_SOAP_12 = "http://www.w3.org/2003/05/soap-envelope";

    private ObraSocialLegadoPort puerto;
    private SistemaDeObraSocialSoap sistema;

    @BeforeEach
    void preparar() {
        puerto = mock(ObraSocialLegadoPort.class);
        sistema = spy(new SistemaDeObraSocialSoap());
        doReturn(puerto).when(sistema).nuevoPuerto(anyString());
    }

    // ---- traduccion de la respuesta ------------------------------------------

    @Test
    void consultarLlamaAValidarCoberturaYTraduceLaRespuesta() {
        when(puerto.validarCobertura(DNI, AFILIADO, "CONSULTA"))
                .thenReturn(respuesta(true, 70, "6000.00", null, "Cubierta al 70%"));

        Cobertura cobertura = sistema.consultar(DNI, AFILIADO, Prestacion.CONSULTA);

        assertEquals(new Cobertura(true, 70, new BigDecimal("6000.00"), null, "Cubierta al 70%"), cobertura);
        verify(puerto, never()).autorizarPrestacion(anyString(), anyString(), anyString());
    }

    @Test
    void autorizarLlamaAAutorizarPrestacionYTraeElNumeroDeAutorizacion() {
        when(puerto.autorizarPrestacion(DNI, AFILIADO, "TELECONSULTA"))
                .thenReturn(respuesta(true, 70, "4500.00", "AUT-OS-2002-TELECONSULTA", "Cubierta al 70%"));

        Cobertura cobertura = sistema.autorizar(DNI, AFILIADO, Prestacion.TELECONSULTA);

        assertTrue(cobertura.autorizada());
        assertEquals("AUT-OS-2002-TELECONSULTA", cobertura.numeroAutorizacion());
        assertEquals(new BigDecimal("4500.00"), cobertura.copago());
        verify(puerto, never()).validarCobertura(anyString(), anyString(), anyString());
    }

    @Test
    void unaNegativaDelLegadoEsUnaCoberturaNoAutorizadaYNoUnError() {
        when(puerto.validarCobertura(DNI, AFILIADO, "CONSULTA"))
                .thenReturn(respuesta(false, 0, "20000.00", null, "No cubre"));

        Cobertura cobertura = sistema.consultar(DNI, AFILIADO, Prestacion.CONSULTA);

        assertEquals(false, cobertura.autorizada());
        assertEquals(0, cobertura.porcentaje());
        assertNull(cobertura.numeroAutorizacion());
    }

    @Test
    void traducirUnaRespuestaNulaEsUnLegadoNoDisponible() {
        assertThrows(ObraSocialNoDisponibleException.class, () -> SistemaDeObraSocialSoap.traducir(null));
    }

    @Test
    void unaRespuestaNulaDelPuertoEsUnLegadoNoDisponible() {
        when(puerto.validarCobertura(DNI, AFILIADO, "CONSULTA")).thenReturn(null);

        assertThrows(ObraSocialNoDisponibleException.class,
                () -> sistema.consultar(DNI, AFILIADO, Prestacion.CONSULTA));
    }

    // ---- fallas: pedido rechazado vs legado no disponible ---------------------

    @Test
    void unFaultDelClienteEsUnPedidoRechazadoYConservaElMensajeDelLegado() {
        SOAPFaultException rechazo = fault(NS_SOAP_11, "Client", "No existe el afiliado OS-9999");
        when(puerto.autorizarPrestacion(DNI, AFILIADO, "CONSULTA")).thenThrow(rechazo);

        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> sistema.autorizar(DNI, AFILIADO, Prestacion.CONSULTA));

        assertTrue(e.getMessage().contains("No existe el afiliado OS-9999"));
    }

    @Test
    void unFaultSender12TambienEsUnPedidoRechazado() {
        SOAPFaultException rechazo = fault(NS_SOAP_12, "Sender", "Prestacion desconocida");
        when(puerto.validarCobertura(DNI, AFILIADO, "CONSULTA")).thenThrow(rechazo);

        assertThrows(DatosInvalidosException.class,
                () -> sistema.consultar(DNI, AFILIADO, Prestacion.CONSULTA));
    }

    @Test
    void unFaultDelServidorEsUnLegadoNoDisponibleNoUnDatoInvalido() {
        SOAPFaultException interno = fault(NS_SOAP_11, "Server", "NullPointerException en el legado");
        when(puerto.autorizarPrestacion(DNI, AFILIADO, "CONSULTA")).thenThrow(interno);

        ObraSocialNoDisponibleException e = assertThrows(ObraSocialNoDisponibleException.class,
                () -> sistema.autorizar(DNI, AFILIADO, Prestacion.CONSULTA));

        assertSame(interno, e.getCause());
        // El detalle tecnico del legado no le llega al usuario.
        assertTrue(!e.getMessage().contains("NullPointerException"));
    }

    @Test
    void unFaultSinCodigoSeTrataComoFallaDelLegado() {
        SOAPFaultException raro = fault(null, null, "Fault raro");
        when(puerto.validarCobertura(DNI, AFILIADO, "CONSULTA")).thenThrow(raro);

        assertThrows(ObraSocialNoDisponibleException.class,
                () -> sistema.consultar(DNI, AFILIADO, Prestacion.CONSULTA));
    }

    @Test
    void unTimeoutOUnaConexionRechazadaEsUnLegadoNoDisponibleConSuCausa() {
        WebServiceException timeout = new WebServiceException("Could not receive Message.");
        when(puerto.autorizarPrestacion(DNI, AFILIADO, "CONSULTA")).thenThrow(timeout);

        ObraSocialNoDisponibleException e = assertThrows(ObraSocialNoDisponibleException.class,
                () -> sistema.autorizar(DNI, AFILIADO, Prestacion.CONSULTA));

        assertSame(timeout, e.getCause());
        assertTrue(e.getMessage().contains("no respondi"));
    }

    // ---- ayudas ---------------------------------------------------------------

    private static RespuestaCoberturaXml respuesta(boolean autorizado, int porcentaje, String copago,
            String numeroAutorizacion, String mensaje) {
        RespuestaCoberturaXml xml = new RespuestaCoberturaXml();
        xml.autorizado = autorizado;
        xml.plan = "PLAN_MEDIO";
        xml.porcentajeCobertura = porcentaje;
        xml.arancel = new BigDecimal("20000.00");
        xml.copago = new BigDecimal(copago);
        xml.numeroAutorizacion = numeroAutorizacion;
        xml.mensaje = mensaje;
        return xml;
    }

    /**
     * Un SOAPFault de mentira: no hace falta un runtime SAAJ para clasificarlo.
     * Hay que llamarlo antes del when(...) del puerto: Mockito no admite crear
     * un stub dentro de otro.
     */
    private static SOAPFaultException fault(String namespace, String codigo, String texto) {
        SOAPFault fault = mock(SOAPFault.class);
        when(fault.getFaultString()).thenReturn(texto);
        when(fault.getFaultCodeAsQName()).thenReturn(codigo == null ? null : new QName(namespace, codigo));
        return new SOAPFaultException(fault);
    }
}
