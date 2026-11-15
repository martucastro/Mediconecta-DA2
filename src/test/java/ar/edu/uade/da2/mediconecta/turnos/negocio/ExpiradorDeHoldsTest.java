package ar.edu.uade.da2.mediconecta.turnos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;

/**
 * Liberacion de los holds vencidos (SCRUM-118).
 *
 * Los dos caminos del contenedor se invocan directamente, sin levantarlo:
 * expirar(Timer) es lo que corre el @Timeout del temporizador de cada turno y
 * barrerHoldsVencidos() lo que corre el @Schedule de cada minuto.
 *
 * El tiempo es deterministico: el expirador lee "ahora" de un reloj fijo, asi
 * que el limite que le pasa a la consulta de holds vencidos es exacto. Que
 * holds entran en esa consulta lo decide TurnoDAO.listarHoldsVencidos
 * (inicioHold < limite); aca se verifica que el limite sea el correcto y que
 * cada hold quede del lado que corresponde.
 */
@ExtendWith(MockitoExtension.class)
class ExpiradorDeHoldsTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 15, 12, 0);
    // AHORA menos DURACION_HOLD_MS (5 minutos).
    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 11, 15, 11, 55);

    @Mock
    private TurnoDAO turnoDAO;
    @Mock
    private TimerService timerService;

    @InjectMocks
    private ExpiradorDeHolds expirador;

    @BeforeEach
    void preparar() throws Exception {
        relojEn(AHORA);
    }

    // ---- Barrido periodico (@Schedule) ----------------------------------------

    @Test
    void elBarridoLiberaUnHoldVencido() {
        Turno vencido = enHold(1L, AHORA.minusMinutes(6));
        when(turnoDAO.listarHoldsVencidos(LIMITE)).thenReturn(List.of(vencido));
        when(turnoDAO.buscarParaActualizar(1L)).thenReturn(vencido);

        expirador.barrerHoldsVencidos();

        assertLiberado(vencido);
        verify(turnoDAO).actualizar(vencido);
    }

    @Test
    void elBarridoBuscaLosHoldsConElLimiteExactoDeCincoMinutos() {
        expirador.barrerHoldsVencidos();

        assertEquals(LIMITE, limiteConsultado());
        assertEquals(LIMITE, AHORA.minusNanos(ExpiradorDeHolds.DURACION_HOLD_MS * 1_000_000));
    }

    @Test
    void unHoldVigenteNoSeToca() {
        // Retenido hace 4 minutos: le queda 1. La consulta (inicioHold < limite)
        // no lo devuelve, y sin turnos para liberar no se escribe nada.
        Turno vigente = enHold(2L, AHORA.minusMinutes(4));
        when(turnoDAO.listarHoldsVencidos(LIMITE)).thenReturn(List.of());

        expirador.barrerHoldsVencidos();

        assertDespuesDelLimite(vigente.getInicioHold(), limiteConsultado());
        assertEquals(EstadoTurno.EN_HOLD, vigente.getEstado());
        verify(turnoDAO, never()).buscarParaActualizar(anyLong());
        verify(turnoDAO, never()).actualizar(any());
    }

    @Test
    void unHoldDeExactamenteCincoMinutosTodaviaNoEntraEnElBarrido() {
        // inicioHold == limite y la consulta usa "<": lo libera su temporizador,
        // o el barrido del minuto siguiente.
        expirador.barrerHoldsVencidos();

        assertEquals(LIMITE, limiteConsultado());
        assertDespuesDelLimite(AHORA.minusMinutes(5), limiteConsultado());
    }

    @Test
    void elMismoHoldQuedaVencidoCuandoElRelojAvanza() throws Exception {
        LocalDateTime inicio = AHORA.minusMinutes(4);

        expirador.barrerHoldsVencidos();
        assertDespuesDelLimite(inicio, limiteConsultado());

        relojEn(AHORA.plusMinutes(2));
        expirador.barrerHoldsVencidos();

        ArgumentCaptor<LocalDateTime> limites = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(turnoDAO, times(2)).listarHoldsVencidos(limites.capture());
        LocalDateTime segundoLimite = limites.getAllValues().get(1);
        assertEquals(AHORA.minusMinutes(3), segundoLimite);
        assertTrue(inicio.isBefore(segundoLimite), "a los 6 minutos el hold ya entra en el barrido");
    }

    @Test
    void elBarridoNoLiberaUnTurnoQueSeConfirmoMientrasTanto() {
        // La consulta lo vio EN_HOLD, pero cuando el barrido toma el lock el
        // paciente ya lo habia confirmado (ver docs/diagramas/confirmar-turno.md).
        Turno vistoPorLaConsulta = enHold(3L, AHORA.minusMinutes(6));
        Turno confirmado = confirmado(3L);
        when(turnoDAO.listarHoldsVencidos(LIMITE)).thenReturn(List.of(vistoPorLaConsulta));
        when(turnoDAO.buscarParaActualizar(3L)).thenReturn(confirmado);

        expirador.barrerHoldsVencidos();

        assertNoLiberado(confirmado);
        verify(turnoDAO, never()).actualizar(any());
    }

    // ---- Temporizador por turno (@Timeout) ------------------------------------

    @Test
    void elTemporizadorLiberaElHoldDeSuTurno() {
        Turno vencido = enHold(4L, AHORA.minusMinutes(5));
        when(turnoDAO.buscarParaActualizar(4L)).thenReturn(vencido);

        expirador.expirar(timerDe(4L));

        assertLiberado(vencido);
        verify(turnoDAO).actualizar(vencido);
    }

    @Test
    void elTemporizadorNoLiberaUnTurnoConfirmado() {
        Turno confirmado = confirmado(5L);
        when(turnoDAO.buscarParaActualizar(5L)).thenReturn(confirmado);

        expirador.expirar(timerDe(5L));

        assertNoLiberado(confirmado);
        verify(turnoDAO, never()).actualizar(any());
    }

    @Test
    void elTemporizadorDeUnTurnoQueYaNoExisteNoHaceNada() {
        when(turnoDAO.buscarParaActualizar(6L)).thenReturn(null);

        expirador.expirar(timerDe(6L));

        verify(turnoDAO, never()).actualizar(any());
    }

    // ---- Programar y cancelar -------------------------------------------------

    @Test
    void cancelarSoloApagaElTemporizadorDeEseTurno() {
        Timer delTurno = timerDe(7L);
        Timer deOtroPaciente = timerDe(8L);
        when(timerService.getTimers()).thenReturn(List.of(delTurno, deOtroPaciente));

        expirador.cancelar(7L);

        verify(delTurno).cancel();
        verify(deOtroPaciente, never()).cancel();
    }

    @Test
    void programarReemplazaElTemporizadorAnteriorYGuardaElIdDelTurno() {
        Timer anterior = timerDe(9L);
        when(timerService.getTimers()).thenReturn(List.of(anterior));

        expirador.programar(9L, ExpiradorDeHolds.DURACION_HOLD_MS);

        verify(anterior).cancel();
        ArgumentCaptor<TimerConfig> config = ArgumentCaptor.forClass(TimerConfig.class);
        verify(timerService).createSingleActionTimer(eq(ExpiradorDeHolds.DURACION_HOLD_MS), config.capture());
        assertEquals(9L, config.getValue().getInfo());
        // No persistente: el barrido cubre los holds que sobreviven a un reinicio.
        assertFalse(config.getValue().isPersistent());
    }

    // ---- Ayudas -----------------------------------------------------------

    private void relojEn(LocalDateTime instante) throws Exception {
        Field campo = ExpiradorDeHolds.class.getDeclaredField("reloj");
        campo.setAccessible(true);
        campo.set(expirador, Clock.fixed(instante.atZone(ZONA).toInstant(), ZONA));
    }

    private LocalDateTime limiteConsultado() {
        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(turnoDAO).listarHoldsVencidos(limite.capture());
        return limite.getValue();
    }

    /** Del lado vigente: la condicion inicioHold < limite de la consulta es falsa. */
    private static void assertDespuesDelLimite(LocalDateTime inicioHold, LocalDateTime limite) {
        assertFalse(inicioHold.isBefore(limite),
                "inicioHold " + inicioHold + " no tendria que entrar con el limite " + limite);
    }

    private static Timer timerDe(Long turnoId) {
        Timer timer = mock(Timer.class);
        when(timer.getInfo()).thenReturn(turnoId);
        return timer;
    }

    private static Turno enHold(Long id, LocalDateTime inicioHold) {
        Turno turno = new Turno(null, AHORA.plusDays(1));
        turno.setId(id);
        turno.setEstado(EstadoTurno.EN_HOLD);
        turno.setPaciente(paciente());
        turno.setInicioHold(inicioHold);
        turno.setCoberturaAutorizada(true);
        turno.setCoberturaPorcentaje(new BigDecimal("70"));
        turno.setCopago(new BigDecimal("6000.00"));
        turno.setNumeroAutorizacion("AUT-1");
        return turno;
    }

    private static Turno confirmado(Long id) {
        Turno turno = enHold(id, AHORA.minusMinutes(6));
        turno.setEstado(EstadoTurno.CONFIRMADO);
        return turno;
    }

    private static Usuario paciente() {
        Usuario paciente = new Usuario();
        paciente.setId(1L);
        return paciente;
    }

    private static void assertLiberado(Turno turno) {
        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(turno.getPaciente());
        assertNull(turno.getInicioHold());
        assertNull(turno.getCoberturaAutorizada());
        assertNull(turno.getCopago());
        assertNull(turno.getNumeroAutorizacion());
    }

    private static void assertNoLiberado(Turno turno) {
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertNotNull(turno.getPaciente());
        assertEquals(new BigDecimal("6000.00"), turno.getCopago());
    }
}
