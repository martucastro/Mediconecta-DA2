package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

/**
 * SCRUM-117: registrar una consulta y autorizacion por rol. Quien escribe lo
 * declara @RolesAllowed; que un paciente solo lea su propia historia lo decide
 * la fachada comparando el caller contra el id pedido.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeHistoriaClinicaAutorizacionTest {

    private static final Long PACIENTE_ID = 3L;
    private static final Long OTRO_PACIENTE_ID = 4L;

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

    // ---- Autorizacion declarativa -------------------------------------------

    @Test
    void soloUnProfesionalEscribeEnLaHistoria() throws Exception {
        assertEquals(Set.of("PROFESIONAL"), roles("crearHistoria", Long.class));
        assertEquals(Set.of("PROFESIONAL"), roles("agregarEntrada", Long.class, NuevaEntradaDTO.class));
        assertEquals(Set.of("PROFESIONAL"), roles("registrarConsulta", Long.class, RegistrarConsultaDTO.class));
    }

    @Test
    void losTresRolesPuedenIntentarLeer() throws Exception {
        Set<String> todos = Set.of("PROFESIONAL", "PACIENTE", "ADMINISTRADOR");
        assertEquals(todos, roles("obtenerHistoriaDePaciente", Long.class));
        assertEquals(todos, roles("listarEntradas", Long.class));
    }

    // ---- Autorizacion por dato ----------------------------------------------

    @Test
    void unPacienteLeeSuPropiaHistoria() {
        comoPaciente(PACIENTE_ID);
        HistoriaClinica propia = new HistoriaClinica(PACIENTE_ID);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(propia);

        assertSame(propia, servicio.obtenerHistoriaDePaciente(PACIENTE_ID));
    }

    @Test
    void unPacienteNoLeeLaHistoriaDeOtro() {
        comoPaciente(PACIENTE_ID);

        assertThrows(EJBAccessException.class, () -> servicio.obtenerHistoriaDePaciente(OTRO_PACIENTE_ID));
        assertThrows(EJBAccessException.class, () -> servicio.listarEntradas(OTRO_PACIENTE_ID));
        verify(historiaDAO, never()).buscarPorPaciente(anyLong());
    }

    @Test
    void unProfesionalYUnAdministradorLeenCualquierHistoria() {
        when(contexto.isCallerInRole("PROFESIONAL")).thenReturn(true);
        HistoriaClinica ajena = new HistoriaClinica(OTRO_PACIENTE_ID);
        when(historiaDAO.buscarPorPaciente(OTRO_PACIENTE_ID)).thenReturn(ajena);

        assertSame(ajena, servicio.obtenerHistoriaDePaciente(OTRO_PACIENTE_ID));

        when(contexto.isCallerInRole("PROFESIONAL")).thenReturn(false);
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(true);
        assertSame(ajena, servicio.obtenerHistoriaDePaciente(OTRO_PACIENTE_ID));
    }

    // ---- Registrar consulta -------------------------------------------------

    @Test
    void registrarUnaConsultaGuardaElDiagnosticoYCadaRecetaEnLaHistoria() {
        comoProfesional();
        pacienteExistente();
        HistoriaClinica historia = new HistoriaClinica(PACIENTE_ID);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(historia);
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setDiagnostico(diagnostico());
        datos.setRecetas(List.of(receta("Paracetamol"), receta("Ibuprofeno")));

        HistoriaClinica resultado = servicio.registrarConsulta(PACIENTE_ID, datos);

        assertSame(historia, resultado);
        List<TipoEntrada> tipos = resultado.getEntradas().stream().map(EntradaClinica::getTipo).toList();
        assertEquals(List.of(TipoEntrada.DIAGNOSTICO, TipoEntrada.RECETA, TipoEntrada.RECETA), tipos);
        verify(entradaDAO, times(3)).guardar(any());
    }

    @Test
    void laPrimeraConsultaAbreLaHistoria() {
        comoProfesional();
        pacienteExistente();
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setDiagnostico(diagnostico());

        HistoriaClinica resultado = servicio.registrarConsulta(PACIENTE_ID, datos);

        assertNotNull(resultado);
        assertEquals(PACIENTE_ID, resultado.getPacienteId());
        verify(historiaDAO).guardar(resultado);
    }

    @Test
    void unaConsultaSinDiagnosticoEsDatoInvalido() {
        RegistrarConsultaDTO datos = new RegistrarConsultaDTO();
        datos.setRecetas(List.of(receta("Paracetamol")));

        assertThrows(DatosInvalidosException.class, () -> servicio.registrarConsulta(PACIENTE_ID, datos));
        verify(entradaDAO, never()).guardar(any());
    }

    @Test
    void elErrorNoRevelaSiElIdExisteNiQueRolTiene() {
        // SCRUM-127: antes "No existe un usuario con id 99" y "El usuario 2 no
        // tiene rol PACIENTE" dejaban enumerar ids y roles.
        when(servicioDeUsuarios.obtenerUsuario(99L)).thenReturn(null);
        when(servicioDeUsuarios.obtenerUsuario(2L)).thenReturn(
                usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL));

        String inexistente = assertThrows(DatosInvalidosException.class,
                () -> servicio.crearHistoria(99L)).getMessage();
        String noPaciente = assertThrows(DatosInvalidosException.class,
                () -> servicio.crearHistoria(2L)).getMessage();

        assertEquals(inexistente, noPaciente);
        assertFalse(inexistente.contains("99"), inexistente);
        assertFalse(noPaciente.contains(ServicioDeUsuarios.ROL_PROFESIONAL), noPaciente);
    }

    private Set<String> roles(String metodo, Class<?>... parametros) throws Exception {
        Method m = ServicioDeHistoriaClinica.class.getMethod(metodo, parametros);
        return Set.of(m.getAnnotation(RolesAllowed.class).value());
    }

    private void comoPaciente(Long id) {
        Usuario paciente = usuario(id, "paciente" + id + "@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE);
        Principal principal = paciente::getEmail;
        when(contexto.isCallerInRole("PROFESIONAL")).thenReturn(false);
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(false);
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(paciente.getEmail())).thenReturn(paciente);
    }

    private void comoProfesional() {
        Usuario profesional = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        Principal principal = profesional::getEmail;
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(profesional.getEmail())).thenReturn(profesional);
    }

    private void pacienteExistente() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID)).thenReturn(
                usuario(PACIENTE_ID, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE));
    }

    private static NuevaEntradaDTO diagnostico() {
        NuevaEntradaDTO datos = new NuevaEntradaDTO();
        datos.setCodigoCIE10("J00");
        datos.setDescripcion("Resfrio comun");
        return datos;
    }

    private static NuevaEntradaDTO receta(String medicamento) {
        NuevaEntradaDTO datos = new NuevaEntradaDTO();
        datos.setMedicamento(medicamento);
        datos.setDosis("500mg");
        datos.setDiasTratamiento(5);
        return datos;
    }

    private static Usuario usuario(Long id, String email, String rol) {
        Usuario usuario = new Usuario("Nombre", email, rol, "hash");
        usuario.setId(id);
        return usuario;
    }
}
