package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
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

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinicaDAO;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ejb.SessionContext;

/**
 * El autor de una entrada clinica sale del caller autenticado, nunca del
 * cuerpo del pedido: si no, un profesional podria firmar a nombre de otro.
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

    @BeforeEach
    void preparar() {
        autor = usuario(2L, "profesional@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        otroProfesional = usuario(7L, "otro@mediconecta.com", ServicioDeUsuarios.ROL_PROFESIONAL);
        when(servicioDeUsuarios.obtenerUsuario(PACIENTE_ID)).thenReturn(
                usuario(PACIENTE_ID, "paciente@mediconecta.com", ServicioDeUsuarios.ROL_PACIENTE));
        // lenient: con el arreglo el id del cuerpo ya no se consulta.
        lenient().when(servicioDeUsuarios.obtenerUsuario(7L)).thenReturn(otroProfesional);
        when(historiaDAO.buscarPorPaciente(PACIENTE_ID)).thenReturn(new HistoriaClinica(PACIENTE_ID));
        Principal principal = autor::getEmail;
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(servicioDeUsuarios.obtenerPorEmail(autor.getEmail())).thenReturn(autor);
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
