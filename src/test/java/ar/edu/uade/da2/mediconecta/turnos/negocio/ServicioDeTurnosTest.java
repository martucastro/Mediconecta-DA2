package ar.edu.uade.da2.mediconecta.turnos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
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
    private ServicioDeUsuarios servicioDeUsuarios;
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

    private Usuario paciente;
    private Usuario profesional;

    @BeforeEach
    void preparar() throws Exception {
        paciente = usuario(1L, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        profesional = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        inyectar(servicio, "eventoReserva", eventoReserva);
        inyectar(servicio, "eventoConfirmacion", eventoConfirmacion);
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

    // ---- helpers ---------------------------------------------------------------

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

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }
}
