package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de intentos fallidos de login por email (SCRUM-127).
 *
 * Con MAXIMO_FALLOS fallos dentro de VENTANA, ese email queda rechazado hasta
 * que la ventana vence, aunque despues llegue la contraseña correcta: si no,
 * el intento que acierta seguiria entrando y el limite no frenaria nada.
 *
 * Cuenta por email y no por IP: frena la fuerza bruta contra una cuenta sin
 * depender de proxies ni de X-Forwarded-For. Un email inexistente se cuenta
 * igual que uno real, para que el bloqueo no delate que cuentas existen.
 *
 * Lo comparten el login (ServicioDeUsuarios) y el mecanismo Basic
 * (AutenticacionBasica): si solo lo usara el login, bastaria con probar
 * contraseñas en el encabezado Authorization de cualquier endpoint protegido.
 *
 * ponytail: memoria de un solo nodo. Con dos instancias detras de un balanceador
 * cada una cuenta por su lado, y un reinicio borra los contadores. Si hace
 * falta, mover los contadores a un store compartido (una tabla o una cache
 * Infinispan del servidor).
 */
public class LimitadorDeIntentos {

    static final int MAXIMO_FALLOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(15);

    // ponytail: sin tope, rociar emails inventados haria crecer el mapa sin
    // limite. Al llegar al tope se descartan los vencidos y, si no alcanza, se
    // vacia todo: quien rocie mas de 10.000 emails en 15 minutos reinicia los
    // contadores. Con un store compartido el tope deja de hacer falta.
    static final int MAXIMO_EMAILS = 10_000;

    /** La instancia del proceso: la usan el login y AutenticacionBasica. */
    public static final LimitadorDeIntentos COMPARTIDO = new LimitadorDeIntentos();

    private record Fallos(int cantidad, Instant desde) {
    }

    private final Map<String, Fallos> fallos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimitadorDeIntentos() {
        this(Clock.systemUTC());
    }

    LimitadorDeIntentos(Clock reloj) {
        this.reloj = reloj;
    }

    public boolean estaBloqueado(String email) {
        Fallos actuales = fallos.get(clave(email));
        return actuales != null && !vencido(actuales) && actuales.cantidad() >= MAXIMO_FALLOS;
    }

    public void registrarFallo(String email) {
        if (fallos.size() >= MAXIMO_EMAILS) {
            fallos.values().removeIf(this::vencido);
            if (fallos.size() >= MAXIMO_EMAILS) {
                fallos.clear();
            }
        }
        fallos.merge(clave(email), new Fallos(1, reloj.instant()),
                (previos, nuevo) -> vencido(previos)
                        ? nuevo
                        : new Fallos(previos.cantidad() + 1, previos.desde()));
    }

    public void registrarExito(String email) {
        fallos.remove(clave(email));
    }

    private boolean vencido(Fallos registrados) {
        return !reloj.instant().isBefore(registrados.desde().plus(VENTANA));
    }

    private static String clave(String email) {
        return email == null ? "" : email.toLowerCase(Locale.ROOT);
    }
}
