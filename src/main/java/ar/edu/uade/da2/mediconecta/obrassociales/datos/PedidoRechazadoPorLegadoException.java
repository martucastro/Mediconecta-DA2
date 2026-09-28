package ar.edu.uade.da2.mediconecta.obrassociales.datos;

/**
 * El legado respondió, pero con un error: no reconoce al afiliado, el DNI no
 * corresponde o la prestación no existe. Reintentar no cambia el resultado.
 */
public class PedidoRechazadoPorLegadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoRechazadoPorLegadoException(String mensaje) {
        super(mensaje);
    }
}
