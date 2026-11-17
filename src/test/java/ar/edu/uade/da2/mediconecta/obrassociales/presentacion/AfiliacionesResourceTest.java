package ar.edu.uade.da2.mediconecta.obrassociales.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ServicioDeObrasSocialesLocal;
import jakarta.ws.rs.core.Response;

/**
 * La capa de presentación de afiliaciones, con la fachada como doble: lo que se
 * verifica es la traducción HTTP (cuerpo nulo, 200 con el DTO, 404 con cuerpo
 * JSON de error). Quién puede invocar cada operación lo decide @RolesAllowed
 * sobre la fachada, y eso se verifica contra el servidor en el smoke test.
 */
@ExtendWith(MockitoExtension.class)
class AfiliacionesResourceTest {

    private static final Long PACIENTE_ID = 3L;
    private static final String DNI = "30333444";
    private static final String AFILIADO = "OS-2002";

    @Mock
    private ServicioDeObrasSocialesLocal servicio;

    @InjectMocks
    private AfiliacionesResource recurso;

    @Test
    void registrarSinCuerpoEsDatoInvalidoYNoLlamaALaFachada() {
        assertThrows(DatosInvalidosException.class, () -> recurso.registrar(null));

        verifyNoInteractions(servicio);
    }

    @Test
    void registrarDelegaEnLaFachadaYDevuelveLaAfiliacion() {
        when(servicio.registrarAfiliacion(PACIENTE_ID, DNI, AFILIADO))
                .thenReturn(new AfiliacionDePaciente(PACIENTE_ID, DNI, AFILIADO));
        RegistroAfiliacionRequest pedido = new RegistroAfiliacionRequest();
        pedido.setPacienteId(PACIENTE_ID);
        pedido.setDni(DNI);
        pedido.setNumeroAfiliado(AFILIADO);

        Response respuesta = recurso.registrar(pedido);

        assertEquals(200, respuesta.getStatus());
        afiliacionEsperada((AfiliacionDTO) respuesta.getEntity());
    }

    @Test
    void miAfiliacionDevuelveLaDelPacienteAutenticado() {
        when(servicio.obtenerMiAfiliacion()).thenReturn(new AfiliacionDePaciente(PACIENTE_ID, DNI, AFILIADO));

        Response respuesta = recurso.miAfiliacion();

        assertEquals(200, respuesta.getStatus());
        afiliacionEsperada((AfiliacionDTO) respuesta.getEntity());
    }

    @Test
    void miAfiliacionSinObraSocialEs404ConCuerpoJsonDeError() {
        when(servicio.obtenerMiAfiliacion()).thenReturn(null);

        Response respuesta = recurso.miAfiliacion();

        assertEquals(404, respuesta.getStatus());
        assertEquals("application/json", respuesta.getMediaType().toString());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getEntity();
        assertTrue(cuerpo.get("error").toString().contains("obra social"));
    }

    private static void afiliacionEsperada(AfiliacionDTO dto) {
        assertEquals(PACIENTE_ID, dto.getPacienteId());
        assertEquals(DNI, dto.getDni());
        assertEquals(AFILIADO, dto.getNumeroAfiliado());
    }
}
