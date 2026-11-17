package ar.edu.uade.da2.mediconecta.pagos.presentacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.negocio.ServicioDePagosLocal;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

    // No hay POST: el copago solo se cobra dentro de la confirmacion del turno
    // (CobroAlConfirmar). Un endpoint de cobro suelto dejaba cobrar cualquier
    // monto sobre cualquier turno, incluso uno ya confirmado.

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultar(@PathParam("id") Long id) {
        Pago pago = servicio.consultarPago(id);
        if (pago == null) {
            return pagoInexistente(id);
        }
        return Response.ok(new PagoDTO(pago)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarPorTurno(@QueryParam("turnoId") Long turnoId) {
        List<PagoDTO> salida = new ArrayList<>();
        for (Pago pago : servicio.listarPorTurno(turnoId)) {
            salida.add(new PagoDTO(pago));
        }
        return Response.ok(salida).build();
    }

    @PUT
    @Path("/{id}/reembolso")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reembolsar(@PathParam("id") Long id) {
        // Mismo 404 que el GET (SCRUM-132): antes el PUT de un pago inexistente
        // salia 400. consultarPago ya da 403 a un no-administrador, exista o no.
        if (servicio.consultarPago(id) == null) {
            return pagoInexistente(id);
        }
        return Response.ok(new PagoDTO(servicio.reembolsar(id))).build();
    }

    private static Response pagoInexistente(Long id) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "No existe un pago con id " + id + "."))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
