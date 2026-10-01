package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDAO;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AutorizacionDAO;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AutorizacionDePrestacion;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

/**
 * La fachada del Adapter, sin contenedor ni legado: el port SistemaDeObraSocial
 * es un doble, asi que lo que se verifica es lo que decide la fachada (a quien
 * le pregunta, que guarda y que rechaza), no como se habla SOAP.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeObrasSocialesTest {

    private static final Long PACIENTE_ID = 3L;
    private static final String DNI = "30333444";
    private static final String AFILIADO = "OS-2002";

    @Mock
    private SistemaDeObraSocial obraSocial;
    @Mock
    private AfiliacionDAO afiliacionDAO;
    @Mock
    private AutorizacionDAO autorizacionDAO;
    @Mock
    private ServicioDeUsuarios servicioDeUsuarios;

    @InjectMocks
    private ServicioDeObrasSociales servicio;

    // ---- validarCobertura -----------------------------------------------------

    @Test
    void validarCoberturaDevuelveLoQueDiceElLegadoYNoGuardaNada() {
        pacienteAfiliado();
        Cobertura esperada = new Cobertura(true, 70, new BigDecimal("6000.00"), null, "Cubierta al 70%");
        when(obraSocial.consultar(DNI, AFILIADO, Prestacion.CONSULTA)).thenReturn(esperada);

        Cobertura obtenida = servicio.validarCobertura(PACIENTE_ID, Prestacion.CONSULTA);

        assertSame(esperada, obtenida);
        verifyNoInteractions(autorizacionDAO);
    }

    @Test
    void validarCoberturaDeUnaPrestacionNulaEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.validarCobertura(PACIENTE_ID, null));

        verifyNoInteractions(obraSocial);
    }

    @Test
    void validarCoberturaSinIdDePacienteEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.validarCobertura(null, Prestacion.CONSULTA));

        verifyNoInteractions(obraSocial);
    }

    @Test
    void validarCoberturaDeUnUsuarioInexistenteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID)).thenReturn(null);

        assertThrows(DatosInvalidosException.class,
                () -> servicio.validarCobertura(PACIENTE_ID, Prestacion.CONSULTA));

        verifyNoInteractions(obraSocial);
    }

    @Test
    void validarCoberturaDeUnUsuarioQueNoEsPacienteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PROFESIONAL));

        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.validarCobertura(PACIENTE_ID, Prestacion.CONSULTA));

        assertTrue(e.getMessage().contains(ServicioDeUsuarios.ROL_PACIENTE));
        verifyNoInteractions(obraSocial);
    }

    @Test
    void validarCoberturaDeUnPacienteSinAfiliacionEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PACIENTE));
        when(afiliacionDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);

        assertThrows(DatosInvalidosException.class,
                () -> servicio.validarCobertura(PACIENTE_ID, Prestacion.CONSULTA));

        verifyNoInteractions(obraSocial);
    }

    // ---- autorizarPrestacion --------------------------------------------------

    @Test
    void autorizarGuardaNumeroFechaPorcentajeYCopagoCuandoSeAutoriza() {
        pacienteAfiliado();
        Cobertura autorizada = new Cobertura(true, 70, new BigDecimal("6000.00"),
                "AUT-OS-2002-CONSULTA", "Cubierta al 70%");
        when(obraSocial.autorizar(DNI, AFILIADO, Prestacion.CONSULTA)).thenReturn(autorizada);
        LocalDateTime antes = LocalDateTime.now();

        Cobertura obtenida = servicio.autorizarPrestacion(PACIENTE_ID, Prestacion.CONSULTA);

        assertSame(autorizada, obtenida);
        ArgumentCaptor<AutorizacionDePrestacion> captor = ArgumentCaptor.forClass(AutorizacionDePrestacion.class);
        verify(autorizacionDAO).guardar(captor.capture());
        AutorizacionDePrestacion guardada = captor.getValue();
        assertEquals(PACIENTE_ID, guardada.getPacienteId());
        assertEquals("CONSULTA", guardada.getCodigoPrestacion());
        assertEquals("AUT-OS-2002-CONSULTA", guardada.getNumeroAutorizacion());
        assertEquals(70, guardada.getPorcentajeCobertura());
        assertEquals(new BigDecimal("6000.00"), guardada.getCopago());
        assertNotNull(guardada.getFechaAutorizacion());
        assertTrue(!guardada.getFechaAutorizacion().isBefore(antes)
                && !guardada.getFechaAutorizacion().isAfter(LocalDateTime.now()));
    }

    @Test
    void autorizarNoGuardaNadaSiLaObraSocialNoAutoriza() {
        pacienteAfiliado();
        Cobertura rechazada = new Cobertura(false, 0, new BigDecimal("20000.00"), null, "No cubre");
        when(obraSocial.autorizar(DNI, AFILIADO, Prestacion.CONSULTA)).thenReturn(rechazada);

        Cobertura obtenida = servicio.autorizarPrestacion(PACIENTE_ID, Prestacion.CONSULTA);

        assertSame(rechazada, obtenida);
        verify(autorizacionDAO, never()).guardar(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void autorizarRechazaUnaAutorizacionSinNumeroYNoGuardaNada(String numeroVacio) {
        pacienteAfiliado();
        when(obraSocial.autorizar(DNI, AFILIADO, Prestacion.CONSULTA))
                .thenReturn(new Cobertura(true, 70, new BigDecimal("6000.00"), numeroVacio, "Cubierta"));

        assertThrows(ObraSocialNoDisponibleException.class,
                () -> servicio.autorizarPrestacion(PACIENTE_ID, Prestacion.CONSULTA));

        verify(autorizacionDAO, never()).guardar(any());
    }

    @Test
    void autorizarPropagaLaCaidaDelLegadoSinGuardarNada() {
        pacienteAfiliado();
        ObraSocialNoDisponibleException caida = new ObraSocialNoDisponibleException("sin respuesta");
        when(obraSocial.autorizar(DNI, AFILIADO, Prestacion.CONSULTA)).thenThrow(caida);

        ObraSocialNoDisponibleException e = assertThrows(ObraSocialNoDisponibleException.class,
                () -> servicio.autorizarPrestacion(PACIENTE_ID, Prestacion.CONSULTA));

        assertSame(caida, e);
        verify(autorizacionDAO, never()).guardar(any());
    }

    @Test
    void autorizarDeUnUsuarioQueNoEsPacienteEsDatoInvalidoYNoLlamaAlLegado() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_ADMINISTRADOR));

        assertThrows(DatosInvalidosException.class,
                () -> servicio.autorizarPrestacion(PACIENTE_ID, Prestacion.CONSULTA));

        verifyNoInteractions(obraSocial);
        verifyNoInteractions(autorizacionDAO);
    }

    // ---- registrarAfiliacion --------------------------------------------------

    @Test
    void registrarAfiliacionCreaLaAfiliacionDeUnPacienteQueNoTenia() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PACIENTE));
        when(afiliacionDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(null);

        AfiliacionDePaciente afiliacion = servicio.registrarAfiliacion(PACIENTE_ID, " 30333444 ", " OS-2002 ");

        assertEquals(PACIENTE_ID, afiliacion.getPacienteId());
        assertEquals("30333444", afiliacion.getDni());
        assertEquals("OS-2002", afiliacion.getNumeroAfiliado());
        verify(afiliacionDAO).guardar(afiliacion);
    }

    @Test
    void registrarAfiliacionCorrigeLosDatosDeUnaAfiliacionExistente() {
        AfiliacionDePaciente existente = new AfiliacionDePaciente(PACIENTE_ID, "1", "OS-0000");
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PACIENTE));
        when(afiliacionDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(existente);

        AfiliacionDePaciente afiliacion = servicio.registrarAfiliacion(PACIENTE_ID, DNI, AFILIADO);

        assertSame(existente, afiliacion);
        assertEquals(DNI, afiliacion.getDni());
        assertEquals(AFILIADO, afiliacion.getNumeroAfiliado());
        verify(afiliacionDAO, never()).guardar(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void registrarAfiliacionExigeDniYNumeroDeAfiliado(String vacio) {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PACIENTE));

        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarAfiliacion(PACIENTE_ID, vacio, AFILIADO));
        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarAfiliacion(PACIENTE_ID, DNI, vacio));

        verify(afiliacionDAO, never()).guardar(any());
    }

    @Test
    void registrarAfiliacionDeUnUsuarioQueNoEsPacienteEsDatoInvalido() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PROFESIONAL));

        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarAfiliacion(PACIENTE_ID, DNI, AFILIADO));

        verify(afiliacionDAO, never()).guardar(any());
    }

    // ---- ayudas ---------------------------------------------------------------

    private void pacienteAfiliado() {
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID))
                .thenReturn(usuario(PACIENTE_ID, ServicioDeUsuarios.ROL_PACIENTE));
        when(afiliacionDAO.buscarPorPaciente(PACIENTE_ID))
                .thenReturn(new AfiliacionDePaciente(PACIENTE_ID, DNI, AFILIADO));
    }

    private static Usuario usuario(Long id, String rol) {
        Usuario usuario = new Usuario("Usuario " + id, "u" + id + "@mediconecta.com", rol, "hash");
        usuario.setId(id);
        return usuario;
    }
}
