package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;
import java.util.logging.Logger;

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
 * La URL se configura por la propiedad de sistema mediconecta.pasarela.url; por
 * defecto apunta a la simulación desplegada en el mismo servidor.
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

    private static final String URL_BASE = System.getProperty("mediconecta.pasarela.url",
            "http://localhost:8080/mediconecta/api/externo/pagos");

    // Un Client de JAX-RS es caro de crear (resuelve providers, arma el motor
    // HTTP) y esta pensado para reutilizarse: se construye una sola vez cuando
    // el contenedor crea la instancia @ApplicationScoped y se cierra recien
    // cuando la destruye, en vez de abrir y cerrar uno por invocacion.
    private Client cliente;

    @PostConstruct
    public void iniciar() {
        cliente = ClientBuilder.newClient();
    }

    @PreDestroy
    public void cerrar() {
        cliente.close();
    }

    @Override
    public ResultadoPasarela cobrar(BigDecimal monto, String moneda, String tokenMedioDePago) {
        PagoExternoRequest cuerpo = new PagoExternoRequest(monto, moneda, tokenMedioDePago);

        try (Response respuesta = cliente.target(URL_BASE)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo))) {

            if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new PasarelaNoDisponibleException(
                        "La pasarela respondió con estado HTTP " + respuesta.getStatus() + ".");
            }
            return traducir(respuesta.readEntity(PagoExternoResponse.class));

        } catch (ProcessingException e) {
            LOGGER.warning(() -> "No se pudo contactar a la pasarela: " + e.getMessage());
            throw new PasarelaNoDisponibleException(
                    "No se pudo contactar a la pasarela de pago.", e);
        }
    }

    @Override
    public ResultadoPasarela reembolsar(String idTransaccionExterna) {
        try (Response respuesta = cliente.target(URL_BASE)
                .path(idTransaccionExterna)
                .path("reembolsos")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(""))) {

            if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new PasarelaNoDisponibleException(
                        "La pasarela respondió con estado HTTP " + respuesta.getStatus()
                                + " al reembolsar.");
            }
            return traducir(respuesta.readEntity(PagoExternoResponse.class));

        } catch (ProcessingException e) {
            LOGGER.warning(() -> "No se pudo contactar a la pasarela: " + e.getMessage());
            throw new PasarelaNoDisponibleException(
                    "No se pudo contactar a la pasarela de pago.", e);
        }
    }

    /**
     * Traduce el status del partner (texto en inglés) al estado del dominio. Es
     * el corazón del Adapter: acá, y sólo acá, se conoce el vocabulario externo.
     */
    private ResultadoPasarela traducir(PagoExternoResponse externa) {
        if (externa == null || externa.getStatus() == null) {
            throw new PasarelaNoDisponibleException("La pasarela devolvió una respuesta vacía.");
        }
        return switch (externa.getStatus()) {
            case PagoExternoResponse.APPROVED ->
                    new ResultadoPasarela(EstadoPago.APROBADO, externa.getTransactionId());
            case PagoExternoResponse.REJECTED ->
                    new ResultadoPasarela(EstadoPago.RECHAZADO, null);
            case PagoExternoResponse.REFUNDED ->
                    new ResultadoPasarela(EstadoPago.REEMBOLSADO, externa.getTransactionId());
            default -> throw new PasarelaNoDisponibleException(
                    "La pasarela devolvió un status desconocido: " + externa.getStatus() + ".");
        };
    }
}
