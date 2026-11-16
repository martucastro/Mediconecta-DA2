package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.ApplicationException;
import jakarta.ejb.SessionContext;

/**
 * Reembolso del copago al cancelar un turno confirmado (SCRUM-124). El rollback
 * lo hace el contenedor; aca se verifica que la falla de la pasarela sale como
 * una excepcion con rollback=true y que antes no se toco el pago.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDePagosTest {

    private static final Long TURNO = 7L;

    @Mock
    private PagoDAO pagoDAO;
    @Mock
    private PasarelaDePagoAdapter pasarela;
    @Mock
    private SessionContext contexto;
    @Mock
    private ServicioDeUsuarios servicioDeUsuarios;
    @Mock
    private ServicioDeTurnos servicioDeTurnos;

    @InjectMocks
    private ServicioDePagos servicio;

    @Test
    void cancelarReembolsaElCopagoAprobadoDelTurno() {
        comoElPacienteDelTurno();
        Pago aprobado = pago(EstadoPago.APROBADO, "tx-1");
        Pago rechazado = pago(EstadoPago.RECHAZADO, null);
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(List.of(rechazado, aprobado));
        when(pasarela.reembolsar("tx-1"))
                .thenReturn(new ResultadoPasarela(EstadoPago.REEMBOLSADO, "tx-1"));

        servicio.reembolsarCopagoDelTurno(TURNO);

        assertEquals(EstadoPago.REEMBOLSADO, aprobado.getEstado());
        assertEquals(EstadoPago.RECHAZADO, rechazado.getEstado());
        verify(pagoDAO).actualizar(aprobado);
        verify(pagoDAO, never()).actualizar(rechazado);
    }

    @Test
    void siLaPasarelaNoRespondeLaFallaRevierteLaCancelacion() {
        comoElPacienteDelTurno();
        Pago aprobado = pago(EstadoPago.APROBADO, "tx-2");
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(List.of(aprobado));
        when(pasarela.reembolsar("tx-2")).thenThrow(new PasarelaNoDisponibleException("caida"));

        assertThrows(PasarelaNoDisponibleException.class,
                () -> servicio.reembolsarCopagoDelTurno(TURNO));

        // Sale con rollback=true: el contenedor revierte la cancelacion entera.
        assertTrue(PasarelaNoDisponibleException.class.getAnnotation(ApplicationException.class)
                .rollback());
        assertEquals(EstadoPago.APROBADO, aprobado.getEstado());
        verify(pagoDAO, never()).actualizar(any());
    }

    @Test
    void siLaPasarelaNoConfirmaElReembolsoNoSeCancela() {
        comoElPacienteDelTurno();
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(List.of(pago(EstadoPago.APROBADO, "tx-3")));
        when(pasarela.reembolsar("tx-3"))
                .thenReturn(new ResultadoPasarela(EstadoPago.RECHAZADO, "tx-3"));

        assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.reembolsarCopagoDelTurno(TURNO));
        verify(pagoDAO, never()).actualizar(any());
    }

    @Test
    void sinCopagoCobradoNoHayNadaQueReembolsar() {
        comoElPacienteDelTurno();
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(List.of());

        servicio.reembolsarCopagoDelTurno(TURNO);

        verify(pasarela, never()).reembolsar(any());
    }

    private void comoElPacienteDelTurno() {
        Usuario paciente = new Usuario("Paciente", "paciente@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE, "hash");
        paciente.setId(1L);
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1));
        turno.setId(TURNO);
        turno.setPaciente(paciente);
        Principal principal = paciente::getEmail;
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turno);
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(paciente.getEmail())).thenReturn(paciente);
    }

    private static Pago pago(EstadoPago estado, String idTransaccion) {
        Pago pago = new Pago(TURNO, new BigDecimal("6000.00"), "ARS");
        pago.setEstado(estado);
        pago.setIdTransaccionExterna(idTransaccion);
        return pago;
    }
}
