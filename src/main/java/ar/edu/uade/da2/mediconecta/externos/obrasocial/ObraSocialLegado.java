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
 * Ese fault está declarado en el contrato (SCRUM-129): cada operación declara
 * PedidoInvalidoException, así que el WSDL publica un wsdl:fault por operación,
 * y el SOAP Fault lleva el elemento PedidoInvalido en su detail.
 */
@WebService(
        serviceName = "ObraSocialLegadoService",
        portName = "ObraSocialLegadoPort",
        targetNamespace = ObraSocialLegado.NAMESPACE)
public class ObraSocialLegado {

    static final String NAMESPACE = "http://legado.obrasocial.example/";

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
            throw faultDelCliente(e);
        }
    }

    private static RespuestaCobertura evaluar(String dni, String numeroAfiliado, String codigoPrestacion) {
        try {
            return PadronDeAfiliados.evaluar(dni, numeroAfiliado, codigoPrestacion);
        } catch (PedidoInvalidoException e) {
            throw faultDelCliente(e);
        }
    }

    /**
     * El fault declarado, armado a mano: código Client y, en el detail, el
     * elemento PedidoInvalido con su message, tal como lo publica el WSDL.
     *
     * No se lanza PedidoInvalidoException porque JAX-WS la enviaría con código
     * Server, y el cliente la tomaría como una falla del legado. Tampoco sirve
     * lanzarla con un SOAPFaultException como causa: WildFly le entrega a CXF
     * ese SOAPFaultException y no la excepción declarada
     * (AbstractInvocationHandler.handleInvocationException), así que el
     * detail tiene que viajar dentro del propio fault.
     */
    private static SOAPFaultException faultDelCliente(PedidoInvalidoException e) {
        try {
            SOAPFault fault = SOAPFactory.newInstance().createFault(e.getMessage(),
                    new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Client"));
            // message va sin namespace: el schema del WSDL es elementFormDefault
            // unqualified. Con prefijo en PedidoInvalido no queda un namespace
            // por defecto que message herede.
            fault.addDetail()
                    .addDetailEntry(new QName(NAMESPACE, "PedidoInvalido", "os"))
                    .addChildElement(new QName("message"))
                    .addTextNode(e.getMessage());
            return new SOAPFaultException(fault);
        } catch (SOAPException ex) {
            throw new IllegalStateException("No se pudo armar el SOAP Fault", ex);
        }
    }
}
