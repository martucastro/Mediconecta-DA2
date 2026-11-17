package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
 * El autor de una entrada clinica sale del caller autenticado, nunca del
 * cuerpo del pedido: si no, un profesional podria firmar a nombre de otro.
 *
 * Tambien cubre los casos de la fachada que sumo SCRUM-87 (commit 5bf956a):
 * crear la historia, BOLA al leerla (un paciente que pide la de otro), la
 * consulta de varios pasos y el listado. El factory es real (@Spy), asi que la
 * validacion por tipo se ejercita de verdad.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeHistoriaClinicaTest {

    private static final Long PACIENTE_ID = 3L;

    @Mock
    private HistoriaClinicaDAO historiaDAO;
    @Mock
    private EntradaClinicaDAO entradaDAO;
    @Spy
    private EntradaClinicaFactory factory = new EntradaClinicaFactory();
    @Mock
    private ServicioDeUsuarios servicioDeUsuarios;
    @Mock
    private SessionContext contexto;

    @InjectMocks
    private ServicioDeHistoriaClinica servicio;

    private Usuario autor;
    private Usuario otroProfesional;
    private Usuario paciente;

    // lenient: es el escenario comun (el profesional autor atiende al paciente
    // con historia abierta), pero los casos de validacion cortan antes de usarlo.
    @BeforeEach
    void preparar() {
        autor = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        otroProfesional = usuario(7L, "otro@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        paciente = usuario(PACIENTE_ID, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        lenient().when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID)).thenReturn(paciente);
        lenient().when(servicioDeUsuarios.obtenerUsuario(7L)).thenReturn(otroProfesional);
        lenient().when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(new HistoriaClinica(PACIENTE_ID));
        autenticadoComo(autor);
    }

    @Test
    void unaEntradaConElIdDeOtroProfesionalQuedaFirmadaPorQuienLlama() {
        NuevaEntradaDTO datos = diagnostico();
        datos.setProfesionalId(otroProfesional.getId());

        EntradaClinica entrada = servicio.agregarEntrada(PACIENTE_ID, datos);

        assertEquals(autor.getId(), entrada.getProfesionalId());
    }

    @Test
    void unaConsultaConElIdDeOtroProfesionalQuedaFirmadaPorQuienLlama() {
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setProfesionalId(otroProfesional.getId());
        datos.setDiagnostico(diagnostico());
        NuevaEntradaDTO receta = new NuevaEntradaDTO();
        receta.setMedicamento("Paracetamol");
        receta.setDosis("500mg");
        receta.setDiasTratamiento(5);
        datos.setRecetas(List.of(receta));

        servicio.registrarConsulta(PACIENTE_ID, datos);

        ArgumentCaptor<EntradaClinica> guardadas = ArgumentCaptor.forClass(EntradaClinica.class);
        verify(entradaDAO, times(2)).guardar(guardadas.capture());
        guardadas.getAllValues().forEach(e -> assertEquals(autor.getId(), e.getProfesionalId()));
    }

    @Test
    void unaRecetaNulaEnLaListaEsDatoInvalidoYNoUn500() {
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setDiagnostico(diagnostico());
        NuevaEntradaDTO receta = new NuevaEntradaDTO();
        receta.setMedicamento("Paracetamol");
        receta.setDosis("500mg");
        receta.setDiasTratamiento(5);
        // Asi llega "recetas":[{...}, null] desde JSON-B.
        datos.setRecetas(Arrays.asList(receta, null));

        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarConsulta(PACIENTE_ID, datos));
    }

    // ---- crearHistoria --------------------------------------------------------

    @Test
    void crearHistoriaDeUnPacienteSinHistoriaLaPersiste() {
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);

        HistoriaClinica historia = servicio.crearHistoria(PACIENTE_ID);

        assertEquals(PACIENTE_ID, historia.getPacienteId());
        verify(historiaDAO).guardar(historia);
    }

    @Test
    void crearHistoriaCuandoYaExisteEsConflicto() {
        assertThrows(ConflictoDeNegocioException.class, () -> servicio.crearHistoria(PACIENTE_ID));
        verify(historiaDAO, never()).guardar(any());
    }

    @Test
    void crearHistoriaConPacienteInexistenteEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(9L));
    }

    @Test
    void crearHistoriaConUsuarioQueNoEsPacienteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(autor.getId())).thenReturn(autor);

        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(autor.getId()));
    }

    @Test
    void crearHistoriaConIdNuloEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.crearHistoria(null));
    }

    // ---- obtenerHistoriaDePaciente (autorizacion por dato / BOLA) -------------

    @Test
    void unProfesionalPuedeLeerLaHistoriaDeCualquierPaciente() {
        HistoriaClinica historia = new HistoriaClinica(PACIENTE_ID);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(historia);

        assertSame(historia, servicio.obtenerHistoriaDePaciente(PACIENTE_ID));
    }

    @Test
    void unPacientePuedeLeerSuPropiaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(PACIENTE_ID);
        autenticadoComo(paciente);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(historia);

        assertSame(historia, servicio.obtenerHistoriaDePaciente(PACIENTE_ID));
    }

    @Test
    void unPacienteNoPuedeLeerLaHistoriaDeOtro() {
        // BOLA/IDOR: el paciente 3 pide la historia del paciente 5.
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
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);

        assertNull(servicio.obtenerHistoriaDePaciente(PACIENTE_ID));
    }

    // ---- agregarEntrada -------------------------------------------------------

    @Test
    void agregarUnaEntradaValidaLaPersisteYLaSumaALaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(PACIENTE_ID);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(historia);

        EntradaClinica entrada = servicio.agregarEntrada(PACIENTE_ID, diagnostico());

        verify(entradaDAO).guardar(entrada);
        assertTrue(historia.getEntradas().contains(entrada));
    }

    @Test
    void agregarEntradaConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.agregarEntrada(PACIENTE_ID, null));
    }

    // ---- registrarConsulta (transaccion de varios pasos) ----------------------

    @Test
    void registrarConsultaGuardaElDiagnosticoYLasRecetas() {
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setDiagnostico(diagnostico());
        datos.setRecetas(List.of(receta(), receta()));

        HistoriaClinica resultado = servicio.registrarConsulta(PACIENTE_ID, datos);

        assertEquals(3, resultado.getEntradas().size());
        verify(entradaDAO, times(3)).guardar(any());
    }

    @Test
    void registrarConsultaSinDiagnosticoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarConsulta(PACIENTE_ID, new RegistrarConsultaDTO()));
    }

    @Test
    void registrarConsultaConDatosNulosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.registrarConsulta(PACIENTE_ID, null));
    }

    @Test
    void unaRecetaInvalidaCortaLaConsultaEntera() {
        // El factory rechaza la receta sin medicamento: en el contenedor esto
        // revierte la transaccion y la historia no queda a medio registrar.
        NuevaEntradaDTO recetaRota = receta();
        recetaRota.setMedicamento(null);
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setDiagnostico(diagnostico());
        datos.setRecetas(List.of(receta(), recetaRota));

        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarConsulta(PACIENTE_ID, datos));

        assertTrue(e.getMessage().contains("medicamento"), e.getMessage());
    }

    // ---- listarEntradas -------------------------------------------------------

    @Test
    void listarEntradasDevuelveLasDeLaHistoria() {
        HistoriaClinica historia = new HistoriaClinica(PACIENTE_ID);
        historia.setId(10L);
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(historia);
        List<EntradaClinica> entradas = List.of();
        when(entradaDAO.listarPorHistoria(10L)).thenReturn(entradas);

        assertSame(entradas, servicio.listarEntradas(PACIENTE_ID));
    }

    @Test
    void listarEntradasDeUnPacienteSinHistoriaDevuelveListaVacia() {
        when(contexto.isCallerInRole(ServicioDeUsuarios.ROL_PROFESIONAL)).thenReturn(true);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);

        assertTrue(servicio.listarEntradas(PACIENTE_ID).isEmpty());
    }

    // ---- helpers --------------------------------------------------------------

    private void autenticadoComo(Usuario usuario) {
        Principal principal = usuario::getEmail;
        lenient().when(contexto.getCallerPrincipal()).thenReturn(principal);
        lenient().when(servicioDeUsuarios.obtenerPorEmail(usuario.getEmail())).thenReturn(usuario);
    }

    private static NuevaEntradaDTO receta() {
        NuevaEntradaDTO receta = new NuevaEntradaDTO();
        receta.setTipo(TipoEntrada.RECETA);
        receta.setMedicamento("Enalapril");
        receta.setDosis("10 mg cada 12 h");
        receta.setDiasTratamiento(30);
        return receta;
    }

    private static NuevaEntradaDTO diagnostico() {
        NuevaEntradaDTO datos = new NuevaEntradaDTO();
        datos.setTipo(TipoEntrada.DIAGNOSTICO);
        datos.setCodigoCIE10("J00");
        datos.setDescripcion("Resfrio comun");
        return datos;
    }

    private static Usuario usuario(Long id, String email, String rol) {
        Usuario usuario = new Usuario("Nombre", email, rol, "hash");
        usuario.setId(id);
        return usuario;
    }
}
