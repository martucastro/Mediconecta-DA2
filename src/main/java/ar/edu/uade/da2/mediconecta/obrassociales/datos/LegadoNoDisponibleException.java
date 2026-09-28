package ar.edu.uade.da2.mediconecta.obrassociales.datos;

/**
 * El legado no respondió: conexión rechazada, timeout de conexión o de
 * respuesta. No dice nada sobre el pedido en sí, así que puede reintentarse.
 */
public class LegadoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LegadoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
