package ar.edu.uade.da2.mediconecta.pagos.presentacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.negocio.CobroDTO;
import ar.edu.uade.da2.mediconecta.pagos.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.pagos.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.pagos.negocio.PasarelaNoDisponibleException;
import ar.edu.uade.da2.mediconecta.pagos.negocio.ServicioDePagosLocal;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Capa de presentación del componente: recibe la petición HTTP, delega en la
 * fachada y traduce el resultado. No contiene ninguna regla de negocio.
 *
 * La autorización por rol se declara sobre la fachada ServicioDePagos, no acá,
 * para que la regla valga aunque mañana otro consumidor llame al componente. A
 * nivel de transporte, web.xml además exige estar autenticado para /api/pagos/*.
 */
@Path("/pagos")
@RequestScoped
public class PagosResource {

    @Inject
    private ServicioDePagosLocal servicio;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cobrar(CobroDTO datos) {
        return responder(() -> Response.status(Response.Status.CREATED)
                .entity(new PagoDTO(servicio.cobrar(datos)))
                .build());
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultar(@PathParam("id") Long id) {
        return responder(() -> {
            Pago pago = servicio.consultarPago(id);
            if (pago == null) {
                return error(Response.Status.NOT_FOUND, "No existe un pago con id " + id + ".");
            }
            return Response.ok(new PagoDTO(pago)).build();
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarPorTurno(@QueryParam("turnoId") Long turnoId) {
        return responder(() -> {
            List<PagoDTO> salida = new ArrayList<>();
            for (Pago pago : servicio.listarPorTurno(turnoId)) {
                salida.add(new PagoDTO(pago));
            }
            return Response.ok(salida).build();
        });
    }

    @PUT
    @Path("/{id}/reembolso")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reembolsar(@PathParam("id") Long id) {
        return responder(() -> Response.ok(new PagoDTO(servicio.reembolsar(id))).build());
    }

    /**
     * Traduce las excepciones de negocio a códigos HTTP que digan la verdad.
     * Mismo criterio que TurnosResource y HistoriaClinicaResource: la traducción
     * vive en presentación porque el código HTTP es un detalle del transporte.
     */
    private Response responder(Operacion operacion) {
        try {
            return operacion.ejecutar();
        } catch (DatosInvalidosException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (ConflictoDeNegocioException e) {
            return error(Response.Status.CONFLICT, e.getMessage());
        } catch (PasarelaNoDisponibleException e) {
            return error(Response.Status.BAD_GATEWAY, e.getMessage());
        }
    }

    private Response error(Response.Status estado, String mensaje) {
        return Response.status(estado)
                .entity(Map.of("error", mensaje))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @FunctionalInterface
    private interface Operacion {
        Response ejecutar();
    }
}
