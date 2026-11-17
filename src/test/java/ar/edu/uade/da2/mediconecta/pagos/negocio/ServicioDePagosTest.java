package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.ApplicationException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

/**
 * Reembolso del copago al cancelar un turno confirmado (SCRUM-124). El rollback
 * lo hace el contenedor; aca se verifica que la falla de la pasarela sale como
 * una excepcion con rollback=true y que antes no se toco el pago.
 *
 * Tambien cubre los casos que sumo SCRUM-87 (commit 5bf956a): BOLA (cobrar o
 * consultar el pago de un turno ajeno), validacion del cobro y la transicion
 * de estado invalida del reembolso manual (solo un pago APROBADO -> 409).
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

    // ---- Cobro ---------------------------------------------------------------

    @Test
    void unCobroRechazadoQuedaRechazadoAunqueLaConfirmacionSeRevierta() {
        comoElPacienteDelTurno();
        when(pasarela.cobrar(any(), any(), any()))
                .thenReturn(new ResultadoPasarela(EstadoPago.RECHAZADO, null));
        when(pagoDAO.actualizarEnNuevaTransaccion(any())).thenAnswer(i -> i.getArgument(0));

        Pago pago = servicio.cobrar(cobro("rechazar_demo"));

        // El rechazo es un resultado conocido: se guarda en su propia
        // transaccion, asi sobrevive al rollback de la confirmacion (409).
        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        verify(pagoDAO).guardarEnNuevaTransaccion(pago);
        verify(pagoDAO).actualizarEnNuevaTransaccion(pago);
        verify(pagoDAO, never()).actualizar(any());
    }

    @Test
    void unCobroAprobadoSeActualizaEnLaTransaccionDeLaConfirmacion() {
        comoElPacienteDelTurno();
        when(pasarela.cobrar(any(), any(), any()))
                .thenReturn(new ResultadoPasarela(EstadoPago.APROBADO, "tx-9"));
        when(pagoDAO.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(EstadoPago.APROBADO, servicio.cobrar(cobro("tok_visa_demo")).getEstado());

        verify(pagoDAO, never()).actualizarEnNuevaTransaccion(any());
    }

    @Test
    void conLaPasarelaCaidaElIntentoQuedaPendiente() {
        comoElPacienteDelTurno();
        when(pasarela.cobrar(any(), any(), any())).thenThrow(new PasarelaNoDisponibleException("caida"));

        assertThrows(PasarelaNoDisponibleException.class, () -> servicio.cobrar(cobro("caer_demo")));

        // Resultado desconocido: el rastro PENDIENTE no se toca.
        verify(pagoDAO).guardarEnNuevaTransaccion(any());
        verify(pagoDAO, never()).actualizarEnNuevaTransaccion(any());
        verify(pagoDAO, never()).actualizar(any());
    }

    @Test
    void unTokenDeMasDe255CaracteresEsDatoInvalido() {
        // Sin tope, un token de 200 KB se aceptaba y se cobraba.
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.cobrar(cobro("t".repeat(256))));

        assertTrue(e.getMessage().contains("255"), e.getMessage());

        verify(pagoDAO, never()).guardarEnNuevaTransaccion(any());
        verify(pasarela, never()).cobrar(any(), any(), any());
    }

    // ---- Reembolso al cancelar ----------------------------------------------

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

    // ---- Validacion y BOLA del cobro ------------------------------------------

    @Test
    void cobrarUnTurnoAjenoEstaProhibido() {
        // BOLA: el paciente 99 intenta cobrar el turno del paciente 1.
        comoOtroPaciente();

        assertThrows(EJBAccessException.class, () -> servicio.cobrar(cobro("tok_visa_demo")));
        verify(pasarela, never()).cobrar(any(), any(), any());
    }

    @Test
    void cobrarConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.cobrar(null));
    }

    @Test
    void cobrarConMontoNoPositivoEsDatoInvalido() {
        CobroDTO datos = cobro("tok_visa_demo");
        datos.setMonto(BigDecimal.ZERO);

        assertThrows(DatosInvalidosException.class, () -> servicio.cobrar(datos));
    }

    @Test
    void cobrarConMonedaInvalidaEsDatoInvalido() {
        CobroDTO datos = cobro("tok_visa_demo");
        datos.setMoneda("AR");

        assertThrows(DatosInvalidosException.class, () -> servicio.cobrar(datos));
    }

    @Test
    void cobrarSinTokenDeMedioDePagoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.cobrar(cobro("  ")));
    }

    // ---- Consulta y listado --------------------------------------------------

    @Test
    void unAdministradorConsultaCualquierPago() {
        Pago pago = pago(EstadoPago.APROBADO, "tx-4");
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);

        assertSame(pago, servicio.consultarPago(5L));
    }

    @Test
    void consultarUnPagoInexistenteDevuelveNull() {
        assertNull(servicio.consultarPago(5L));
    }

    @Test
    void consultarConIdNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.consultarPago(null));
    }

    @Test
    void consultarElPagoDeUnTurnoAjenoEstaProhibido() {
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago(EstadoPago.APROBADO, "tx-5"));
        comoOtroPaciente();

        assertThrows(EJBAccessException.class, () -> servicio.consultarPago(5L));
    }

    @Test
    void unAdministradorListaLosPagosDeUnTurno() {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);
        List<Pago> pagos = List.of(pago(EstadoPago.APROBADO, "tx-6"));
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(pagos);

        assertSame(pagos, servicio.listarPorTurno(TURNO));
    }

    @Test
    void listarConTurnoNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.listarPorTurno(null));
    }

    // ---- Reembolso manual (transicion de estado invalida -> 409) -------------

    @Test
    void reembolsarUnPagoAprobadoLoDejaReembolsado() {
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago(EstadoPago.APROBADO, "tx-7"));
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);
        when(pasarela.reembolsar("tx-7"))
                .thenReturn(new ResultadoPasarela(EstadoPago.REEMBOLSADO, "tx-7"));
        when(pagoDAO.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(EstadoPago.REEMBOLSADO, servicio.reembolsar(5L).getEstado());
    }

    @Test
    void reembolsarUnPagoQueNoEstaAprobadoEsConflicto() {
        // Solo un pago APROBADO se puede reembolsar.
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago(EstadoPago.PENDIENTE, null));
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.reembolsar(5L));
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    void reembolsarUnPagoInexistenteEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.reembolsar(5L));
    }

    /** El turno es del paciente 1, pero quien llama es el paciente 99. */
    private void comoOtroPaciente() {
        Usuario duenio = new Usuario("Paciente", "paciente@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE, "hash");
        duenio.setId(1L);
        Usuario otro = new Usuario("Otro", "otro@mediconecta.com",
                ServicioDeUsuarios.ROL_PACIENTE, "hash");
        otro.setId(99L);
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1));
        turno.setId(TURNO);
        turno.setPaciente(duenio);
        Principal principal = otro::getEmail;
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turno);
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(otro.getEmail())).thenReturn(otro);
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

    private static CobroDTO cobro(String token) {
        CobroDTO cobro = new CobroDTO();
        cobro.setTurnoId(TURNO);
        cobro.setMonto(new BigDecimal("6000.00"));
        cobro.setMoneda("ARS");
        cobro.setTokenMedioDePago(token);
        return cobro;
    }

    private static Pago pago(EstadoPago estado, String idTransaccion) {
        Pago pago = new Pago(TURNO, new BigDecimal("6000.00"), "ARS");
        pago.setEstado(estado);
        pago.setIdTransaccionExterna(idTransaccion);
        return pago;
    }
}
