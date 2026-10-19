package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnConfirmacion;
import jakarta.annotation.Priority;
import jakarta.transaction.Transactional;

@ExtendWith(MockitoExtension.class)
class SalaAlConfirmarTest {

    @Mock
    private ServicioDeTelemedicina servicioDeTelemedicina;

    @InjectMocks
    private SalaAlConfirmar observador;

    @Test
    void unTurnoDeTelemedicinaPideLaSala() {
        observador.alConfirmar(new TurnoEnConfirmacion(turno(1L, ModalidadTurno.TELEMEDICINA)));

        verify(servicioDeTelemedicina).crearSesion(1L);
    }

    @Test
    void unTurnoPresencialNoPideNada() {
        observador.alConfirmar(new TurnoEnConfirmacion(turno(2L, ModalidadTurno.PRESENCIAL)));

        verify(servicioDeTelemedicina, never()).crearSesion(anyLong());
    }

    @Test
    void laFallaDelProveedorNoSeAtrapa() {
        // Opcion B: la excepcion tiene que salir por el fire() de confirmarTurno
        // para que la confirmacion entera se revierta.
        when(servicioDeTelemedicina.crearSesion(3L))
                .thenThrow(new ProveedorDeVideoNoDisponibleException("timeout"));

        assertThrows(ProveedorDeVideoNoDisponibleException.class, () -> observador
                .alConfirmar(new TurnoEnConfirmacion(turno(3L, ModalidadTurno.TELEMEDICINA))));
    }

    @Test
    void vaDespuesDelCobroYCorreEnLaTransaccionDeLaConfirmacion() throws Exception {
        Method alConfirmar = SalaAlConfirmar.class.getMethod("alConfirmar", TurnoEnConfirmacion.class);

        assertEquals(PuntosDeExtension.SALA_DE_VIDEO,
                alConfirmar.getParameters()[0].getAnnotation(Priority.class).value());
        assertEquals(Transactional.TxType.MANDATORY,
                alConfirmar.getAnnotation(Transactional.class).value());
    }

    private static Turno turno(Long id, ModalidadTurno modalidad) {
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1), modalidad, null);
        turno.setId(id);
        turno.setEstado(EstadoTurno.EN_HOLD);
        return turno;
    }
}
