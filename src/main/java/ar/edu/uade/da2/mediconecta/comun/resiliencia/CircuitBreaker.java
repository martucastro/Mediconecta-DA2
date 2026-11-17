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
 * - ABIERTO: al llegar al umbral, durante el tiempo de apertura las llamadas ni
 *   siquiera salen: falla rapido con la excepcion que arma el llamador (la misma
 *   "no disponible" de siempre, asi que los mappers 503/502 no cambian).
 * - SEMIABIERTO: vencido ese tiempo, pasa UNA llamada de prueba; si sale bien
 *   se cierra, si falla se vuelve a abrir. Las demas se rechazan mientras tanto.
 *
 * Solo cuentan como falla las excepciones del tipo indicado (las de
 * "no disponible"); un DatosInvalidosException, por ejemplo, no abre el circuito.
 * Un Error tampoco prueba que el sistema este vivo, asi que cuenta como falla.
 *
 * Umbral y tiempo de apertura se leen de propiedades de sistema en cada llamada
 * (prefijo + ".cb.umbral" y prefijo + ".cb.abiertoMs"), como las URL de los
 * adaptadores: se cambian en caliente con jboss-cli. Un valor invalido usa el
 * valor por defecto.
 *
 * Es thread-safe: cada adaptador es @ApplicationScoped y comparte su breaker
 * entre todos los pedidos. El estado se lee y se cambia bajo el monitor de la
 * instancia; la llamada protegida corre afuera, para no serializarlas.
 */
public class CircuitBreaker {

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());

    private final String nombre;
    private final String prefijoPropiedad;
    private final int umbralPorDefecto;
    private final long abiertoMsPorDefecto;
    private final LongSupplier relojMs;

    // Protegidos por el monitor de la instancia.
    private Estado estado = Estado.CERRADO;
    private int fallasConsecutivas;
    private long abiertoDesdeMs;
    private boolean pruebaEnCurso;

    CircuitBreaker(String nombre, String prefijoPropiedad, int umbralPorDefecto,
            long abiertoMsPorDefecto, LongSupplier relojMs) {
        this.nombre = nombre;
        this.prefijoPropiedad = prefijoPropiedad;
        this.umbralPorDefecto = umbralPorDefecto;
        this.abiertoMsPorDefecto = abiertoMsPorDefecto;
        this.relojMs = relojMs;
    }

    /**
     * Crea el breaker de un sistema externo. Umbral y tiempo abierto salen de
     * las propiedades prefijo + ".cb.umbral" y prefijo + ".cb.abiertoMs", con
     * estos valores por defecto.
     */
    public static CircuitBreaker deConfiguracion(String nombre, String prefijoPropiedad,
            int umbralPorDefecto, long abiertoMsPorDefecto) {
        return new CircuitBreaker(nombre, prefijoPropiedad, umbralPorDefecto, abiertoMsPorDefecto,
                System::currentTimeMillis);
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
        boolean fallo = true;
        try {
            T resultado = llamada.get();
            fallo = false;
            return resultado;
        } catch (RuntimeException e) {
            // Otra excepcion (por ejemplo, un rechazo de negocio) es una respuesta: el sistema esta vivo.
            fallo = falla.isInstance(e);
            throw e;
        } finally {
            registrar(fallo);
        }
    }

    public synchronized Estado estado() {
        return estado;
    }

    private synchronized void autorizar(Supplier<? extends RuntimeException> rechazo) {
        if (estado == Estado.ABIERTO) {
            if (relojMs.getAsLong() - abiertoDesdeMs < abiertoMs()) {
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

    private synchronized void registrar(boolean fallo) {
        if (!fallo) {
            if (estado != Estado.CERRADO) {
                LOGGER.info(() -> "Circuit breaker '" + nombre + "': CERRADO, el sistema externo volvio.");
            }
            estado = Estado.CERRADO;
            fallasConsecutivas = 0;
            pruebaEnCurso = false;
            return;
        }
        fallasConsecutivas++;
        // Ya abierto: es una llamada que salio antes de abrirse. No alarga la apertura.
        if (estado == Estado.SEMIABIERTO
                || (estado == Estado.CERRADO && fallasConsecutivas >= umbral())) {
            estado = Estado.ABIERTO;
            abiertoDesdeMs = relojMs.getAsLong();
            pruebaEnCurso = false;
            long abiertoMs = abiertoMs();
            int fallas = fallasConsecutivas;
            LOGGER.warning(() -> "Circuit breaker '" + nombre + "': ABIERTO por " + abiertoMs
                    + " ms tras " + fallas + " falla(s) consecutiva(s).");
        }
    }

    private int umbral() {
        return (int) numero(prefijoPropiedad + ".cb.umbral", umbralPorDefecto, 1);
    }

    private long abiertoMs() {
        return numero(prefijoPropiedad + ".cb.abiertoMs", abiertoMsPorDefecto, 0);
    }

    private static long numero(String propiedad, long porDefecto, long minimo) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            long numero = Long.parseLong(valor.trim());
            if (numero >= minimo && numero <= Integer.MAX_VALUE) {
                return numero;
            }
        } catch (NumberFormatException e) {
            // cae al aviso de abajo
        }
        LOGGER.warning(() -> "Valor invalido para " + propiedad + ": '" + valor + "'. Se usa " + porDefecto + ".");
        return porDefecto;
    }
}
