package ar.edu.uade.da2.mediconecta.pagos.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.pagos.negocio.ServicioDePagosLocal;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * SCRUM-132: un pago inexistente es 404 tanto en GET /pagos/{id} como en
 * PUT /pagos/{id}/reembolso (antes el PUT respondia 400).
 */
@ExtendWith(MockitoExtension.class)
class PagosResourceTest {

    @Mock
    private ServicioDePagosLocal servicio;

    @InjectMocks
    private PagosResource recurso;

    @Test
    void reembolsarUnPagoInexistenteEs404ComoConsultarlo() {
        when(servicio.consultarPago(99L)).thenReturn(null);

        Response consulta = recurso.consultar(99L);
        Response reembolso = recurso.reembolsar(99L);

        assertEquals(404, consulta.getStatus());
        assertEquals(404, reembolso.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, reembolso.getMediaType().toString());
        assertEquals(Map.of("error", "No existe un pago con id 99."), reembolso.getEntity());
        verify(servicio, never()).reembolsar(anyLong());
    }
}
