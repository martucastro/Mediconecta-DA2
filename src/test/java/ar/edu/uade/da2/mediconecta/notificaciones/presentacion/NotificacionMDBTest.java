package ar.edu.uade.da2.mediconecta.notificaciones.presentacion;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.da2.mediconecta.notificaciones.negocio.ServicioDeNotificaciones;

import jakarta.jms.MapMessage;
import jakarta.jms.MessageFormatException;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Prueba la traduccion del mensaje JMS y la politica de reintentos, sin
 * levantar WildFly ni Artemis: el MapMessage y el servicio son dobles.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionMDBTest {

    @Mock
    private ServicioDeNotificaciones servicio;

    @InjectMocks
    private NotificacionMDB mdb;

    private MapMessage mensajeCompleto() throws Exception {
        MapMessage mapa = mock(MapMessage.class);
        when(mapa.itemExists("turnoId")).thenReturn(true);
        when(mapa.itemExists("pacienteId")).thenReturn(true);
        when(mapa.itemExists("fechaHora")).thenReturn(true);
        when(mapa.getLong("turnoId")).thenReturn(7L);
        when(mapa.getLong("pacienteId")).thenReturn(3L);
        when(mapa.getString("fechaHora")).thenReturn("2026-10-20T10:00");
        return mapa;
    }

    @Test
    void traduceElMensajeYDelegaEnElServicio() throws Exception {
        mdb.onMessage(mensajeCompleto());

        verify(servicio).notificarTurnoConfirmado(7L, 3L, "2026-10-20T10:00");
    }

    @Test
    void descartaUnMensajeQueNoEsMapMessage() {
        mdb.onMessage(mock(TextMessage.class));

        verifyNoInteractions(servicio);
    }

    @Test
    void descartaUnMensajeSinFechaHora() throws Exception {
        MapMessage mapa = mock(MapMessage.class);
        when(mapa.itemExists("turnoId")).thenReturn(true);
        when(mapa.itemExists("pacienteId")).thenReturn(true);
        when(mapa.itemExists("fechaHora")).thenReturn(false);

        mdb.onMessage(mapa);

        verifyNoInteractions(servicio);
    }

    @Test
    void descartaUnMensajeSinTurnoId() throws Exception {
        MapMessage mapa = mock(MapMessage.class);
        when(mapa.itemExists("turnoId")).thenReturn(false);

        mdb.onMessage(mapa);

        verifyNoInteractions(servicio);
    }

    @Test
    void descartaUnMensajeCuyoTurnoIdNoSePuedeLeer() throws Exception {
        MapMessage mapa = mock(MapMessage.class);
        when(mapa.itemExists("turnoId")).thenReturn(true);
        when(mapa.itemExists("pacienteId")).thenReturn(true);
        when(mapa.itemExists("fechaHora")).thenReturn(true);
        when(mapa.getLong("turnoId")).thenThrow(new MessageFormatException("no es un numero"));

        mdb.onMessage(mapa);

        verifyNoInteractions(servicio);
    }

    @Test
    void siElServicioFallaLaExcepcionSubeParaQueArtemisReintente() throws Exception {
        MapMessage mapa = mensajeCompleto();
        doThrow(new IllegalStateException("la base no responde"))
                .when(servicio).notificarTurnoConfirmado(7L, 3L, "2026-10-20T10:00");

        assertThrows(IllegalStateException.class, () -> mdb.onMessage(mapa));
    }
}
