package ar.edu.uade.da2.mediconecta.externos.obrasocial;

/**
 * El legado no puede evaluar el pedido: afiliado desconocido, DNI que no
 * corresponde al número de afiliado o prestación inexistente.
 *
 * Es una excepción interna del simulador. ObraSocialLegado la convierte en un
 * SOAP Fault de código Client, porque el error es del cliente y no del legado.
 * Es distinto de "sin cobertura", que es una respuesta válida.
 */
class PedidoInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
