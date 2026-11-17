package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.comun.resiliencia.CircuitBreaker;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Implementación del Adapter que llama a la pasarela de pago externa por HTTP
 * (integración síncrona REST con un partner moderno).
 *
 * Esta es la diferencia con una llamada en proceso: el cobro sale de la JVM como
 * un POST HTTP contra /api/externo/pagos y la respuesta vuelve como JSON, que acá
 * se traduce al dominio de MediConecta. ServicioDePagos no conoce nada de esto:
 * habla contra PasarelaDePagoAdapter.
 *
 * La URL se lee de la propiedad de sistema mediconecta.pasarela.url en cada
 * llamada, como la del legado SOAP (SistemaDeObraSocialSoap): se puede cambiar
 * en caliente desde jboss-cli, sin redesplegar. Por defecto apunta a la
 * simulación desplegada en el mismo servidor.
 *
 * Timeouts explícitos, los mismos que el cliente SOAP (2 s para conectar, 5 s
 * para responder): el cobro corre dentro de la confirmación, con la fila del
 * turno bloqueada y el paciente esperando. Sin ellos, una pasarela que acepta la
 * conexión y no contesta colgaba la confirmación indefinidamente.
 *
 * Cómo se lee la respuesta: 200 se traduce; un 4xx dice que la pasarela entendió
 * el pedido y no lo acepta, así que es un rechazo (RECHAZADO, 409 al paciente) y
 * no una caída. Salvo 408 y 429, que dicen "ahora no" y no "nunca": esos,
 * cualquier otro estado, un error de red o un timeout son
 * PasarelaNoDisponibleException (502) y cuentan para el circuit breaker.
 *
 * Un cobro que salió y no tuvo respuesta (timeout de lectura, conexión cortada)
 * es además incierto: la pasarela pudo haber cobrado. Sin clave de idempotencia
 * ni id de transacción no hay con qué reembolsarlo, así que sale como
 * CobroInciertoException y ServicioDePagos deja el pago INCIERTO para que lo
 * concilie un administrador. Ver sinRespuesta.
 *
 * Pool de conexiones: no se configura. RESTEasy 7 (el de WildFly 41) arma el
 * Client con un pool de 50 conexiones, 50 por destino, y acá hay un solo
 * destino. Cada cobro o reembolso corre dentro de una transacción que ya tiene
 * tomada una conexión de MediConectaDS, cuyo pool (20 por defecto) se agota
 * antes que el de HTTP.
 *
 * PagoExternoRequest/PagoExternoResponse son propias de este paquete, no las de
 * externos.pasarela: esas modelan el contrato del lado de la simulación, un
 * sistema aparte que se alcanza por HTTP. El Adapter no tiene que importar el
 * tipo interno del partner para hablarle en su idioma; solo necesita el mismo
 * shape JSON.
 */
