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
 *
 * Ese fault está declarado en el contrato (SCRUM-129): cada operación lanza
 * PedidoInvalidoException, así que el WSDL publica un wsdl:fault por operación
 * y el SOAP Fault lleva el elemento PedidoInvalido en su detail.
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
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) throws PedidoInvalidoException {
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
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) throws PedidoInvalidoException {
        RespuestaCobertura respuesta = evaluar(dni, numeroAfiliado, codigoPrestacion);
        if (respuesta.isAutorizado()) {
            respuesta.setNumeroAutorizacion("AUT-" + numeroAfiliado + "-" + codigoPrestacion);
        }
        return respuesta;
    }

    /**
     * Presenta ante la obra social una prestación ya autorizada
     * (autorizarPrestacion), para que le reconozca su parte y la factura
     * pueda cobrarla. A diferencia de las otras dos operaciones, no evalúa
     * plan ni cobertura: valida que la autorización corresponda al afiliado
     * (mismo formato determinista AUT-&lt;afiliado&gt;-&lt;prestación&gt; que
     * emite autorizarPrestacion) y devuelve cuánto reconoce y con qué número
     * queda presentado el reclamo. Una autorización que no corresponda al
     * afiliado, o que no exista, es un pedido inválido.
     */
    @WebMethod
    @WebResult(name = "respuesta")
    public RespuestaReclamo presentarReclamo(
            @WebParam(name = "dni") String dni,
            @WebParam(name = "numeroAfiliado") String numeroAfiliado,
            @WebParam(name = "numeroAutorizacion") String numeroAutorizacion) throws PedidoInvalidoException {
        try {
            return PadronDeAfiliados.presentarReclamo(dni, numeroAfiliado, numeroAutorizacion);
        } catch (PedidoInvalidoException e) {
            throw comoFaultDelCliente(e);
        }
    }

    private static RespuestaCobertura evaluar(String dni, String numeroAfiliado, String codigoPrestacion)
            throws PedidoInvalidoException {
        try {
            return PadronDeAfiliados.evaluar(dni, numeroAfiliado, codigoPrestacion);
        } catch (PedidoInvalidoException e) {
            throw comoFaultDelCliente(e);
        }
    }

    /**
     * Un fault declarado sale por defecto con código Server (JAX-WS lo trata
     * como excepción de la aplicación), y el cliente lo tomaría como una falla
     * del legado. Con un SOAPFaultException de código Client como causa, CXF
     * toma de ahí el faultcode y escribe el detail a partir de la excepción.
     */
    // ponytail: depende de cómo CXF (el runtime de WildFly) arma el fault; el
    // smoke test verifica el código Client contra el servidor desplegado. Si se
    // cambia de runtime, un SOAPHandler que fije el faultcode cumple lo mismo.
    private static PedidoInvalidoException comoFaultDelCliente(PedidoInvalidoException e) {
        try {
            SOAPFault fault = SOAPFactory.newInstance().createFault(e.getMessage(),
                    new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Client"));
            e.initCause(new SOAPFaultException(fault));
            return e;
        } catch (SOAPException ex) {
            throw new IllegalStateException("No se pudo armar el SOAP Fault", ex);
        }
    }
}
