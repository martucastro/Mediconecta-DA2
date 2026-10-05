package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.EstadoSesionVideo;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.SesionVideo;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.SesionVideoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

@ExtendWith(MockitoExtension.class)
class ServicioDeTelemedicinaTest {

    private static final SalaDeVideo SALA =
            new SalaDeVideo("sala-1", "https://video/sala-1#host", "https://video/sala-1#guest");

    @Mock
    private SesionVideoDAO sesionDAO;
    @Mock
    private ProveedorDeVideoAdapter proveedor;
    @Mock
    private ServicioDeTurnos servicioDeTurnos;
    @Mock
    private ServicioDeUsuarios servicioDeUsuarios;
    @Mock
    private SessionContext contexto;

    @InjectMocks
    private ServicioDeTelemedicina servicio;

    private Usuario paciente;
    private Usuario profesional;
    private Usuario otroPaciente;

    @BeforeEach
    void preparar() {
        paciente = usuario(1L, "paciente@mediconecta.com");
        profesional = usuario(2L, "profesional@mediconecta.com");
        otroPaciente = usuario(3L, "otro@mediconecta.com");
    }

    // ---- crearSesion -----------------------------------------------------------

    @Test
    void crearSesionDeUnTurnoDeTelemedicinaGuardaLosDosEnlaces() {
        Turno turno = turno(10L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(10L)).thenReturn(turno);
        when(proveedor.crearSala(10L, turno.getFechaHora())).thenReturn(SALA);

        SesionCreada resultado = servicio.crearSesion(10L);
        SesionVideo sesion = resultado.sesion();

        assertTrue(resultado.nueva());
        ArgumentCaptor<SesionVideo> guardada = ArgumentCaptor.forClass(SesionVideo.class);
        verify(sesionDAO).guardar(guardada.capture());
        assertSame(guardada.getValue(), sesion);
        assertEquals(10L, sesion.getTurnoId());
        assertEquals(paciente.getId(), sesion.getPacienteId());
        assertEquals(profesional.getId(), sesion.getProfesionalId());
        assertEquals("sala-1", sesion.getSalaId());
        assertEquals("https://video/sala-1#host", sesion.getEnlaceProfesional());
        assertEquals("https://video/sala-1#guest", sesion.getEnlacePaciente());
        assertEquals(EstadoSesionVideo.CREADA, sesion.getEstado());
    }

    @Test
    void unTurnoEnHoldTambienPuedeTenerSala() {
        // El paso 2/2 la crea dentro de confirmarTurno, antes de marcarlo CONFIRMADO.
        Turno turno = turno(11L, ModalidadTurno.TELEMEDICINA, EstadoTurno.EN_HOLD, paciente);
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(11L)).thenReturn(turno);
        when(proveedor.crearSala(anyLong(), any())).thenReturn(SALA);

        servicio.crearSesion(11L);

