package ar.edu.uade.da2.mediconecta.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;
import ar.edu.uade.da2.mediconecta.notificaciones.datos.NotificacionDAO;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Prueba la logica del recordatorio sin levantar WildFly: el DAO se reemplaza
 * por un doble de Mockito, igual que en ServicioDeTurnosTest.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeNotificacionesTest {

    @Mock
    private NotificacionDAO notificacionDAO;

    @InjectMocks
    private ServicioDeNotificaciones servicio;

    @Test
    void guardaElRegistroConElMensajeDelTurno() {
        when(notificacionDAO.existePorTurno(7L)).thenReturn(false);

        servicio.notificarTurnoConfirmado(7L, 3L, "2026-10-20T10:00");

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionDAO).guardar(captor.capture());
        Notificacion guardada = captor.getValue();
        assertEquals(7L, guardada.getTurnoId());
        assertEquals(3L, guardada.getPacienteId());
        assertEquals("LOG", guardada.getCanal());
        assertEquals("Tu turno #7 quedo confirmado para el 20/10/2026 a las 10:00.", guardada.getMensaje());
    }

    @Test
    void noDuplicaSiElTurnoYaFueNotificado() {
        when(notificacionDAO.existePorTurno(7L)).thenReturn(true);

        servicio.notificarTurnoConfirmado(7L, 3L, "2026-10-20T10:00");

        verify(notificacionDAO, never()).guardar(any(Notificacion.class));
    }

    @Test
    void siLaFechaNoEsIsoLaDejaTalCual() {
        when(notificacionDAO.existePorTurno(7L)).thenReturn(false);

        servicio.notificarTurnoConfirmado(7L, 3L, "pasado manana");

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionDAO).guardar(captor.capture());
        assertTrue(captor.getValue().getMensaje().contains("pasado manana"));
    }

    @Test
    void siFaltaLaFechaNoGuardaElTextoNull() {
        when(notificacionDAO.existePorTurno(7L)).thenReturn(false);

        servicio.notificarTurnoConfirmado(7L, 3L, null);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionDAO).guardar(captor.capture());
        assertTrue(captor.getValue().getMensaje().contains("fecha sin informar"));
    }

    @Test
    void listarMiasPideAlDaoLasDelPaciente() {
        Notificacion una = new Notificacion(7L, 3L, "LOG", "hola");
        when(notificacionDAO.buscarPorPaciente(3L)).thenReturn(List.of(una));

        assertEquals(List.of(una), servicio.listarMias(3L));
    }
}
