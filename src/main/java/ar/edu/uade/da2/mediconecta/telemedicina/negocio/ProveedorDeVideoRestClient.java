package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
 * La URL se configura con la propiedad de sistema mediconecta.video.url; por
 * defecto apunta a la simulacion desplegada en el mismo servidor.
 *
 * A diferencia de la pasarela, aca se fijan timeouts: crear una sala va a correr
 * dentro de la confirmacion de un turno (paso 2/2), con el paciente esperando y
 * la fila del turno bloqueada. Un proveedor que no contesta tiene que convertirse
 * en un 503 rapido, no en una confirmacion colgada.
 */
@ApplicationScoped
public class ProveedorDeVideoRestClient implements ProveedorDeVideoAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(ProveedorDeVideoRestClient.class.getName());

    private static final String URL_BASE = System.getProperty("mediconecta.video.url",
            "http://localhost:8080/mediconecta/api/externo/salas");

    private static final long TIMEOUT_CONEXION_S = 3;
    private static final long TIMEOUT_LECTURA_S = 5;

    // Un Client se construye una sola vez y se reutiliza (ver
    // PasarelaDePagoRestClient): es caro de crear y es thread-safe.
    private Client cliente;

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
        SalaExternaRequest cuerpo = new SalaExternaRequest("turno-" + turnoId,
                fechaHora != null ? fechaHora.toString() : null);

        try (Response respuesta = cliente.target(URL_BASE)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo))) {

            if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new ProveedorDeVideoNoDisponibleException(
                        "El proveedor de video respondio con estado HTTP " + respuesta.getStatus() + ".");
            }
            return traducir(respuesta.readEntity(SalaExternaResponse.class));

        } catch (ProcessingException e) {
            LOGGER.warning(() -> "No se pudo contactar al proveedor de video: " + e.getMessage());
            throw new ProveedorDeVideoNoDisponibleException(
                    "No se pudo contactar al proveedor de video.", e);
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
