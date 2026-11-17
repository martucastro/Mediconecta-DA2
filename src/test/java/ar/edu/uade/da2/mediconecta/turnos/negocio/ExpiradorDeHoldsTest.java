package ar.edu.uade.da2.mediconecta.turnos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
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
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.NoSuchObjectLocalException;
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
 * que el limite que le pasa a la consulta de holds vencidos es exacto. La
 * consulta en si (JPQL de TurnoDAO.listarHoldsVencidos) no se ejecuta: la
 * reemplaza conLaBase con la misma condicion. Si se cambia esa JPQL, hay que
 * cambiar tambien conLaBase.
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

    // ---- Concurrencia del singleton -------------------------------------------

    @Test
    void elSingletonNoTomaElLockDeEscrituraDelContenedor() {
        // Con el WRITE por defecto, un @Timeout esperando la fila de un turno
        // que confirmarTurno tiene bloqueada frenaba a esa confirmacion en
        // expirador.cancelar: deadlock hasta el timeout del lock.
        Lock lock = ExpiradorDeHolds.class.getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockType.READ, lock.value());
    }

    // ---- Barrido periodico (@Schedule) ----------------------------------------

    @Test
    void elBarridoLiberaUnHoldVencido() {
        Turno vencido = enHold(1L, AHORA.minusMinutes(6));
        conLaBase(vencido);

        expirador.barrerHoldsVencidos();

        assertLiberado(vencido);
        verify(turnoDAO).actualizar(vencido);
    }

    @Test
    void unHoldVigenteNoSeToca() {
        // Retenido hace 4 minutos: le queda 1. Comparte el barrido con uno
        // vencido, y solo se libera el vencido.
        Turno vencido = enHold(1L, AHORA.minusMinutes(6));
        Turno vigente = enHold(2L, AHORA.minusMinutes(4));
        conLaBase(vencido, vigente);

        expirador.barrerHoldsVencidos();

        assertLiberado(vencido);
        assertSigueRetenido(vigente);
        verify(turnoDAO, times(1)).actualizar(any());
    }

    @Test
    void unHoldDeExactamenteCincoMinutosTodaviaNoLoLiberaElBarrido() {
        // inicioHold == limite y la consulta usa "<": lo libera su temporizador
        // o, si se perdio (reinicio), el barrido del minuto siguiente.
        Turno justo = enHold(3L, AHORA.minusMinutes(5));
        conLaBase(justo);

        expirador.barrerHoldsVencidos();

        assertSigueRetenido(justo);
        verify(turnoDAO, never()).actualizar(any());
    }

    @Test
    void elMismoHoldSeLiberaCuandoElRelojAvanza() throws Exception {
        Turno hold = enHold(4L, AHORA.minusMinutes(4));
        conLaBase(hold);

        expirador.barrerHoldsVencidos();
        assertSigueRetenido(hold);

        relojEn(AHORA.plusMinutes(2));
        expirador.barrerHoldsVencidos();
        assertLiberado(hold);
    }

    @Test
    void elBarridoBuscaLosHoldsConElLimiteExactoDeCincoMinutos() {
        expirador.barrerHoldsVencidos();

        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(turnoDAO).listarHoldsVencidos(limite.capture());
        assertEquals(LIMITE, limite.getValue());
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

    @Test
    void elBarridoNoPisaLaConfirmacionSobreLaMismaInstancia() {
        // Caso real: la consulta y el lock devuelven la MISMA entidad del
        // contexto de persistencia. buscarParaActualizar la relee al tomar el
        // lock (em.refresh), asi que el barrido ve CONFIRMADO y no la toca.
        Turno turno = enHold(10L, AHORA.minusMinutes(6));
        when(turnoDAO.listarHoldsVencidos(LIMITE)).thenReturn(List.of(turno));
        when(turnoDAO.buscarParaActualizar(10L)).thenAnswer(invocacion -> {
            turno.setEstado(EstadoTurno.CONFIRMADO);
            return turno;
        });

        expirador.barrerHoldsVencidos();

        assertNoLiberado(turno);
        verify(turnoDAO, never()).actualizar(any());
    }

    @Test
    void elBarridoNoLiberaElHoldNuevoDeOtroPaciente() {
        // La consulta vio el hold vencido de A, pero antes del lock el
        // temporizador de A lo libero y B lo volvio a reservar: la relectura
        // trae el hold de B, que todavia esta vigente.
        Turno vistoPorLaConsulta = enHold(11L, AHORA.minusMinutes(6));
        Turno holdNuevo = enHold(11L, AHORA.minusMinutes(1));
        when(turnoDAO.listarHoldsVencidos(LIMITE)).thenReturn(List.of(vistoPorLaConsulta));
        when(turnoDAO.buscarParaActualizar(11L)).thenReturn(holdNuevo);

        expirador.barrerHoldsVencidos();

        assertSigueRetenido(holdNuevo);
        verify(turnoDAO, never()).actualizar(any());
    }

    // ---- Temporizador por turno (@Timeout) ------------------------------------

    @Test
    void elTemporizadorNoLiberaUnHoldRecienTomado() {
        // El temporizador de A ya estaba corriendo cuando el barrido libero el
        // turno y B lo reservo: ese hold es de B y lo libera su temporizador.
        Turno holdNuevo = enHold(12L, AHORA.minusSeconds(30));
        when(turnoDAO.buscarParaActualizar(12L)).thenReturn(holdNuevo);

        expirador.expirar(timerDe(12L));

        assertSigueRetenido(holdNuevo);
        verify(turnoDAO, never()).actualizar(any());
    }

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
    void unTemporizadorQueOtroPedidoYaCanceloNoHaceFallarLaCancelacion() {
        // Entre getTimers() y getInfo() otra transaccion cancelo ese timer:
        // WildFly lanza NoSuchObjectLocalException (WFLYEJB0331). Antes salia
        // del metodo, revertia la reserva o la cancelacion entera y daba 500
        // (14 veces en 27.600 iteraciones de la prueba de carga).
        Timer desaparecido = mock(Timer.class);
        when(desaparecido.getInfo()).thenThrow(new NoSuchObjectLocalException("WFLYEJB0331"));
        Timer delTurno = timerDe(7L);
        when(timerService.getTimers()).thenReturn(List.of(desaparecido, delTurno));

        expirador.cancelar(7L);

        verify(delTurno).cancel();
    }

    @Test
    void siElTemporizadorDelTurnoDesapareceAntesDeCancelarloNoFalla() {
        Timer delTurno = timerDe(7L);
        doThrow(new NoSuchObjectLocalException("WFLYEJB0331")).when(delTurno).cancel();
        when(timerService.getTimers()).thenReturn(List.of(delTurno));

        expirador.cancelar(7L);
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

    /**
     * Base en memoria para el barrido. listarHoldsVencidos aplica la misma
     * condicion que su JPQL en TurnoDAO (estado EN_HOLD e inicioHold < limite)
     * y buscarParaActualizar devuelve el turno por id, asi los holds vigentes
     * pasan de verdad por el barrido en vez de quedar afuera por el stub.
     */
    private void conLaBase(Turno... turnos) {
        List<Turno> base = List.of(turnos);
        when(turnoDAO.listarHoldsVencidos(any())).thenAnswer(invocacion -> {
            LocalDateTime limite = invocacion.getArgument(0);
            return base.stream()
                    .filter(t -> t.getEstado() == EstadoTurno.EN_HOLD
                            && t.getInicioHold() != null && t.getInicioHold().isBefore(limite))
                    .toList();
        });
        // lenient: en los tests donde nada vencio, el barrido no llega a pedir el lock.
        lenient().when(turnoDAO.buscarParaActualizar(anyLong())).thenAnswer(invocacion -> base.stream()
                .filter(t -> t.getId().equals(invocacion.getArgument(0)))
                .findFirst()
                .orElse(null));
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

    private static void assertSigueRetenido(Turno turno) {
        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        assertNotNull(turno.getPaciente());
        assertNotNull(turno.getInicioHold());
        assertEquals(new BigDecimal("6000.00"), turno.getCopago());
    }

    private static void assertNoLiberado(Turno turno) {
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertNotNull(turno.getPaciente());
        assertEquals(new BigDecimal("6000.00"), turno.getCopago());
    }
}
