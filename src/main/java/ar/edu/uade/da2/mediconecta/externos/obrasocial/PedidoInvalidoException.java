package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.xml.ws.WebFault;

/**
 * El legado no puede evaluar el pedido: afiliado desconocido, DNI que no
 * corresponde al número de afiliado o prestación inexistente.
 *
 * Es el fault declarado del contrato (SCRUM-129): las tres operaciones de
 * ObraSocialLegado la declaran, así que el WSDL publica un wsdl:fault por
 * operación. En el cable no viaja esta excepción: ObraSocialLegado arma el
 * SOAP Fault (código Client, elemento PedidoInvalido en el detail).
 * Es distinto de "sin cobertura", que es una respuesta válida.
 */
@WebFault(name = "PedidoInvalido", targetNamespace = ObraSocialLegado.NAMESPACE)
public class PedidoInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