@ApplicationScoped
public class PasarelaDePagoRestClient implements PasarelaDePagoAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(PasarelaDePagoRestClient.class.getName());

    static final String PROPIEDAD_URL = "mediconecta.pasarela.url";
    private static final String URL_POR_DEFECTO = "http://localhost:8080/mediconecta/api/externo/pagos";

    private static final long TIMEOUT_CONEXION_MS = 2000;
    private static final long TIMEOUT_RESPUESTA_MS = 5000;

    // Un Client de JAX-RS es caro de crear (resuelve providers, arma el motor
    // HTTP) y esta pensado para reutilizarse: se construye una sola vez cuando
    // el contenedor crea la instancia @ApplicationScoped y se cierra recien
    // cuando la destruye, en vez de abrir y cerrar uno por invocacion.
    private Client cliente;

    // Circuit breaker compartido por cobrar y reembolsar: si la pasarela cae,
    // tras 3 fallas seguidas se deja de llamarla por 10 s y se falla al instante.
    // Un pago RECHAZADO es una respuesta valida, no una falla. Propiedades:
    // mediconecta.pasarela.cb.umbral y mediconecta.pasarela.cb.abiertoMs.
    private final CircuitBreaker circuito =
            CircuitBreaker.deConfiguracion("pasarela-de-pago", "mediconecta.pasarela", 3, 10_000);

    private <T> T protegido(Supplier<T> llamada) {
        return circuito.ejecutar(llamada, PasarelaNoDisponibleException.class,
                () -> new PasarelaNoDisponibleException(
                        "La pasarela de pago no está disponible por el momento. Intentá de nuevo en unos segundos."));
    }

    @PostConstruct
    public void iniciar() {
        cliente = ClientBuilder.newBuilder()
                .connectTimeout(TIMEOUT_CONEXION_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_RESPUESTA_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    @PreDestroy
    public void cerrar() {
        cliente.close();
    }

    @Override
    public ResultadoPasarela cobrar(BigDecimal monto, String moneda, String tokenMedioDePago) {
        return protegido(() -> cobrarSinProteger(monto, moneda, tokenMedioDePago));
    }

    private ResultadoPasarela cobrarSinProteger(BigDecimal monto, String moneda, String tokenMedioDePago) {
        PagoExternoRequest cuerpo = new PagoExternoRequest(monto, moneda, tokenMedioDePago);

        try (Response respuesta = cliente.target(urlBase())
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo))) {
            return interpretar(respuesta, "cobrar");

        } catch (ProcessingException e) {
            throw sinRespuesta(e, true);
        }
    }

    @Override
    public ResultadoPasarela reembolsar(String idTransaccionExterna) {
        return protegido(() -> reembolsarSinProteger(idTransaccionExterna));
    }

    private ResultadoPasarela reembolsarSinProteger(String idTransaccionExterna) {
        try (Response respuesta = cliente.target(urlBase())
                .path(idTransaccionExterna)
                .path("reembolsos")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(""))) {
            return interpretar(respuesta, "reembolsar");

        } catch (ProcessingException e) {
            throw sinRespuesta(e, false);
        }
    }

    /**
     * La llamada no tuvo respuesta. Si era un cobro y el pedido pudo haber
     * llegado a la pasarela, el resultado es incierto (CobroInciertoException).
     */
    static PasarelaNoDisponibleException sinRespuesta(ProcessingException e, boolean esCobro) {
        LOGGER.warning(() -> "No se pudo contactar a la pasarela: " + e.getMessage());
        if (esCobro && !noLlegoASalir(e)) {
            return new CobroInciertoException("La pasarela de pago no respondió y no se sabe si el cobro"
                    + " se realizó. Quedó registrado para que lo revise un administrador.", e);
        }
        return new PasarelaNoDisponibleException("No se pudo contactar a la pasarela de pago.", e);
    }

    /**
     * Fallas en las que el pedido seguro no salió: conexión rechazada, host
     * desconocido o timeout de conexión. Cualquier otra (timeout de lectura,
     * conexión cortada) pudo llegar a la pasarela.
     *
     * ponytail: los timeouts de conexión se reconocen por nombre de clase
     * (ConnectTimeoutException de Apache HttpClient, HttpConnectTimeoutException
     * del JDK) o por mensaje, para no depender del motor HTTP de RESTEasy. Un
     * caso no reconocido cae del lado seguro: incierto, y lo revisa una persona.
     */
    static boolean noLlegoASalir(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            String mensaje = String.valueOf(causa.getMessage()).toLowerCase(Locale.ROOT);
            if (causa instanceof ConnectException || causa instanceof UnknownHostException
                    || causa.getClass().getSimpleName().contains("ConnectTimeout")
                    || mensaje.contains("connect timed out")) {
                return true;
            }
        }
        return false;
    }

    static String urlBase() {
        return System.getProperty(PROPIEDAD_URL, URL_POR_DEFECTO);
    }

    /** Del estado HTTP al dominio; ver el javadoc de la clase. */
    static ResultadoPasarela interpretar(Response respuesta, String operacion) {
        int status = respuesta.getStatus();
        boolean transitorio = status == 408 || status == 429;
        if (!transitorio && respuesta.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR) {
            LOGGER.warning(() -> "La pasarela rechazó el pedido de " + operacion + " con HTTP " + status + ".");
            return new ResultadoPasarela(EstadoPago.RECHAZADO, null);
        }
        if (status != Response.Status.OK.getStatusCode()) {
            throw new PasarelaNoDisponibleException(
                    "La pasarela respondió con estado HTTP " + status + " al " + operacion + ".");
        }
        return traducir(respuesta.readEntity(PagoExternoResponse.class));
    }

    /**
     * Traduce el status del partner (texto en inglés) al estado del dominio. Es
     * el corazón del Adapter: acá, y sólo acá, se conoce el vocabulario externo.
     *
     * Un cobro aprobado sin id de transacción no se acepta: sin él no se lo
     * podría reembolsar después (ni al cancelar ni al compensar un cobro
     * huérfano). Se trata como una respuesta inválida del partner.
     */
    static ResultadoPasarela traducir(PagoExternoResponse externa) {
        if (externa == null || externa.getStatus() == null) {
            throw new PasarelaNoDisponibleException("La pasarela devolvió una respuesta vacía.");
        }
        return switch (externa.getStatus()) {
            case PagoExternoResponse.APPROVED -> {
                if (externa.getTransactionId() == null || externa.getTransactionId().isBlank()) {
                    LOGGER.warning("La pasarela aprobó un cobro sin id de transacción.");
                    throw new PasarelaNoDisponibleException(
                            "La pasarela aprobó el cobro sin id de transacción.");
                }
                yield new ResultadoPasarela(EstadoPago.APROBADO, externa.getTransactionId());
            }
            case PagoExternoResponse.REJECTED ->
                    new ResultadoPasarela(EstadoPago.RECHAZADO, null);
            case PagoExternoResponse.REFUNDED ->
                    new ResultadoPasarela(EstadoPago.REEMBOLSADO, externa.getTransactionId());
            default -> throw new PasarelaNoDisponibleException(
                    "La pasarela devolvió un status desconocido: " + externa.getStatus() + ".");
        };
    }
}
