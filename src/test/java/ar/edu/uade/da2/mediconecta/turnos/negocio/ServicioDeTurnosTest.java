package ar.edu.uade.da2.mediconecta.turnos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Cobertura;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.CoberturaEnLaReserva;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Prestacion;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ServicioDeObrasSocialesLocal;
import ar.edu.uade.da2.mediconecta.telemedicina.negocio.ProveedorDeVideoNoDisponibleException;
import ar.edu.uade.da2.mediconecta.telemedicina.negocio.SalaAlConfirmar;
import ar.edu.uade.da2.mediconecta.telemedicina.negocio.ServicioDeTelemedicinaLocal;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuariosLocal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.MapMessage;
import jakarta.jms.Topic;

/**
 * Modelo ampliado del turno y puntos de extension de ServicioDeTurnos.
 *
 * Los eventos se reemplazan por EventoSincronico, que se comporta como un
 * evento CDI con @Observes: los observadores corren en el mismo hilo y sus
 * excepciones salen por fire(). Asi se puede verificar lo que el flujo de
 * turnos les garantiza a las cards que se van a enganchar (SCRUM-91, 93 y 95)
 * sin levantar el contenedor. Lo que aporta el contenedor, el rollback de la
 * transaccion, depende de que esas cards usen excepciones con rollback=true.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeTurnosTest {

    @Mock
    private SessionContext contexto;
    @Mock
    private TurnoDAO turnoDAO;
    @Mock
    private ExpiradorDeHolds expirador;
    @Mock
    private ServicioDeUsuariosLocal servicioDeUsuarios;
    @Mock
    private JMSContext jmsContext;
    @Mock
    private Topic topicoTurnoConfirmado;
    @Mock
    private JMSProducer productor;

    @InjectMocks
    private ServicioDeTurnos servicio;

    private final EventoSincronico<TurnoEnReserva> eventoReserva = new EventoSincronico<>();
    private final EventoSincronico<TurnoEnConfirmacion> eventoConfirmacion = new EventoSincronico<>();
    private final EventoSincronico<TurnoEnCancelacion> eventoCancelacion = new EventoSincronico<>();

    private Usuario paciente;
    private Usuario profesional;

    @BeforeEach
    void preparar() throws Exception {
        paciente = usuario(1L, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        profesional = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        inyectar(servicio, "eventoReserva", eventoReserva);
        inyectar(servicio, "eventoConfirmacion", eventoConfirmacion);
        inyectar(servicio, "eventoCancelacion", eventoCancelacion);
    }

    // ---- Modelo y apertura de franjas -----------------------------------------

    @Test
    void unTurnoSinModalidadExplicitaEsPresencial() {
        assertEquals(ModalidadTurno.PRESENCIAL, new Turno().getModalidad());
        assertEquals(ModalidadTurno.PRESENCIAL,
                new Turno(profesional, LocalDateTime.now().plusDays(1)).getModalidad());
    }

    @Test
    void abrirDisponibilidadSinModalidadAbreUnaFranjaPresencial() {
        autenticadoComo(profesional);

        Turno turno = servicio.abrirDisponibilidad(LocalDateTime.now().plusDays(1), null,
                " Consultorio 3 ");

        assertEquals(ModalidadTurno.PRESENCIAL, turno.getModalidad());
        assertEquals("Consultorio 3", turno.getConsultorio());
        verify(turnoDAO).guardar(turno);
    }

    @Test
    void abrirDisponibilidadDeTelemedicina() {
        autenticadoComo(profesional);

        Turno turno = servicio.abrirDisponibilidad(LocalDateTime.now().plusDays(1),
                ModalidadTurno.TELEMEDICINA, null);

        assertEquals(ModalidadTurno.TELEMEDICINA, turno.getModalidad());
        assertNull(turno.getConsultorio());
    }

    @Test
    void noSePuedeAbrirUnaFranjaAMasDeDosAnios() {
        // Sin tope, "+999999-12-31T23:59:59" llegaba a PostgreSQL y salia 500
        // (timestamp out of range).
        for (LocalDateTime fechaHora : new LocalDateTime[] {
                LocalDateTime.now().plusYears(2).plusDays(1),
                LocalDateTime.of(999_999, 12, 31, 23, 59, 59) }) {
            assertThrows(DatosInvalidosException.class,
                    () -> servicio.abrirDisponibilidad(fechaHora, null, null));
        }
        verify(turnoDAO, never()).listarPorProfesional(anyLong(), any());
        verify(turnoDAO, never()).guardar(any());
    }

    @Test
    void noSePuedeAbrirDosVecesLaMismaFranja() {
        autenticadoComo(profesional);
        LocalDateTime fechaHora = LocalDateTime.now().plusDays(1)
                .withHour(9).withMinute(5).withSecond(0).withNano(0);
        when(turnoDAO.listarPorProfesional(profesional.getId(), fechaHora.toLocalDate()))
                .thenReturn(List.of(new Turno(profesional, fechaHora)));

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.abrirDisponibilidad(fechaHora, null, null));
        // La fecha se lee como la escribe una persona, no en ISO-8601.
        assertEquals(String.format("El profesional ya tiene una franja abierta el %02d/%02d/%d 09:05",
                fechaHora.getDayOfMonth(), fechaHora.getMonthValue(), fechaHora.getYear()),
                e.getMessage());
        verify(turnoDAO, never()).guardar(any());
    }

    @Test
    void laMismaFranjaConOtrosSegundosTambienEstaOcupada() {
        autenticadoComo(profesional);
        LocalDateTime minuto = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        LocalDateTime pedida = minuto.plusSeconds(42);
        when(turnoDAO.listarPorProfesional(profesional.getId(), pedida.toLocalDate()))
                .thenReturn(List.of(new Turno(profesional, minuto.plusSeconds(15).plusNanos(9))));

        assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.abrirDisponibilidad(pedida, null, null));
        verify(turnoDAO, never()).guardar(any());
    }

    @Test
    void unaFranjaCanceladaNoImpideVolverAAbrirEseHorario() {
        autenticadoComo(profesional);
        LocalDateTime fechaHora = LocalDateTime.now().plusDays(1).withNano(0);
        Turno cancelado = new Turno(profesional, fechaHora);
        cancelado.setEstado(EstadoTurno.CANCELADO);
        when(turnoDAO.listarPorProfesional(profesional.getId(), fechaHora.toLocalDate()))
                .thenReturn(List.of(cancelado, new Turno(profesional, fechaHora.plusHours(1))));

        Turno turno = servicio.abrirDisponibilidad(fechaHora, null, null);

        verify(turnoDAO).guardar(turno);
    }

    @Test
    void unaFranjaNuevaTieneLaCoberturaVacia() {
        autenticadoComo(profesional);

        Turno turno = servicio.abrirDisponibilidad(LocalDateTime.now().plusDays(1), null, null);

        assertNull(turno.getCoberturaAutorizada());
        assertNull(turno.getCoberturaPorcentaje());
        assertNull(turno.getCopago());
        assertNull(turno.getNumeroAutorizacion());
    }

    @Test
    void unaFranjaDeTelemedicinaNoLlevaConsultorio() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.abrirDisponibilidad(LocalDateTime.now().plusDays(1),
                        ModalidadTurno.TELEMEDICINA, "Consultorio 3"));
        verify(turnoDAO, never()).guardar(any());
    }

    @Test
    void elConsultorioNoPuedeSuperarLaColumna() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.abrirDisponibilidad(LocalDateTime.now().plusDays(1), null,
                        "x".repeat(61)));
    }

    // ---- Puntos de extension ---------------------------------------------------

    @Test
    void elCobroDelCopagoVaAntesQueLaSalaYLaSalaAntesQueElReclamo() {
        assertTrue(PuntosDeExtension.COBRO_COPAGO < PuntosDeExtension.SALA_DE_VIDEO);
        assertTrue(PuntosDeExtension.SALA_DE_VIDEO < PuntosDeExtension.RECLAMO);
    }

    @Test
    void alCancelarElReembolsoVaDespuesDeTodoLoQueSeEscribeEnLaBase() {
        assertTrue(PuntosDeExtension.ANULACION_RECLAMO < PuntosDeExtension.CIERRE_SALA);
        assertTrue(PuntosDeExtension.CIERRE_SALA < PuntosDeExtension.REEMBOLSO_COPAGO);
    }

    @Test
    void reservarAvisaConElPacienteYaAsignadoYAntesDeRetener() {
        Turno turno = turnoDisponible(10L);
        prepararReserva(turno);
        List<String> visto = new ArrayList<>();
        eventoReserva.observadoPor(e -> visto.add(
                e.getTurno().getPaciente().getEmail() + "/" + e.getTurno().getEstado()));

        servicio.reservarTurno(10L);

        assertEquals(List.of("paciente@mediconecta.com/DISPONIBLE"), visto);
        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        assertTrue(eventoConfirmacion.disparados().isEmpty(),
                "Reservar no puede disparar los pasos de la confirmacion (ni crear salas)");
    }

    @Test
    void reservarConElObservadorDeCoberturaDejaElCopagoEnElTurnoRetenido() throws Exception {
        Turno turno = turnoDisponible(12L);
        prepararReserva(turno);
        ServicioDeObrasSocialesLocal obrasSociales = mock(ServicioDeObrasSocialesLocal.class);
        when(obrasSociales.cotizarReserva(paciente.getId(), Prestacion.CONSULTA)).thenReturn(
                new Cobertura(true, 70, new BigDecimal("6000.00"), "AUT-OS-2002-CONSULTA", "ok"));
        eventoReserva.observadoPor(observadorDeCobertura(obrasSociales)::alReservar);

        servicio.reservarTurno(12L);

        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        assertEquals(new BigDecimal("6000.00"), turno.getCopago());
        assertEquals(new BigDecimal("70"), turno.getCoberturaPorcentaje());
        assertEquals("AUT-OS-2002-CONSULTA", turno.getNumeroAutorizacion());
    }

    @Test
    void conElLegadoCaidoLaReservaFallaEnteraYElTurnoSigueDisponible() throws Exception {
        Turno turno = turnoDisponible(13L);
        prepararReserva(turno);
        ServicioDeObrasSocialesLocal obrasSociales = mock(ServicioDeObrasSocialesLocal.class);
        when(obrasSociales.cotizarReserva(paciente.getId(), Prestacion.CONSULTA))
                .thenThrow(new ObraSocialNoDisponibleException("no respondió"));
        eventoReserva.observadoPor(observadorDeCobertura(obrasSociales)::alReservar);

        assertThrows(ObraSocialNoDisponibleException.class, () -> servicio.reservarTurno(13L));

        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(turno.getCopago());
        verify(turnoDAO, never()).actualizar(any());
        verify(expirador, never()).programar(anyLong(), anyLong());
    }

    @Test
    void noSePuedeReservarUnaFranjaQueYaPaso() {
        Turno turno = new Turno(profesional, LocalDateTime.now().minusHours(1));
        turno.setId(12L);
        prepararReserva(turno);

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.reservarTurno(12L));
        assertEquals("El turno ya pasó: no se puede reservar", e.getMessage());
        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(turno.getPaciente());
        assertTrue(eventoReserva.disparados().isEmpty());
        verify(expirador, never()).programar(anyLong(), anyLong());
    }

    @Test
    void reservarUnTurnoRetenidoDiceQueEstaReservadoEnPalabras() {
        Turno turno = turnoEnHold(14L, ModalidadTurno.PRESENCIAL);
        prepararReserva(turno);

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.reservarTurno(14L));
        assertEquals("El turno ya no está disponible: está reservado", e.getMessage());
    }

    // ---- Un hold vigente por paciente (SCRUM-132) -----------------------------

    @Test
    void unPacienteConUnHoldVigenteNoPuedeReservarOtro() {
        // Cada hold pide una autorizacion al legado de la obra social: sin
        // tope, un paciente podia retener la agenda entera.
        Turno turno = turnoDisponible(15L);
        prepararReserva(turno);
        when(turnoDAO.listarPorPaciente(paciente.getId()))
                .thenReturn(List.of(turnoEnHold(16L, ModalidadTurno.PRESENCIAL)));

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.reservarTurno(15L));

        assertTrue(e.getMessage().contains("otro turno reservado"), e.getMessage());
        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(turno.getPaciente());
        assertTrue(eventoReserva.disparados().isEmpty(), "No se pide otra autorizacion al legado");
        verify(expirador, never()).programar(anyLong(), anyLong());
    }

    @Test
    void unHoldVencidoOUnTurnoConfirmadoNoImpidenReservar() {
        Turno turno = turnoDisponible(17L);
        prepararReserva(turno);
        Turno vencido = turnoEnHold(18L, ModalidadTurno.PRESENCIAL);
        vencido.setInicioHold(LocalDateTime.now().minusMinutes(10));
        Turno confirmado = turnoEnHold(19L, ModalidadTurno.PRESENCIAL);
        confirmado.setEstado(EstadoTurno.CONFIRMADO);
        when(turnoDAO.listarPorPaciente(paciente.getId())).thenReturn(List.of(vencido, confirmado));

        servicio.reservarTurno(17L);

        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
    }

    @Test
    void siUnObservadorDeLaReservaFallaNoQuedaHold() {
        Turno turno = turnoDisponible(11L);
        prepararReserva(turno);
        eventoReserva.observadoPor(e -> {
            throw new ConflictoDeNegocioException("Cobertura rechazada");
        });

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.reservarTurno(11L));

        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        verify(turnoDAO, never()).actualizar(any());
        verify(expirador, never()).programar(anyLong(), anyLong());
    }

    @Test
    void confirmarAvisaAntesDeMarcarElTurnoConfirmado() throws Exception {
        Turno turno = turnoEnHold(20L, ModalidadTurno.PRESENCIAL);
        prepararConfirmacion(turno);
        List<EstadoTurno> visto = new ArrayList<>();
        eventoConfirmacion.observadoPor(e -> visto.add(e.getTurno().getEstado()));

        Turno confirmado = servicio.confirmarTurno(20L);

        assertEquals(List.of(EstadoTurno.EN_HOLD), visto);
        assertEquals(EstadoTurno.CONFIRMADO, confirmado.getEstado());
        assertSame(turno, eventoConfirmacion.disparados().get(0).getTurno());
        verify(productor).send(eq(topicoTurnoConfirmado), any(MapMessage.class));
    }

    @Test
    void siUnObservadorDeLaConfirmacionFallaNadaSeConfirma() {
        Turno turno = turnoEnHold(30L, ModalidadTurno.TELEMEDICINA);
        autenticadoComo(paciente);
        when(turnoDAO.buscarParaActualizar(30L)).thenReturn(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        eventoConfirmacion.observadoPor(e -> {
            throw new ConflictoDeNegocioException("Proveedor externo caido");
        });

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.confirmarTurno(30L));

        // El turno sigue retenido, con su temporizador vivo y sin TurnoConfirmado.
        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        verify(expirador, never()).cancelar(anyLong());
        verify(turnoDAO, never()).actualizar(any());
        verify(jmsContext, never()).createProducer();
    }

    @Test
    void otroPacienteRecibe403SinEnterarseDelEstadoDelTurno() {
        Turno turno = turnoEnHold(31L, ModalidadTurno.PRESENCIAL);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        autenticadoComo(usuario(9L, "otro@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE));
        when(turnoDAO.buscarParaActualizar(31L)).thenReturn(turno);

        // Antes respondia 409 "esta CONFIRMADO": le contaba el estado del turno de otro.
        assertThrows(EJBAccessException.class, () -> servicio.confirmarTurno(31L));
        assertThrows(EJBAccessException.class, () -> servicio.cancelarTurno(31L));
        verify(turnoDAO, never()).actualizar(any());
    }

    @Test
    void confirmarUnTurnoQueYaVolvioALaAgendaSigueSiendo409() {
        // Sin paciente no hay nada ajeno que proteger: HoldPage usa este 409
        // para mostrar "reserva vencida".
        when(turnoDAO.buscarParaActualizar(32L)).thenReturn(turnoDisponible(32L));

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.confirmarTurno(32L));
        // El estado se nombra con palabras, no con el enum.
        assertEquals("El turno no tiene una reserva vigente: está disponible", e.getMessage());
    }

    @Test
    void unHoldVencidoNoSeConfirmaAunqueElBarridoTodaviaNoLoLibero() {
        Turno turno = turnoEnHold(33L, ModalidadTurno.PRESENCIAL);
        turno.setInicioHold(LocalDateTime.now()
                .minusNanos(ExpiradorDeHolds.DURACION_HOLD_MS * 1_000_000).minusSeconds(1));
        autenticadoComo(paciente);
        when(turnoDAO.buscarParaActualizar(33L)).thenReturn(turno);

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.confirmarTurno(33L));
        assertEquals("La reserva del turno venció: ya no se puede confirmar", e.getMessage());
        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        assertTrue(eventoConfirmacion.disparados().isEmpty());
        verify(turnoDAO, never()).actualizar(any());
    }

    // ---- Sala de video al confirmar (SCRUM-95) --------------------------------
    //
    // El evento de confirmacion se conecta al observador real SalaAlConfirmar,
    // con ServicioDeTelemedicina como doble: se ejerce el camino
    // confirmarTurno -> fire() -> observador -> crearSesion. Lo que pasa adentro
    // de crearSesion lo cubre ServicioDeTelemedicinaTest.

    @Test
    void confirmarUnTurnoDeTelemedicinaCreaLaSalaAntesDeConfirmar() throws Exception {
        Turno turno = turnoEnHold(60L, ModalidadTurno.TELEMEDICINA);
        prepararConfirmacion(turno);
        ServicioDeTelemedicinaLocal telemedicina = mock(ServicioDeTelemedicinaLocal.class);
        List<EstadoTurno> estadoAlCrearLaSala = new ArrayList<>();
        when(telemedicina.crearSesion(60L)).thenAnswer(invocacion -> {
            estadoAlCrearLaSala.add(turno.getEstado());
            return null;
        });
        eventoConfirmacion.observadoPor(observadorDeSala(telemedicina)::alConfirmar);

        Turno confirmado = servicio.confirmarTurno(60L);

        verify(telemedicina).crearSesion(60L);
        assertEquals(List.of(EstadoTurno.EN_HOLD), estadoAlCrearLaSala);
        assertEquals(EstadoTurno.CONFIRMADO, confirmado.getEstado());
        verify(productor).send(eq(topicoTurnoConfirmado), any(MapMessage.class));
    }

    @Test
    void confirmarUnTurnoPresencialNoCreaSalaNiLlamaAlProveedor() throws Exception {
        Turno turno = turnoEnHold(61L, ModalidadTurno.PRESENCIAL);
        prepararConfirmacion(turno);
        ServicioDeTelemedicinaLocal telemedicina = mock(ServicioDeTelemedicinaLocal.class);
        eventoConfirmacion.observadoPor(observadorDeSala(telemedicina)::alConfirmar);

        Turno confirmado = servicio.confirmarTurno(61L);

        assertEquals(EstadoTurno.CONFIRMADO, confirmado.getEstado());
        verify(telemedicina, never()).crearSesion(anyLong());
    }

    @Test
    void siElProveedorDeVideoNoRespondeFallaLaConfirmacionEntera() throws Exception {
        Turno turno = turnoEnHold(62L, ModalidadTurno.TELEMEDICINA);
        autenticadoComo(paciente);
        when(turnoDAO.buscarParaActualizar(62L)).thenReturn(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        ServicioDeTelemedicinaLocal telemedicina = mock(ServicioDeTelemedicinaLocal.class);
        when(telemedicina.crearSesion(62L))
                .thenThrow(new ProveedorDeVideoNoDisponibleException("timeout"));
        eventoConfirmacion.observadoPor(observadorDeSala(telemedicina)::alConfirmar);

        assertThrows(ProveedorDeVideoNoDisponibleException.class, () -> servicio.confirmarTurno(62L));

        // Opcion B: nada de la confirmacion se ejecuto. El turno sigue retenido
        // por el paciente, con su temporizador vivo, y TurnoConfirmado no sale.
        assertEquals(EstadoTurno.EN_HOLD, turno.getEstado());
        assertSame(paciente, turno.getPaciente());
        verify(expirador, never()).cancelar(anyLong());
        verify(turnoDAO, never()).actualizar(any());
        verify(jmsContext, never()).createProducer();
    }

    // ---- Validacion de turnoId ---------------------------------------------------
    //
    // Mismo criterio que abrirDisponibilidad: un turnoId nulo es un dato
    // invalido del pedido, no un turno inexistente. Sin la validacion explicita,
    // el id nulo llega hasta TurnoDAO.buscarParaActualizar y, en produccion
    // (fuera de este mock), EntityManager.find rechaza una clave primaria nula
    // con IllegalArgumentException, que el contenedor envuelve como error de
    // sistema y el cliente recibe 500 en vez de 400.

    @Test
    void reservarConTurnoIdNuloLanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class, () -> servicio.reservarTurno(null));
        verify(turnoDAO, never()).buscarParaActualizar(any());
    }

    @Test
    void confirmarConTurnoIdNuloLanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class, () -> servicio.confirmarTurno(null));
        verify(turnoDAO, never()).buscarParaActualizar(any());
    }

    @Test
    void cancelarConTurnoIdNuloLanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class, () -> servicio.cancelarTurno(null));
        verify(turnoDAO, never()).buscarParaActualizar(any());
    }

    // ---- Liberar el turno ------------------------------------------------------

    @Test
    void liberarBorraLaCoberturaDelPacienteAnterior() {
        Turno turno = turnoEnHoldConCobertura(40L);

        turno.liberar();

        assertNull(turno.getPaciente());
        assertNull(turno.getInicioHold());
        assertNull(turno.getCoberturaAutorizada());
        assertNull(turno.getCoberturaPorcentaje());
        assertNull(turno.getCopago());
        assertNull(turno.getNumeroAutorizacion());
    }

    @Test
    void liberarUnHoldLoDevuelveADisponibleComoAlVencer() {
        Turno turno = turnoEnHoldConCobertura(41L);
        prepararReserva(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        when(turnoDAO.actualizar(turno)).thenReturn(turno);

        Turno liberado = servicio.cancelarTurno(41L);

        // Igual que ExpiradorDeHolds: vuelve a la agenda sin nada del paciente.
        assertEquals(EstadoTurno.DISPONIBLE, liberado.getEstado());
        assertNull(liberado.getPaciente());
        assertNull(liberado.getInicioHold());
        assertNull(liberado.getCoberturaAutorizada());
        assertNull(liberado.getCoberturaPorcentaje());
        assertNull(liberado.getNumeroAutorizacion());
        assertNull(liberado.getCopago());
        verify(expirador).cancelar(41L);
        // Un hold no tiene copago cobrado ni reclamo: no hay nada que compensar.
        assertTrue(eventoCancelacion.disparados().isEmpty());
    }

    @Test
    void cancelarUnTurnoConfirmadoLoDejaCancelado() {
        Turno turno = turnoEnHoldConCobertura(42L);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        prepararReserva(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        when(turnoDAO.actualizar(turno)).thenReturn(turno);

        List<String> visto = new ArrayList<>();
        eventoCancelacion.observadoPor(e -> visto.add(
                e.getTurno().getPaciente().getEmail() + "/" + e.getTurno().getEstado()));

        Turno cancelado = servicio.cancelarTurno(42L);

        // SCRUM-128: los pasos de la cancelacion ven el turno ya CANCELADO.
        // ServicioDePagos manda todo a la base antes de reembolsar, asi que una
        // falla al escribir el turno sale antes de la llamada a la pasarela y
        // no despues, con el dinero ya devuelto.
        assertEquals(List.of("paciente@mediconecta.com/CANCELADO"), visto);

        assertEquals(EstadoTurno.CANCELADO, cancelado.getEstado());
        // Conserva al paciente: sigue viendo el turno en /mios y su pago
        // REEMBOLSADO (ServicioDePagos lo autoriza por el paciente del turno).
        assertSame(paciente, cancelado.getPaciente());
        assertEquals(new BigDecimal("1500.00"), cancelado.getCopago());
        verify(expirador).cancelar(42L);
        // Reembolso del copago y anulacion del reclamo (SCRUM-124), con el
        // paciente todavia en el turno.
        assertEquals(1, eventoCancelacion.disparados().size());
        assertSame(turno, eventoCancelacion.disparados().get(0).getTurno());
    }

    @Test
    void siLaCompensacionFallaLaCancelacionEnteraSeRevierte() {
        Turno turno = turnoEnHoldConCobertura(43L);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        prepararReserva(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        eventoCancelacion.observadoPor(e -> {
            throw new ConflictoDeNegocioException("Pasarela caida al reembolsar");
        });

        ConflictoDeNegocioException e = assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.cancelarTurno(43L));

        // El turno ya estaba CANCELADO en memoria (los pasos lo ven asi), pero
        // la excepcion sale con rollback=true: el contenedor revierte la fila
        // y el turno sigue CONFIRMADO en la base. Conserva al paciente.
        assertTrue(e.getClass().getAnnotation(jakarta.ejb.ApplicationException.class).rollback());
        assertEquals(paciente, turno.getPaciente());
        verify(turnoDAO, never()).actualizar(any());
        verify(expirador, never()).cancelar(43L);
    }

    @Test
    void cancelarUnTurnoYaCanceladoEs409() {
        Turno turno = turnoDisponible(44L);
        turno.setEstado(EstadoTurno.CANCELADO);
        when(turnoDAO.buscarParaActualizar(44L)).thenReturn(turno);
        // lenient: con el arreglo ya no se consulta quien llama (el turno no tiene paciente).
        Principal principal = paciente::getEmail;
        lenient().when(contexto.getCallerPrincipal()).thenReturn(principal);
        lenient().when(servicioDeUsuarios.obtenerPorEmail(paciente.getEmail())).thenReturn(paciente);

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.cancelarTurno(44L));
        verify(turnoDAO, never()).actualizar(any());
    }

    // ---- Mis turnos ------------------------------------------------------------

    @Test
    void elPacienteVeLosTurnosDelCallerYNoLosDeOtro() {
        autenticadoComo(paciente);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(false);
        List<Turno> suyos = List.of(turnoEnHold(50L, ModalidadTurno.PRESENCIAL));
        when(turnoDAO.listarPorPaciente(paciente.getId())).thenReturn(suyos);

        // La fecha se ignora para el paciente: no es un filtro de su vista.
        assertSame(suyos, servicio.misTurnos(LocalDate.of(2027, 3, 15)));

        verify(turnoDAO).listarPorPaciente(paciente.getId());
        verify(turnoDAO, never()).listarPorProfesional(anyLong(), any());
    }

    @Test
    void elProfesionalVeSuAgendaConElFiltroDeFecha() {
        autenticadoComo(profesional);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        LocalDate dia = LocalDate.of(2027, 3, 15);
        List<Turno> agenda = List.of(turnoDisponible(51L));
        when(turnoDAO.listarPorProfesional(profesional.getId(), dia)).thenReturn(agenda);

        assertSame(agenda, servicio.misTurnos(dia));

        verify(turnoDAO, never()).listarPorPaciente(anyLong());
    }

    @Test
    void misTurnosNoEstaHabilitadoParaElAdministrador() throws Exception {
        RolesAllowed roles = ServicioDeTurnos.class.getMethod("misTurnos", LocalDate.class)
                .getAnnotation(RolesAllowed.class);

        assertEquals(List.of(ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL),
                List.of(roles.value()));
    }

    // ---- helpers ---------------------------------------------------------------

    private Turno turnoEnHoldConCobertura(Long id) {
        Turno turno = turnoEnHold(id, ModalidadTurno.PRESENCIAL);
        turno.setCoberturaAutorizada(true);
        turno.setCoberturaPorcentaje(new java.math.BigDecimal("70"));
        turno.setCopago(new java.math.BigDecimal("1500.00"));
        turno.setNumeroAutorizacion("AUT-123");
        return turno;
    }

    private void prepararReserva(Turno turno) {
        autenticadoComo(paciente);
        when(turnoDAO.buscarParaActualizar(turno.getId())).thenReturn(turno);
    }

    private void prepararConfirmacion(Turno turno) {
        autenticadoComo(paciente);
        when(turnoDAO.buscarParaActualizar(turno.getId())).thenReturn(turno);
        when(turnoDAO.actualizar(turno)).thenReturn(turno);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        when(jmsContext.createMapMessage()).thenReturn(mock(MapMessage.class));
        when(jmsContext.createProducer()).thenReturn(productor);
    }

    private void autenticadoComo(Usuario usuario) {
        Principal principal = usuario::getEmail;
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(usuario.getEmail())).thenReturn(usuario);
    }

    private Turno turnoDisponible(Long id) {
        Turno turno = new Turno(profesional, LocalDateTime.now().plusDays(2));
        turno.setId(id);
        return turno;
    }

    private Turno turnoEnHold(Long id, ModalidadTurno modalidad) {
        Turno turno = new Turno(profesional, LocalDateTime.now().plusDays(2), modalidad, null);
        turno.setId(id);
        turno.setPaciente(paciente);
        turno.setEstado(EstadoTurno.EN_HOLD);
        turno.setInicioHold(LocalDateTime.now());
        return turno;
    }

    private static Usuario usuario(Long id, String email, String rol) {
        Usuario usuario = new Usuario("Nombre", email, rol, "hash");
        usuario.setId(id);
        return usuario;
    }

    private static CoberturaEnLaReserva observadorDeCobertura(ServicioDeObrasSocialesLocal obrasSociales)
            throws Exception {
        CoberturaEnLaReserva observador = new CoberturaEnLaReserva();
        inyectar(observador, "obrasSociales", obrasSociales);
        return observador;
    }

    private static SalaAlConfirmar observadorDeSala(ServicioDeTelemedicinaLocal telemedicina)
            throws Exception {
        SalaAlConfirmar observador = new SalaAlConfirmar();
        inyectar(observador, "servicioDeTelemedicina", telemedicina);
        return observador;
    }

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }
}