        verify(sesionDAO).guardar(any());
    }

    @Test
    void unTurnoPresencialNoLlevaSalaNiLlamaAlProveedor() {
        Turno turno = turno(12L, ModalidadTurno.PRESENCIAL, EstadoTurno.CONFIRMADO, paciente);
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(12L)).thenReturn(turno);

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.crearSesion(12L));

        verify(proveedor, never()).crearSala(anyLong(), any());
        verify(sesionDAO, never()).guardar(any());
    }

    @Test
    void unTurnoSinPacienteNoLlevaSala() {
        Turno turno = turno(13L, ModalidadTurno.TELEMEDICINA, EstadoTurno.DISPONIBLE, null);
        autenticadoComo(profesional);
        when(servicioDeTurnos.obtenerTurno(13L)).thenReturn(turno);

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.crearSesion(13L));

        verify(proveedor, never()).crearSala(anyLong(), any());
    }

    @Test
    void unPacienteAjenoNoPuedeCrearLaSala() {
        Turno turno = turno(14L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        autenticadoComo(otroPaciente);
        when(servicioDeTurnos.obtenerTurno(14L)).thenReturn(turno);

        assertThrows(EJBAccessException.class, () -> servicio.crearSesion(14L));

        verify(proveedor, never()).crearSala(anyLong(), any());
    }

    @Test
    void siYaTieneSalaParaEstePacienteNoSePideOtra() {
        Turno turno = turno(15L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        SesionVideo existente = sesion(15L, paciente);
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(15L)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(15L)).thenReturn(existente);

        SesionCreada resultado = servicio.crearSesion(15L);

        assertSame(existente, resultado.sesion());
        assertFalse(resultado.nueva(), "Ya existia: presentacion responde 200 y no 201");

        verify(proveedor, never()).crearSala(anyLong(), any());
    }

    @Test
    void siLaSalaEraDeUnPacienteAnteriorSeRenueva() {
        Turno turno = turno(16L, ModalidadTurno.TELEMEDICINA, EstadoTurno.EN_HOLD, otroPaciente);
        SesionVideo vieja = sesion(16L, paciente);
        autenticadoComo(otroPaciente);
        when(servicioDeTurnos.obtenerTurno(16L)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(16L)).thenReturn(vieja);
        when(proveedor.crearSala(anyLong(), any())).thenReturn(SALA);
        when(sesionDAO.actualizar(vieja)).thenReturn(vieja);

        SesionCreada resultado = servicio.crearSesion(16L);
        SesionVideo renovada = resultado.sesion();

        assertTrue(resultado.nueva());

        assertEquals(otroPaciente.getId(), renovada.getPacienteId());
        assertEquals("sala-1", renovada.getSalaId());
        assertEquals("https://video/sala-1#guest", renovada.getEnlacePaciente());
    }

    @Test
    void siElProveedorNoRespondeNoSeGuardaNada() {
        Turno turno = turno(17L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(17L)).thenReturn(turno);
        when(proveedor.crearSala(anyLong(), any()))
                .thenThrow(new ProveedorDeVideoNoDisponibleException("timeout"));

        assertThrows(ProveedorDeVideoNoDisponibleException.class, () -> servicio.crearSesion(17L));

        verify(sesionDAO, never()).guardar(any());
    }

    @Test
    void sinIdDeTurnoEsUnDatoInvalido() {
        autenticadoComo(paciente);

        assertThrows(DatosInvalidosException.class, () -> servicio.crearSesion(null));
    }

    @Test
    void unTurnoInexistenteRespondeIgualQueUnoAjeno() {
        // Si respondiera distinto (400 o 404), se podria averiguar que ids existen.
        autenticadoComo(paciente);
        when(servicioDeTurnos.obtenerTurno(99L)).thenReturn(null);

        assertThrows(EJBAccessException.class, () -> servicio.crearSesion(99L));
        assertThrows(EJBAccessException.class, () -> servicio.obtenerEnlace(99L));
    }

    // ---- obtenerEnlace ---------------------------------------------------------

    @Test
    void elPacienteRecibeSoloSuEnlace() {
        prepararSesionExistente(20L);
        autenticadoComo(paciente);

        EnlaceDeSesion enlace = servicio.obtenerEnlace(20L);

        assertEquals("PACIENTE", enlace.rol());
        assertEquals("https://video/sala-1#guest", enlace.enlace());
    }

    @Test
    void elProfesionalRecibeSoloSuEnlace() {
        prepararSesionExistente(21L);
        autenticadoComo(profesional);

        EnlaceDeSesion enlace = servicio.obtenerEnlace(21L);

        assertEquals("PROFESIONAL", enlace.rol());
        assertEquals("https://video/sala-1#host", enlace.enlace());
    }

    @Test
    void unPacienteAjenoRecibe403() {
        Turno turno = turno(22L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        when(servicioDeTurnos.obtenerTurno(22L)).thenReturn(turno);
        autenticadoComo(otroPaciente);

        assertThrows(EJBAccessException.class, () -> servicio.obtenerEnlace(22L));

        verify(sesionDAO, never()).buscarPorTurno(anyLong());
    }

    @Test
    void sinSalaDevuelveNull() {
        Turno turno = turno(23L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        when(servicioDeTurnos.obtenerTurno(23L)).thenReturn(turno);
        autenticadoComo(paciente);

        assertNull(servicio.obtenerEnlace(23L));
    }

    @Test
    void elNuevoPacienteNoVeLaSalaDelPacienteAnterior() {
        Turno turno = turno(24L, ModalidadTurno.TELEMEDICINA, EstadoTurno.EN_HOLD, otroPaciente);
        when(servicioDeTurnos.obtenerTurno(24L)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(24L)).thenReturn(sesion(24L, paciente));
        autenticadoComo(otroPaciente);

        assertNull(servicio.obtenerEnlace(24L));
    }

    @Test
    void elProfesionalNoVeLaSalaVieja() {
        // Otro paciente tomo el turno y la sala todavia no se renovo.
        Turno turno = turno(25L, ModalidadTurno.TELEMEDICINA, EstadoTurno.EN_HOLD, otroPaciente);
        when(servicioDeTurnos.obtenerTurno(25L)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(25L)).thenReturn(sesion(25L, paciente));
        autenticadoComo(profesional);

        assertNull(servicio.obtenerEnlace(25L));
    }

    @Test
    void despuesDeCancelarNadieVeLaSala() {
        Turno turno = turno(26L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CANCELADO, null);
        when(servicioDeTurnos.obtenerTurno(26L)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(26L)).thenReturn(sesion(26L, paciente));
        autenticadoComo(profesional);

        assertNull(servicio.obtenerEnlace(26L));
    }

    @Test
    void elPacienteAnteriorRecibe403() {
        Turno turno = turno(27L, ModalidadTurno.TELEMEDICINA, EstadoTurno.CANCELADO, null);
        when(servicioDeTurnos.obtenerTurno(27L)).thenReturn(turno);
        autenticadoComo(paciente);

        assertThrows(EJBAccessException.class, () -> servicio.obtenerEnlace(27L));
    }

    @Test
    void elAdministradorNoEstaHabilitado() {
        RolesAllowed roles = ServicioDeTelemedicina.class.getAnnotation(RolesAllowed.class);

        assertEquals(List.of(ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL),
                List.of(roles.value()));
    }

    // ---- helpers ---------------------------------------------------------------

    private void prepararSesionExistente(Long turnoId) {
        Turno turno = turno(turnoId, ModalidadTurno.TELEMEDICINA, EstadoTurno.CONFIRMADO, paciente);
        when(servicioDeTurnos.obtenerTurno(turnoId)).thenReturn(turno);
        when(sesionDAO.buscarPorTurno(turnoId)).thenReturn(sesion(turnoId, paciente));
    }

    private SesionVideo sesion(Long turnoId, Usuario pacienteDeLaSala) {
        return new SesionVideo(turnoId, pacienteDeLaSala.getId(), profesional.getId(),
                SALA.salaId(), SALA.enlaceProfesional(), SALA.enlacePaciente());
    }

    private void autenticadoComo(Usuario usuario) {
        Principal principal = usuario::getEmail;
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(usuario.getEmail())).thenReturn(usuario);
    }

    private Turno turno(Long id, ModalidadTurno modalidad, EstadoTurno estado, Usuario pacienteDelTurno) {
        Turno turno = new Turno(profesional, LocalDateTime.now().plusDays(1), modalidad, null);
        turno.setId(id);
        turno.setEstado(estado);
        turno.setPaciente(pacienteDelTurno);
        return turno;
    }

    private static Usuario usuario(Long id, String email) {
        Usuario usuario = new Usuario("Nombre", email, "PACIENTE", "hash");
        usuario.setId(id);
        return usuario;
    }
}
