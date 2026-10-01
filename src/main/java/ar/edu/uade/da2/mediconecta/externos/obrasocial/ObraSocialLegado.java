package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import javax.xml.namespace.QName;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.soap.SOAPFaultException;

/**
 * Simulación del sistema legado de la obra social: un tercero, no un componente
 * de MediConecta.
 *
 * El sistema real no existe, pero la integración tiene que ser SOAP de verdad,
 * así que se publica un endpoint JAX-WS sobre el CXF de WildFly, con su WSDL en
 * /mediconecta/legado/obrasocial?wsdl (mapeo en web.xml). Nuestro componente,
 * ServicioDeObrasSociales, lo consume como consumiría al legado real.
 *
 * Vive fuera de usuarios/turnos/historiaclinica y no tiene capas propias porque
 * no es algo que diseñamos nosotros: de él sólo nos importa el contrato. Ninguna
 * clase de negocio debe importar este paquete; la única vía es el WSDL.
 *
 * Un pedido que el legado no puede evaluar (afiliado inexistente, DNI que no
 * corresponde, prestación desconocida) vuelve como SOAP Fault de código Client:
 * el error es del cliente, no del servidor. Un cliente puede así distinguirlo de
 * una falla interna del legado (código Server), que no se reintenta igual.
 */
@WebService(
        serviceName = "ObraSocialLegadoService",
        portName = "ObraSocialLegadoPort",
        targetNamespace = "http://legado.obrasocial.example/")
public class ObraSocialLegado {

    /**
     * Consulta si la prestación está cubierta y cuánto pagaría el afiliado. No
     * reserva nada, así que no devuelve número de autorización.
     */
    @WebMethod
    @WebResult(name = "respuesta")
    public RespuestaCobertura validarCobertura(
            @WebParam(name = "dni") String dni,
            @WebParam(name = "numeroAfiliado") String numeroAfiliado,
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) {
        return evaluar(dni, numeroAfiliado, codigoPrestacion);
    }

    /**
     * Autoriza la prestación. Mismo criterio que validarCobertura, pero si queda
     * autorizada devuelve un número de autorización, determinista para que la
     * demo sea repetible.
     */
    @WebMethod
    @WebResult(name = "respuesta")
    public RespuestaCobertura autorizarPrestacion(
            @WebParam(name = "dni") String dni,
            @WebParam(name = "numeroAfiliado") String numeroAfiliado,
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) {
        RespuestaCobertura respuesta = evaluar(dni, numeroAfiliado, codigoPrestacion);
        if (respuesta.isAutorizado()) {
            respuesta.setNumeroAutorizacion("AUT-" + numeroAfiliado + "-" + codigoPrestacion);
        }
        return respuesta;
    }

    private static RespuestaCobertura evaluar(String dni, String numeroAfiliado, String codigoPrestacion) {
        try {
            return PadronDeAfiliados.evaluar(dni, numeroAfiliado, codigoPrestacion);
        } catch (PedidoInvalidoException e) {
            throw faultDelCliente(e.getMessage());
        }
    }

    private static SOAPFaultException faultDelCliente(String mensaje) {
        try {
            SOAPFault fault = SOAPFactory.newInstance().createFault(mensaje,
                    new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Client"));
            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new IllegalStateException("No se pudo armar el SOAP Fault", e);
        }
    }
}
