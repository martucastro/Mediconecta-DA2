package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

/**
 * Pruebas de unidad de la fachada de pagos (SCRUM-87).
 *
 * Mockito sin contenedor: se mockean el PagoDAO, el Adapter de la pasarela, el
 * SessionContext y las fachadas de usuarios y turnos. Cubre el camino feliz y,
 * sobre todo, los casos que la catedra prueba en vivo: BOLA (cobrar/consultar
 * un turno ajeno -> 403), transicion de estado invalida (reembolsar un pago no
 * APROBADO -> 409) y la pasarela caida.
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

    private Usuario paciente;
    private Usuario otro;

    @BeforeEach
    void preparar() {
        paciente = usuario(1L, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        otro = usuario(99L, "otro@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
    }

    // ---- cobrar ---------------------------------------------------------------

    @Test
    void cobrarUnTurnoPropioConLaPasarelaOkDejaElPagoAprobado() {
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turnoDe(paciente));
        when(pasarela.cobrar(any(), any(), any()))
                .thenReturn(new ResultadoPasarela(EstadoPago.APROBADO, "tx-1"));
        when(pagoDAO.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        Pago pago = servicio.cobrar(cobro("6000.00", "ARS", "tok_abc"));

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals("tx-1", pago.getIdTransaccionExterna());
    }

    @Test
    void cobrarUnRechazoDeLaPasarelaDejaElPagoRechazadoSinExcepcion() {
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turnoDe(paciente));
        when(pasarela.cobrar(any(), any(), any()))
                .thenReturn(new ResultadoPasarela(EstadoPago.RECHAZADO, null));
        when(pagoDAO.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        Pago pago = servicio.cobrar(cobro("6000.00", "ARS", "tok_abc"));

        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
    }

    @Test
    void cobrarConLaPasarelaCaidaPropagaLaExcepcion() {
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turnoDe(paciente));
        when(pasarela.cobrar(any(), any(), any()))
                .thenThrow(new PasarelaNoDisponibleException("timeout"));

        assertThrows(PasarelaNoDisponibleException.class,
                () -> servicio.cobrar(cobro("6000.00", "ARS", "tok_abc")));
    }

    @Test
    void cobrarUnTurnoAjenoEstaProhibido() {
        // BOLA: el paciente 1 intenta cobrar un turno del paciente 99.
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turnoDe(otro));

        assertThrows(EJBAccessException.class,
                () -> servicio.cobrar(cobro("6000.00", "ARS", "tok_abc")));
    }

    @Test
    void cobrarConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.cobrar(null));
    }

    @Test
    void cobrarConMontoNoPositivoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.cobrar(cobro("0.00", "ARS", "tok_abc")));
    }

    @Test
    void cobrarConMonedaInvalidaEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.cobrar(cobro("6000.00", "AR", "tok_abc")));
    }

    @Test
    void cobrarSinTokenDeMedioDePagoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.cobrar(cobro("6000.00", "ARS", "  ")));
    }

    // ---- consultarPago --------------------------------------------------------

    @Test
    void consultarUnPagoPropioLoDevuelve() {
        Pago pago = pago(EstadoPago.APROBADO);
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);

        assertSame(pago, servicio.consultarPago(5L));
    }

    @Test
    void consultarUnPagoInexistenteDevuelveNull() {
        when(pagoDAO.buscarPorId(5L)).thenReturn(null);

        assertNull(servicio.consultarPago(5L));
    }

    @Test
    void consultarConIdNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.consultarPago(null));
    }

    @Test
    void consultarElPagoDeUnTurnoAjenoEstaProhibido() {
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago(EstadoPago.APROBADO));
        when(servicioDeTurnos.obtenerTurno(TURNO)).thenReturn(turnoDe(otro));
        autenticadoComo(paciente);

        assertThrows(EJBAccessException.class, () -> servicio.consultarPago(5L));
    }

    // ---- listarPorTurno -------------------------------------------------------

    @Test
    void listarLosPagosDeUnTurnoPropio() {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);
        List<Pago> pagos = List.of(pago(EstadoPago.APROBADO));
        when(pagoDAO.listarPorTurno(TURNO)).thenReturn(pagos);

        assertSame(pagos, servicio.listarPorTurno(TURNO));
    }

    @Test
    void listarConTurnoNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.listarPorTurno(null));
    }

    // ---- reembolsar (patron State: transicion invalida -> 409) ----------------

    @Test
    void reembolsarUnPagoAprobadoLoDejaReembolsado() {
        Pago pago = pago(EstadoPago.APROBADO);
        pago.setIdTransaccionExterna("tx-1");
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);
        when(pasarela.reembolsar("tx-1"))
                .thenReturn(new ResultadoPasarela(EstadoPago.REEMBOLSADO, "tx-1"));
        when(pagoDAO.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(EstadoPago.REEMBOLSADO, servicio.reembolsar(5L).getEstado());
    }

    @Test
    void reembolsarUnPagoQueNoEstaAprobadoEsConflicto() {
        // Transicion de estado invalida: solo un pago APROBADO se puede reembolsar.
        when(pagoDAO.buscarPorId(5L)).thenReturn(pago(EstadoPago.PENDIENTE));
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(true);

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.reembolsar(5L));
    }

    @Test
    void reembolsarUnPagoInexistenteEsDatoInvalido() {
        when(pagoDAO.buscarPorId(5L)).thenReturn(null);

        assertThrows(DatosInvalidosException.class, () -> servicio.reembolsar(5L));
    }

    // ---- helpers --------------------------------------------------------------

    private void autenticadoComo(Usuario usuario) {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(usuario.getEmail());
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(usuario.getEmail())).thenReturn(usuario);
    }

    private Turno turnoDe(Usuario paciente) {
        Turno turno = new Turno(usuario(2L, "profesional@mediconecta.com",
                ServicioDeUsuarios.ROL_PROFESIONAL), LocalDateTime.now().plusDays(1),
                ModalidadTurno.PRESENCIAL, null);
        turno.setId(TURNO);
        turno.setPaciente(paciente);
        return turno;
    }

    private static CobroDTO cobro(String monto, String moneda, String token) {
        CobroDTO dto = new CobroDTO();
        dto.setTurnoId(TURNO);
        dto.setMonto(new BigDecimal(monto));
        dto.setMoneda(moneda);
        dto.setTokenMedioDePago(token);
        return dto;
    }

    private static Pago pago(EstadoPago estado) {
        Pago pago = new Pago(TURNO, new BigDecimal("6000.00"), "ARS");
        pago.setEstado(estado);
        return pago;
    }

    private static Usuario usuario(Long id, String email, String rol) {
        Usuario u = new Usuario("Nombre", email, rol, "hash");
        u.setId(id);
        return u;
    }
}
