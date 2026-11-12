package ar.edu.uade.da2.mediconecta.comun.resiliencia;

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Circuit breaker simple (CERRADO -> ABIERTO -> SEMIABIERTO) para proteger las
 * llamadas a sistemas externos.
 *
 * Por que propio y no @CircuitBreaker de MicroProfile Fault Tolerance: el
 * servidor se arranca con standalone-full.xml, que no trae el subsistema
 * microprofile-fault-tolerance-smallrye (solo viene en los perfiles
 * standalone-microprofile*.xml). Esta clase no depende del perfil, y se prueba
 * sin contenedor.
 *
 * Funcionamiento:
 * - CERRADO: las llamadas pasan. Se cuentan las fallas consecutivas; cualquier
 *   respuesta del sistema externo (aunque sea un rechazo de negocio) las reinicia.
 * - ABIERTO: al llegar al umbral, durante tiempoAbiertoMs las llamadas ni
 *   siquiera salen: falla rapido con la excepcion que arma el llamador (la misma
 *   "no disponible" de siempre, asi que los mappers 503/502 no cambian).
 * - SEMIABIERTO: vencido ese tiempo, pasa UNA llamada de prueba; si sale bien
 *   se cierra, si falla se vuelve a abrir. Las demas se rechazan mientras tanto.
 *
 * Solo cuentan como falla las excepciones del tipo indicado (las de
 * "no disponible"); un DatosInvalidosException, por ejemplo, no abre el circuito.
 */
public class CircuitBreaker {

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());

    private final String nombre;
    private final int umbralDeFallas;
    private final long tiempoAbiertoMs;
    private final LongSupplier relojMs;

    private Estado estado = Estado.CERRADO;
    private int fallasConsecutivas;
    private long abiertoDesdeMs;
    private boolean pruebaEnCurso;

    public CircuitBreaker(String nombre, int umbralDeFallas, long tiempoAbiertoMs) {
        this(nombre, umbralDeFallas, tiempoAbiertoMs, System::currentTimeMillis);
    }

    CircuitBreaker(String nombre, int umbralDeFallas, long tiempoAbiertoMs, LongSupplier relojMs) {
        this.nombre = nombre;
        this.umbralDeFallas = umbralDeFallas;
        this.tiempoAbiertoMs = tiempoAbiertoMs;
        this.relojMs = relojMs;
    }

    /**
     * Crea el breaker leyendo umbral y tiempo abierto de propiedades de sistema
     * (prefijo + ".cb.umbral" y prefijo + ".cb.abiertoMs"), con valores por defecto.
     */
    public static CircuitBreaker deConfiguracion(String nombre, String prefijoPropiedad,
            int umbralPorDefecto, long abiertoMsPorDefecto) {
        return new CircuitBreaker(nombre,
                (int) numero(prefijoPropiedad + ".cb.umbral", umbralPorDefecto),
                numero(prefijoPropiedad + ".cb.abiertoMs", abiertoMsPorDefecto));
    }

    /**
     * Ejecuta la llamada protegida.
     *
     * @param falla   tipo de excepcion que cuenta como falla del sistema externo
     * @param rechazo arma la excepcion con la que se falla rapido si el circuito esta abierto
     */
    public <T> T ejecutar(Supplier<T> llamada, Class<? extends RuntimeException> falla,
            Supplier<? extends RuntimeException> rechazo) {
        autorizar(rechazo);
        try {
            T resultado = llamada.get();
            registrarExito();
            return resultado;
        } catch (RuntimeException e) {
            if (falla.isInstance(e)) {
                registrarFalla();
            } else {
                // El sistema externo contesto (por ejemplo, rechazando el pedido): esta vivo.
                registrarExito();
            }
            throw e;
        }
    }

    public synchronized Estado estado() {
        return estado;
    }

    private synchronized void autorizar(Supplier<? extends RuntimeException> rechazo) {
        if (estado == Estado.ABIERTO) {
            if (relojMs.getAsLong() - abiertoDesdeMs < tiempoAbiertoMs) {
                throw rechazo.get();
            }
            estado = Estado.SEMIABIERTO;
            pruebaEnCurso = false;
            LOGGER.info(() -> "Circuit breaker '" + nombre + "': SEMIABIERTO, se prueba una llamada.");
        }
        if (estado == Estado.SEMIABIERTO) {
            if (pruebaEnCurso) {
                throw rechazo.get();
            }
            pruebaEnCurso = true;
        }
    }

    private synchronized void registrarExito() {
        if (estado != Estado.CERRADO) {
            LOGGER.info(() -> "Circuit breaker '" + nombre + "': CERRADO, el sistema externo volvio.");
        }
        estado = Estado.CERRADO;
        fallasConsecutivas = 0;
        pruebaEnCurso = false;
    }

    private synchronized void registrarFalla() {
        fallasConsecutivas++;
        if (estado == Estado.SEMIABIERTO || fallasConsecutivas >= umbralDeFallas) {
            estado = Estado.ABIERTO;
            abiertoDesdeMs = relojMs.getAsLong();
            pruebaEnCurso = false;
            LOGGER.warning(() -> "Circuit breaker '" + nombre + "': ABIERTO por " + tiempoAbiertoMs
                    + " ms tras " + fallasConsecutivas + " falla(s) consecutiva(s).");
        }
    }

    private static long numero(String propiedad, long porDefecto) {
        try {
            String valor = System.getProperty(propiedad);
            return valor == null || valor.isBlank() ? porDefecto : Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
