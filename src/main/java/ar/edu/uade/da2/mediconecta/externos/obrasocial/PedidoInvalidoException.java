package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.xml.ws.WebFault;

/**
 * El legado no puede evaluar el pedido: afiliado desconocido, DNI que no
 * corresponde al número de afiliado o prestación inexistente.
 *
 * Es el fault declarado del contrato (SCRUM-129): las tres operaciones de
 * ObraSocialLegado la declaran, así que el WSDL publica un wsdl:fault por
 * operación y el SOAP Fault viaja con el elemento PedidoInvalido en su detail.
 * Es distinto de "sin cobertura", que es una respuesta válida.
 */
@WebFault(name = "PedidoInvalido", targetNamespace = "http://legado.obrasocial.example/")
public class PedidoInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }

    // Declarado aquí y no solo heredado: CXF arma el detail del fault con los
    // getters que declara esta clase, y sin este quedaba vacío.
    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
