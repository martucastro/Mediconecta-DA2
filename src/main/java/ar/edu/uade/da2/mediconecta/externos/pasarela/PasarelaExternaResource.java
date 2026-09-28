package ar.edu.uade.da2.mediconecta.externos.pasarela;

import java.util.UUID;
import java.util.logging.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simulación del sistema de pagos EXTERNO, expuesto como un servicio REST propio.
 *
 * No es parte del dominio de MediConecta: representa a un partner moderno (una
 * pasarela tipo Stripe/Mercado Pago) que vive del otro lado de la red. Está bajo
 * /api/externo/* — fuera de las restricciones de seguridad de la app — porque un
 * tercero no se autentica con las credenciales de nuestros usuarios.
 *
 * ServicioDePagos NUNCA llama a esta clase directamente: la alcanza por HTTP a
 * través de PasarelaDePagoRestClient. Que estén en la misma JVM es un detalle del
 * entorno de prueba; el código las trata como dos sistemas separados.
 *
 * Regla de la simulación: aprueba salvo que el token empiece con "rechazar".
 */
@Path("/externo/pagos")
@RequestScoped
public class PasarelaExternaResource {

    private static final Logger LOGGER =
            Logger.getLogger(PasarelaExternaResource.class.getName());

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cobrar(PagoExternoRequest solicitud) {
        LOGGER.info(() -> "Pasarela externa: solicitud de cobro por "
                + (solicitud == null ? "null" : solicitud.getAmount() + " " + solicitud.getCurrency()));

        // Simula una caida de la pasarela: permite probar el comportamiento ante
        // un sistema externo no disponible (503 -> PasarelaNoDisponibleException).
        if (solicitud != null && solicitud.getCardToken() != null
                && solicitud.getCardToken().startsWith("caer")) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        if (solicitud == null || solicitud.getCardToken() == null
                || solicitud.getCardToken().startsWith("rechazar")) {
            return Response.ok(new PagoExternoResponse(PagoExternoResponse.REJECTED, null)).build();
        }

        String transactionId = "tx_" + UUID.randomUUID();
        return Response.ok(
                new PagoExternoResponse(PagoExternoResponse.APPROVED, transactionId)).build();
    }

    @POST
    @Path("/{transactionId}/reembolsos")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reembolsar(@PathParam("transactionId") String transactionId) {
        LOGGER.info(() -> "Pasarela externa: reembolso de " + transactionId);
        return Response.ok(
                new PagoExternoResponse(PagoExternoResponse.REFUNDED, transactionId)).build();
    }
}
