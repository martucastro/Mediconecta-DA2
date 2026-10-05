package ar.edu.uade.da2.mediconecta.externos.video;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simulacion del proveedor de video EXTERNO, expuesto como un servicio REST
 * propio.
 *
 * No es parte del dominio de MediConecta: representa a un tercero (un servicio
 * de videoconsultas tipo Zoom o Daily) que vive del otro lado de la red. Esta
 * bajo /api/externo/*, fuera de las restricciones de seguridad de la app, por
 * el mismo motivo que la pasarela de pago: un tercero no se autentica con las
 * credenciales de nuestros usuarios.
 *
 * ServicioDeTelemedicina NUNCA llama a esta clase directamente: la alcanza por
 * HTTP a traves de ProveedorDeVideoRestClient.
 *
 * Las salas son de Jitsi Meet, que crea la sala en el momento en que alguien
 * entra a la URL: los enlaces que devuelve la simulacion funcionan de verdad
 * sin API key. El nombre de la sala lleva un UUID porque es el unico control de
 * acceso que tiene; los dos enlaces apuntan a la misma sala y solo cambian el
 * nombre con el que entra cada participante.
 *
 * Reglas de la simulacion:
 * - reference que empieza con "caer", o la propiedad de sistema
 *   mediconecta.video.simular-caida=true: responde 503, como un proveedor caido.
 * - sin cuerpo o sin reference: 400.
 */
@Path("/externo/salas")
@RequestScoped
public class ProveedorDeVideoExternoResource {

    private static final Logger LOGGER =
            Logger.getLogger(ProveedorDeVideoExternoResource.class.getName());

    static final String PROPIEDAD_SIMULAR_CAIDA = "mediconecta.video.simular-caida";

    private static final String SERVIDOR = "https://meet.jit.si/";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response crearSala(SalaExternaRequest solicitud) {
        LOGGER.info(() -> "Proveedor de video externo: solicitud de sala para "
                + (solicitud == null ? "null" : solicitud.getReference()));

        if (Boolean.getBoolean(PROPIEDAD_SIMULAR_CAIDA) || (solicitud != null
                && solicitud.getReference() != null && solicitud.getReference().startsWith("caer"))) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        if (solicitud == null || solicitud.getReference() == null || solicitud.getReference().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "reference is required"))
                    .build();
        }

        String roomId = "MediConecta-" + UUID.randomUUID();
        return Response.ok(new SalaExternaResponse(roomId,
                SERVIDOR + roomId + "#userInfo.displayName=%22Profesional%22",
                SERVIDOR + roomId + "#userInfo.displayName=%22Paciente%22")).build();
    }
}
