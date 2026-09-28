package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

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
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) throws AfiliadoInexistenteFault {
        return PadronDeAfiliados.evaluar(dni, numeroAfiliado, codigoPrestacion);
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
            @WebParam(name = "codigoPrestacion") String codigoPrestacion) throws AfiliadoInexistenteFault {
        RespuestaCobertura respuesta = PadronDeAfiliados.evaluar(dni, numeroAfiliado, codigoPrestacion);
        if (respuesta.isAutorizado()) {
            respuesta.setNumeroAutorizacion("AUT-" + numeroAfiliado + "-" + codigoPrestacion);
        }
        return respuesta;
    }
}
