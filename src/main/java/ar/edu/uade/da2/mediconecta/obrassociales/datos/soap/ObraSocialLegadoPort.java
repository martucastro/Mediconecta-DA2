package ar.edu.uade.da2.mediconecta.obrassociales.datos.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

/**
 * Contrato del legado tal como lo publica su WSDL, del lado del cliente.
 *
 * Escrito a mano en vez de generado con wsimport: son dos operaciones y un tipo,
 * y así el build no depende de tener el legado levantado para leer su WSDL.
 * Nombres y namespace tienen que coincidir exactamente con los del WSDL.
 */
@WebService(name = "ObraSocialLegado", targetNamespace = ObraSocialLegadoPort.NAMESPACE)
public interface ObraSocialLegadoPort {

    String NAMESPACE = "http://legado.obrasocial.example/";

    @WebMethod
    @WebResult(name = "respuesta")
    RespuestaCoberturaXml validarCobertura(
            @WebParam(name = "dni") String dni,
            @WebParam(name = "numeroAfiliado") String numeroAfiliado,
            @WebParam(name = "codigoPrestacion") String codigoPrestacion);

    @WebMethod
    @WebResult(name = "respuesta")
    RespuestaCoberturaXml autorizarPrestacion(
            @WebParam(name = "dni") String dni,
            @WebParam(name = "numeroAfiliado") String numeroAfiliado,
            @WebParam(name = "codigoPrestacion") String codigoPrestacion);
}
