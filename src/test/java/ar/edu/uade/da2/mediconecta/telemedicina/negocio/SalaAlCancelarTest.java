package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnCancelacion;
import jakarta.annotation.Priority;
import jakarta.transaction.Transactional;

/** Al cancelar un turno confirmado de telemedicina, su sala deja de estar CREADA (SCRUM-132). */
@ExtendWith(MockitoExtension.class)
class SalaAlCancelarTest {

    @Mock
    private ServicioDeTelemedicinaLocal servicioDeTelemedicina;

    @InjectMocks
    private SalaAlCancelar observador;

    @Test
    void unTurnoDeTelemedicinaCancelaSuSala() {
        observador.alCancelar(new TurnoEnCancelacion(turno(1L, ModalidadTurno.TELEMEDICINA)));

        verify(servicioDeTelemedicina).cancelarSesion(1L);
    }

    @Test
    void unTurnoPresencialNoTocaNada() {
        observador.alCancelar(new TurnoEnCancelacion(turno(2L, ModalidadTurno.PRESENCIAL)));

        verify(servicioDeTelemedicina, never()).cancelarSesion(anyLong());
    }

    @Test
    void vaAntesDelReembolsoYCorreEnLaTransaccionDeLaCancelacion() throws Exception {
        Method alCancelar = SalaAlCancelar.class.getMethod("alCancelar", TurnoEnCancelacion.class);
        int prioridad = alCancelar.getParameters()[0].getAnnotation(Priority.class).value();

        // El reembolso es lo unico que el rollback no deshace: va al final.
        assertEquals(PuntosDeExtension.CIERRE_SALA, prioridad);
        assertTrue(prioridad < PuntosDeExtension.REEMBOLSO_COPAGO);
        assertEquals(Transactional.TxType.MANDATORY,
                alCancelar.getAnnotation(Transactional.class).value());
    }

    private static Turno turno(Long id, ModalidadTurno modalidad) {
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1), modalidad, null);
        turno.setId(id);
        turno.setEstado(EstadoTurno.CANCELADO);
        return turno;
    }
}
