package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.datos.UsuarioDAO;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SCRUM-102: GET /api/usuarios/{id} no puede exponer el email y el rol de
 * cualquiera. Un administrador consulta a todos; el resto, solo su propio id.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeUsuariosTest {

    private static final Long ID_PACIENTE = 1L;
    private static final Long ID_OTRO = 2L;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private SessionContext contexto;

    @InjectMocks
    private ServicioDeUsuarios servicio;

    private Usuario paciente;
    private Usuario otro;

    @BeforeEach
    void prepararUsuarios() {
        paciente = new Usuario("Ana Perez", "ana@test.com", "PACIENTE", "hash");
        paciente.setId(ID_PACIENTE);
        otro = new Usuario("Luis Gomez", "luis@test.com", "PACIENTE", "hash");
        otro.setId(ID_OTRO);
    }

    private void comoUsuario(Usuario quien) {
        Principal principal = quien::getEmail;
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(false);
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(usuarioDAO.buscarPorEmail(quien.getEmail())).thenReturn(quien);
    }

    @Test
    void elAdministradorPuedeConsultarAOtroUsuario() {
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(true);
        when(usuarioDAO.buscarPorId(ID_OTRO)).thenReturn(otro);

        assertSame(otro, servicio.consultarUsuario(ID_OTRO));
    }

    @Test
    void elAdministradorRecibeNullSiElUsuarioNoExiste() {
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(true);
        when(usuarioDAO.buscarPorId(99L)).thenReturn(null);

        assertNull(servicio.consultarUsuario(99L));
    }

    @Test
    void unPacientePuedeConsultarSuPropioUsuario() {
        comoUsuario(paciente);
        when(usuarioDAO.buscarPorId(ID_PACIENTE)).thenReturn(paciente);

        assertSame(paciente, servicio.consultarUsuario(ID_PACIENTE));
    }

    @Test
    void unPacienteNoPuedeConsultarAOtroUsuario() {
        comoUsuario(paciente);

        assertThrows(EJBAccessException.class,
                () -> servicio.consultarUsuario(ID_OTRO));
        // No debe ni buscar el id pedido: asi no se filtra si existe o no.
        verify(usuarioDAO, never()).buscarPorId(anyLong());
    }

    @Test
    void unProfesionalNoPuedeConsultarAOtroUsuario() {
        Usuario profesional = new Usuario("Dr. Ruiz", "ruiz@test.com", "PROFESIONAL", "hash");
        profesional.setId(10L);
        comoUsuario(profesional);

        assertThrows(EJBAccessException.class,
                () -> servicio.consultarUsuario(ID_PACIENTE));
        verify(usuarioDAO, never()).buscarPorId(anyLong());
    }

    @Test
    void siElCallerNoExisteEnLaBaseSeRechaza() {
        Principal fantasma = () -> "fantasma@test.com";
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(false);
        when(contexto.getCallerPrincipal()).thenReturn(fantasma);
        when(usuarioDAO.buscarPorEmail("fantasma@test.com")).thenReturn(null);

        assertThrows(EJBAccessException.class,
                () -> servicio.consultarUsuario(ID_PACIENTE));
        verify(usuarioDAO, never()).buscarPorId(anyLong());
    }

    @Test
    void consultarUsuarioExigeEstarAutenticadoConAlgunRolConocido() throws Exception {
        Method metodo = ServicioDeUsuarios.class.getMethod("consultarUsuario", Long.class);
        RolesAllowed anotacion = metodo.getAnnotation(RolesAllowed.class);

        assertNotNull(anotacion, "consultarUsuario debe declarar @RolesAllowed");
        Set<String> roles = new HashSet<>(Arrays.asList(anotacion.value()));
        assertEquals(3, roles.size());
        assertTrue(roles.containsAll(
                Set.of("PACIENTE", "PROFESIONAL", "ADMINISTRADOR")));
    }

    @Test
    void obtenerUsuarioInternoSigueSinRestriccion() throws Exception {
        // Lo usan ServicioDeObrasSociales y ServicioDeHistoriaClinica por dentro:
        // si alguien le pusiera @RolesAllowed, se romperian esos flujos.
        Method metodo = ServicioDeUsuarios.class.getMethod("obtenerUsuario", Long.class);

        assertNull(metodo.getAnnotation(RolesAllowed.class));
    }
}