package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

/**
 * Pruebas de unidad de la fachada de historia clinica (SCRUM-87).
 *
 * Mockito sin contenedor: se mockean los DAO, la fachada de usuarios y el
 * SessionContext; el EntradaClinicaFactory se usa real para que la validacion
 * por tipo y el rollback de una consulta a medio registrar se ejerciten de
 * verdad y no contra un mock. Mayoria de casos negativos y de borde, incluido
 * BOLA (un paciente que pide la historia de otro -> 403).
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeHistoriaClinicaTest {

    @Mock
    private HistoriaClinicaDAO historiaDAO;
    @Mock
    private EntradaClinicaDAO entradaDAO;
    @Mock
    private ServicioDeUsuarios servicioDeUsuarios;
    @Mock
    private SessionContext contexto;

    @InjectMocks
    private ServicioDeHistoriaClinica servicio;

    private Usuario paciente;
    private Usuario profesional;

    @BeforeEach
    void preparar() throws Exception {
        paciente = usuario(1L, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        profesional = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        // Factory real: la validacion por tipo y el rollback se prueban de verdad.
        inyectar(servicio, "factory", new EntradaClinicaFactory());
    }

    // ---- crearHistoria --------------------------------------------------------

    @Test
    void crearHistoriaDeUnPacienteSinHistoriaLaPersiste() {
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(null);

        HistoriaClinica historia = servicio.crearHistoria(1L);

        assertEquals(1L, historia.getPacienteId());
        verify(historiaDAO).guardar(historia);
    }

    @Test
    void crearHistoriaCuandoYaExisteEsConflicto() {
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(new HistoriaClinica(1L));

        assertThrows(ConflictoDeNegocioException.class, () -> servicio.crearHistoria(1L));
        verify(historiaDAO, never()).guardar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void crearHistoriaConPacienteInexistenteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(9L)).thenReturn(null);

        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(9L));
    }

    @Test
    void crearHistoriaConUsuarioQueNoEsPacienteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(profesional);

        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(2L));
    }

    @Test
    void crearHistoriaConIdNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(null));
    }

    // ---- obtenerHistoriaDePaciente (autorizacion por dato / BOLA) -------------

    @Test
    void unProfesionalPuedeLeerLaHistoriaDeCualquierPaciente() {
        HistoriaClinica historia = new HistoriaClinica(1L);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);

        assertSame(historia, servicio.obtenerHistoriaDePaciente(1L));
    }

    @Test
    void unPacientePuedeLeerSuPropiaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(1L);
        autenticadoComo(paciente);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);

        assertSame(historia, servicio.obtenerHistoriaDePaciente(1L));
    }

    @Test
    void unPacienteNoPuedeLeerLaHistoriaDeOtro() {
        // BOLA/IDOR: el paciente 1 pide la historia del paciente 5.
        autenticadoComo(paciente);

        assertThrows(EJBAccessException.class, () -> servicio.obtenerHistoriaDePaciente(5L));
        verify(historiaDAO, never()).buscarPorPaciente(5L);
    }

    @Test
    void obtenerHistoriaConIdNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.obtenerHistoriaDePaciente(null));
    }

    @Test
    void obtenerHistoriaDeUnPacienteSinHistoriaDevuelveNull() {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(null);

        assertNull(servicio.obtenerHistoriaDePaciente(1L));
    }

    // ---- agregarEntrada -------------------------------------------------------

    @Test
    void agregarUnaEntradaValidaLaPersisteYLaSumaALaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(1L);
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(profesional);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);

        EntradaClinica entrada = servicio.agregarEntrada(1L, diagnosticoDTO());

        verify(entradaDAO).guardar(entrada);
        assertTrue(historia.getEntradas().contains(entrada));
    }

    @Test
    void agregarEntradaConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.agregarEntrada(1L, null));
    }

    @Test
    void agregarEntradaConProfesionalInvalidoEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(null);

        assertThrows(DatosInvalidosException.class, () -> servicio.agregarEntrada(1L, diagnosticoDTO()));
    }

    // ---- registrarConsulta (transaccion de varios pasos) ----------------------

    @Test
    void registrarConsultaGuardaElDiagnosticoYLasRecetas() {
        HistoriaClinica historia = new HistoriaClinica(1L);
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(profesional);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);

        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setProfesionalId(2L);
        datos.setDiagnostico(diagnosticoDTO());
        datos.setRecetas(List.of(recetaDTO(), recetaDTO()));

        HistoriaClinica resultado = servicio.registrarConsulta(1L, datos);

        assertEquals(3, resultado.getEntradas().size());
        verify(entradaDAO, org.mockito.Mockito.times(3))
                .guardar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrarConsultaSinDiagnosticoEsDatoInvalido() {
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setProfesionalId(2L);

        assertThrows(DatosInvalidosException.class, () -> servicio.registrarConsulta(1L, datos));
    }

    @Test
    void registrarConsultaConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.registrarConsulta(1L, null));
    }

    @Test
    void unaRecetaInvalidaCortaLaConsultaEntera() {
        // El factory rechaza la receta sin medicamento: en el contenedor esto
        // revierte la transaccion y la historia no queda a medio registrar.
        HistoriaClinica historia = new HistoriaClinica(1L);
        when(servicioDeUsuarios.obtenerUsuario(1L)).thenReturn(paciente);
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(profesional);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);

        NuevaEntradaDTO recetaRota = recetaDTO();
        recetaRota.setMedicamento(null);

        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setProfesionalId(2L);
        datos.setDiagnostico(diagnosticoDTO());
        datos.setRecetas(List.of(recetaDTO(), recetaRota));

        assertThrows(DatosInvalidosException.class, () -> servicio.registrarConsulta(1L, datos));
    }

    // ---- listarEntradas -------------------------------------------------------

    @Test
    void listarEntradasDevuelveLasDeLaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(1L);
        historia.setId(10L);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(historia);
        List<EntradaClinica> entradas = List.of();
        when(entradaDAO.listarPorHistoria(10L)).thenReturn(entradas);

        assertSame(entradas, servicio.listarEntradas(1L));
    }

    @Test
    void listarEntradasDeUnPacienteSinHistoriaDevuelveListaVacia() {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(1L)).thenReturn(null);

        assertTrue(servicio.listarEntradas(1L).isEmpty());
    }

    // ---- helpers --------------------------------------------------------------

    private void autenticadoComo(Usuario usuario) {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(false);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)).thenReturn(false);
        Principal principal = org.mockito.Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn(usuario.getEmail());
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(usuario.getEmail())).thenReturn(usuario);
    }

    private NuevaEntradaDTO diagnosticoDTO() {
        NuevaEntradaDTO d = new NuevaEntradaDTO();
        d.setTipo(TipoEntrada.DIAGNOSTICO);
        d.setProfesionalId(2L);
        d.setCodigoCIE10("I10");
        d.setDescripcion("Hipertension arterial esencial");
        return d;
    }

    private NuevaEntradaDTO recetaDTO() {
        NuevaEntradaDTO r = new NuevaEntradaDTO();
        r.setTipo(TipoEntrada.RECETA);
        r.setProfesionalId(2L);
        r.setMedicamento("Enalapril");
        r.setDosis("10 mg cada 12 h");
        r.setDiasTratamiento(30);
        return r;
    }

    private static Usuario usuario(Long id, String email, String rol) {
        Usuario u = new Usuario("Nombre", email, rol, "hash");
        u.setId(id);
        return u;
    }

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
