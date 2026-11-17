package ar.edu.uade.da2.mediconecta.historiaclinica.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.historiaclinica.negocio.ServicioDeHistoriaClinicaLocal;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class HistoriaClinicaResourceTest {

    @Mock
    private ServicioDeHistoriaClinicaLocal servicio;

    @InjectMocks
    private HistoriaClinicaResource recurso;

    @Test
    void sinHistoriaEs404EnJson() {
        // SCRUM-132: antes salia como texto plano.
        when(servicio.obtenerHistoriaDePaciente(3L)).thenReturn(null);

        Response respuesta = recurso.obtener(3L);

        assertEquals(404, respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "El paciente no tiene una historia clínica abierta."), respuesta.getEntity());
    }
}
