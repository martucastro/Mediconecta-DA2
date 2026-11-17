package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

/**
 * SCRUM-127: un no-administrador recibe el mismo 403 si el pago (o el turno)
 * no existe que si es de otro. Con un 404 o un 400 para el inexistente y un
 * 403 para el ajeno, se podian enumerar los ids ocupados.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDePagosAccesoTest {

    private static final Long PAGO_AJENO = 10L;
    private static final Long TURNO_AJENO = 20L;
    private static final Long INEXISTENTE = 999L;

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
    void unPagoInexistenteYUnoAjenoDanElMismoRechazo() {
        comoPaciente();
        when(pagoDAO.buscarPorId(INEXISTENTE)).thenReturn(null);
        when(pagoDAO.buscarPorId(PAGO_AJENO)).thenReturn(new Pago(TURNO_AJENO, new BigDecimal("6000.00"), "ARS"));
        when(servicioDeTurnos.obtenerTurno(TURNO_AJENO)).thenReturn(turnoDeOtroPaciente());

        assertThrows(EJBAccessException.class, () -> servicio.consultarPago(PAGO_AJENO));
        assertThrows(EJBAccessException.class, () -> servicio.consultarPago(INEXISTENTE));
    }

    @Test
    void losPagosDeUnTurnoInexistenteYDeUnoAjenoDanElMismoRechazo() {
        comoPaciente();
        when(servicioDeTurnos.obtenerTurno(INEXISTENTE)).thenReturn(null);
        when(servicioDeTurnos.obtenerTurno(TURNO_AJENO)).thenReturn(turnoDeOtroPaciente());

        assertThrows(EJBAccessException.class, () -> servicio.listarPorTurno(TURNO_AJENO));
        assertThrows(EJBAccessException.class, () -> servicio.listarPorTurno(INEXISTENTE));
        verify(pagoDAO, never()).listarPorTurno(anyLong());
    }

    @Test
    void elAdministradorSigueRecibiendoNullParaUnPagoInexistente() {
        // El resource lo traduce a 404: el administrador ve todos los pagos,
        // asi que saber que un id no existe no le revela nada.
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);
        when(pagoDAO.buscarPorId(INEXISTENTE)).thenReturn(null);

        assertNull(servicio.consultarPago(INEXISTENTE));
    }

    private void comoPaciente() {
        Usuario paciente = new Usuario("Paciente", "paciente@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE, "hash");
        paciente.setId(1L);
        Principal principal = paciente::getEmail;
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(paciente.getEmail())).thenReturn(paciente);
    }

    private static Turno turnoDeOtroPaciente() {
        Usuario otro = new Usuario("Otro", "otro@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE, "hash");
        otro.setId(2L);
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1));
        turno.setId(TURNO_AJENO);
        turno.setPaciente(otro);
        return turno;
    }
}
