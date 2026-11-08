package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnConfirmacion;
import jakarta.annotation.Priority;
import jakarta.transaction.Transactional;

@ExtendWith(MockitoExtension.class)
class CobroAlConfirmarTest {

    @Mock
    private ServicioDePagosLocal servicioDePagos;

    @InjectMocks
    private CobroAlConfirmar observador;

    @Test
    void unTurnoConCopagoCobraElMontoConElToken() {
        when(servicioDePagos.cobrar(any())).thenReturn(pago(EstadoPago.APROBADO));

        observador.alConfirmar(new TurnoEnConfirmacion(turno(1L, new BigDecimal("6000.00")), "tok_abc"));

        ArgumentCaptor<CobroDTO> captor = ArgumentCaptor.forClass(CobroDTO.class);
        verify(servicioDePagos).cobrar(captor.capture());
        CobroDTO cobro = captor.getValue();
        assertEquals(1L, cobro.getTurnoId());
        assertEquals(new BigDecimal("6000.00"), cobro.getMonto());
        assertEquals(CobroAlConfirmar.MONEDA_COPAGO, cobro.getMoneda());
        assertEquals("tok_abc", cobro.getTokenMedioDePago());
    }

    @Test
    void unTurnoSinCopagoNoCobraNada() {
        observador.alConfirmar(new TurnoEnConfirmacion(turno(2L, null), null));
        observador.alConfirmar(new TurnoEnConfirmacion(turno(3L, BigDecimal.ZERO), null));

        verify(servicioDePagos, never()).cobrar(any());
    }

    @Test
    void unCopagoSinMedioDePagoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> observador.alConfirmar(
                new TurnoEnConfirmacion(turno(4L, new BigDecimal("6000.00")), null)));

        verify(servicioDePagos, never()).cobrar(any());
    }

    @Test
    void unCobroRechazadoNoDejaConfirmarElTurno() {
        // La pasarela no lanza ante un rechazo: devuelve el Pago en RECHAZADO.
        // El observador lo traduce a 409 para que la confirmacion se revierta.
        when(servicioDePagos.cobrar(any())).thenReturn(pago(EstadoPago.RECHAZADO));

        assertThrows(ConflictoDeNegocioException.class, () -> observador.alConfirmar(
                new TurnoEnConfirmacion(turno(5L, new BigDecimal("6000.00")), "tok_abc")));
    }

    @Test
    void laPasarelaCaidaNoSeAtrapa() {
        // Opcion B (como la sala): la excepcion tiene que salir por el fire()
        // de confirmarTurno para que la confirmacion entera se revierta.
        when(servicioDePagos.cobrar(any()))
                .thenThrow(new PasarelaNoDisponibleException("timeout"));

        assertThrows(PasarelaNoDisponibleException.class, () -> observador.alConfirmar(
                new TurnoEnConfirmacion(turno(6L, new BigDecimal("6000.00")), "tok_abc")));
    }

    @Test
    void vaAntesDeLaSalaYCorreEnLaTransaccionDeLaConfirmacion() throws Exception {
        Method alConfirmar = CobroAlConfirmar.class.getMethod("alConfirmar", TurnoEnConfirmacion.class);

        assertEquals(PuntosDeExtension.COBRO_COPAGO,
                alConfirmar.getParameters()[0].getAnnotation(Priority.class).value());
        assertEquals(Transactional.TxType.MANDATORY,
                alConfirmar.getAnnotation(Transactional.class).value());
    }

    private static Turno turno(Long id, BigDecimal copago) {
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1), ModalidadTurno.PRESENCIAL, null);
        turno.setId(id);
        turno.setEstado(EstadoTurno.EN_HOLD);
        turno.setCopago(copago);
        return turno;
    }

    private static Pago pago(EstadoPago estado) {
        Pago pago = new Pago(1L, new BigDecimal("6000.00"), CobroAlConfirmar.MONEDA_COPAGO);
        pago.setEstado(estado);
        return pago;
    }
}
