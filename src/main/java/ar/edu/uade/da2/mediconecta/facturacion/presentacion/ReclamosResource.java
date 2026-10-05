package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import java.util.ArrayList;
import java.util.List;

import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;
import ar.edu.uade.da2.mediconecta.facturacion.negocio.ServicioDeFacturacion;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Capa de presentacion del componente: recibe la peticion HTTP, delega en la
 * fachada y traduce el resultado. No contiene ninguna regla de negocio.
 *
 * Solo lectura, pensada para la demo: visibilidad del estado de los reclamos
 * para el administrador. La autorizacion por rol se declara sobre
 * ServicioDeFacturacion (@RolesAllowed(ADMINISTRADOR) en listarReclamos), no
 * aca, para que valga aunque manana otro consumidor llame al componente. A
 * nivel de transporte, web.xml ademas restringe /api/reclamos al
 * ADMINISTRADOR.
 */
@Path("/reclamos")
@RequestScoped
public class ReclamosResource {

    @Inject
    private ServicioDeFacturacion servicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listar() {
        List<ReclamoDTO> salida = new ArrayList<>();
        for (Reclamo reclamo : servicio.listarReclamos()) {
            salida.add(new ReclamoDTO(reclamo));
        }
        return Response.ok(salida).build();
    }
}
