package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.facturacion.negocio.ServicioDeFacturacion;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.TextMessage;

/**
 * Parseo de TurnoConfirmadoFacturacionMDB, segundo suscriptor del topico
 * TurnoConfirmado (NotificacionMDB es el primero). Mismo criterio que
 * ReclamoMDBTest: no hace falta contenedor para probar el parseo.
 */
@ExtendWith(MockitoExtension.class)
class TurnoConfirmadoFacturacionMDBTest {

    @Mock
    private ServicioDeFacturacion servicio;

    @InjectMocks
    private TurnoConfirmadoFacturacionMDB mdb;

    @Test
    void unMensajeBienFormadoDelegaElTurnoId() throws JMSException {
        MapMessage mensaje = mock(MapMessage.class);
        when(mensaje.getLong("turnoId")).thenReturn(50L);

        mdb.onMessage(mensaje);

        verify(servicio).registrarReclamo(50L);
    }

    @Test
    void unMensajeQueNoEsMapMessageSeDescartaSinExcepcionNiLlamarAlServicio() {
        TextMessage mensaje = mock(TextMessage.class);

        assertDoesNotThrow(() -> mdb.onMessage(mensaje));

        verify(servicio, never()).registrarReclamo(anyLong());
    }

    @Test
    void unMensajeAlQueFallaLaLecturaSeDescartaSinExcepcion() throws JMSException {
        MapMessage mensaje = mock(MapMessage.class);
        when(mensaje.getLong("turnoId")).thenThrow(new JMSException("campo ausente"));

        assertDoesNotThrow(() -> mdb.onMessage(mensaje));

        verify(servicio, never()).registrarReclamo(anyLong());
    }
}
