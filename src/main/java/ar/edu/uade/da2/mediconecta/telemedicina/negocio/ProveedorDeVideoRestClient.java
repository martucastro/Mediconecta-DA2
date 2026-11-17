package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.resiliencia.CircuitBreaker;
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
 * Implementacion del Adapter que llama al proveedor de video por HTTP, con el
 * Jakarta REST Client API.
 *
 * La URL se lee de la propiedad de sistema mediconecta.video.url en cada
 * llamada, como la del legado SOAP: se puede cambiar en caliente desde
 * jboss-cli. Por defecto apunta a la simulacion desplegada en el mismo servidor.
 *
 * A diferencia de la pasarela, aca se fijan timeouts: crear una sala va a correr
 * dentro de la confirmacion de un turno (paso 2/2), con el paciente esperando y
 * la fila del turno bloqueada. Un proveedor que no contesta tiene que convertirse
 * en un 503 rapido, no en una confirmacion colgada.
 *
 * Un 4xx no es un proveedor caido: dice que entendio el pedido y no lo acepta,
 * y reintentar no lo arregla. Sale como ConflictoDeNegocioException (409), no
 * como un 503 con Retry-After.
 */
@ApplicationScoped
public class ProveedorDeVideoRestClient implements ProveedorDeVideoAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(ProveedorDeVideoRestClient.class.getName());

    static final String PROPIEDAD_URL = "mediconecta.video.url";
    private static final String URL_POR_DEFECTO = "http://localhost:8080/mediconecta/api/externo/salas";

    private static final long TIMEOUT_CONEXION_S = 3;
    private static final long TIMEOUT_LECTURA_S = 5;

    // Un Client se construye una sola vez y se reutiliza (ver
    // PasarelaDePagoRestClient): es caro de crear y es thread-safe.
    private Client cliente;

    // Circuit breaker: con el proveedor caido, tras 3 fallas seguidas la
    // confirmacion de turnos falla al instante (503) en vez de esperar el timeout
    // de lectura en cada intento. Un 4xx (409) no cuenta. Propiedades:
    // mediconecta.video.cb.umbral y mediconecta.video.cb.abiertoMs.
    private final CircuitBreaker circuito =
            CircuitBreaker.deConfiguracion("proveedor-de-video", "mediconecta.video", 3, 10_000);

    @PostConstruct
    public void iniciar() {
        cliente = ClientBuilder.newBuilder()
                .connectTimeout(TIMEOUT_CONEXION_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_LECTURA_S, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    public void cerrar() {
        cliente.close();
    }

    @Override
    public SalaDeVideo crearSala(Long turnoId, LocalDateTime fechaHora) {
        return circuito.ejecutar(() -> crearSalaSinProteger(turnoId, fechaHora),
                ProveedorDeVideoNoDisponibleException.class,
                () -> new ProveedorDeVideoNoDisponibleException(
                        "El proveedor de video no está disponible por el momento. Intentá de nuevo en unos segundos."));
    }

    private SalaDeVideo crearSalaSinProteger(Long turnoId, LocalDateTime fechaHora) {
        SalaExternaRequest cuerpo = new SalaExternaRequest("turno-" + turnoId,
                fechaHora != null ? fechaHora.toString() : null);

        try (Response respuesta = cliente.target(urlBase())
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo))) {
            verificar(respuesta);
            return traducir(respuesta.readEntity(SalaExternaResponse.class));

        } catch (ProcessingException e) {
            LOGGER.warning(() -> "No se pudo contactar al proveedor de video: " + e.getMessage());
            throw new ProveedorDeVideoNoDisponibleException(
                    "No se pudo contactar al proveedor de video.", e);
        }
    }

    static String urlBase() {
        return System.getProperty(PROPIEDAD_URL, URL_POR_DEFECTO);
    }

    /** 200 sigue; un 4xx es un pedido rechazado; el resto, un proveedor caido. */
    static void verificar(Response respuesta) {
        int status = respuesta.getStatus();
        if (respuesta.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR) {
            throw new ConflictoDeNegocioException(
                    "El proveedor de video rechazo el pedido de sala (HTTP " + status + ").");
        }
        if (status != Response.Status.OK.getStatusCode()) {
            throw new ProveedorDeVideoNoDisponibleException(
                    "El proveedor de video respondio con estado HTTP " + status + ".");
        }
    }

    /**
     * Traduce la respuesta del proveedor al dominio: el anfitrion es el
     * profesional y el invitado el paciente. Una respuesta sin alguno de los
     * datos no sirve para armar la sesion, asi que se trata como una falla del
     * proveedor y no se guarda nada a medias.
     */
    static SalaDeVideo traducir(SalaExternaResponse externa) {
        if (externa == null || vacio(externa.getRoomId()) || vacio(externa.getHostUrl())
                || vacio(externa.getGuestUrl())) {
            throw new ProveedorDeVideoNoDisponibleException(
                    "El proveedor de video devolvio una sala incompleta.");
        }
        return new SalaDeVideo(externa.getRoomId(), externa.getHostUrl(), externa.getGuestUrl());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
