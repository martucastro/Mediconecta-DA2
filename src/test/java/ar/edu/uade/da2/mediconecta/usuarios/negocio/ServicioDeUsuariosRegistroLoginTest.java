package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.datos.UsuarioDAO;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;

@ExtendWith(MockitoExtension.class)
class ServicioDeUsuariosRegistroLoginTest {

    @Mock
    private UsuarioDAO usuarioDAO;
    @Mock
    private SessionContext contexto;

    @InjectMocks
    private ServicioDeUsuarios servicio;

    // ---------- registrarUsuario ----------

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, a@a.com, PACIENTE, clave123",
            "'', a@a.com, PACIENTE, clave123",
            "Ana, NULL, PACIENTE, clave123",
            "Ana, '', PACIENTE, clave123",
            "Ana, a@a.com, NULL, clave123",
            "Ana, a@a.com, '', clave123",
            "Ana, a@a.com, PACIENTE, NULL",
            "Ana, a@a.com, PACIENTE, ''"
    })
    void registrarUsuarioConDatosVaciosONulosLanzaDatosInvalidos(
            String nombre, String email, String rol, String contrasena) {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarUsuario(nombre, email, rol, contrasena));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void registrarUsuarioConRolInexistenteLanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.registrarUsuario("Ana", "a@a.com", "SUPERUSUARIO", "clave123"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void registrarPacienteSinSerAdministradorGuardaElUsuario() {
        when(usuarioDAO.buscarPorEmail("a@a.com")).thenReturn(null);

        Usuario resultado = servicio.registrarUsuario("Ana", "a@a.com", "PACIENTE", "clave123");

        verify(usuarioDAO).guardar(resultado);
    }

    @Test
    void registrarProfesionalSinSerAdministradorLanzaAccesoDenegado() {
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(false);

        assertThrows(EJBAccessException.class,
                () -> servicio.registrarUsuario("Dr. Perez", "p@a.com", "PROFESIONAL", "clave123"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void registrarProfesionalSiendoAdministradorGuardaElUsuario() {
        when(contexto.isCallerInRole("ADMINISTRADOR")).thenReturn(true);
        when(usuarioDAO.buscarPorEmail("p@a.com")).thenReturn(null);

        Usuario resultado = servicio.registrarUsuario("Dr. Perez", "p@a.com", "PROFESIONAL", "clave123");

        verify(usuarioDAO).guardar(resultado);
    }

    @Test
    void registrarUsuarioConEmailExistenteLanzaConflicto() {
        when(usuarioDAO.buscarPorEmail("a@a.com")).thenReturn(new Usuario("Otra", "a@a.com", "PACIENTE", "h"));

        assertThrows(ConflictoDeNegocioException.class,
                () -> servicio.registrarUsuario("Ana", "a@a.com", "PACIENTE", "clave123"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void registrarUsuarioGuardaUnHashYNoLaContrasenaPlana() {
        when(usuarioDAO.buscarPorEmail("a@a.com")).thenReturn(null);

        servicio.registrarUsuario("Ana", "a@a.com", "PACIENTE", "clave123");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioDAO).guardar(captor.capture());
        String hash = captor.getValue().getContrasenaHash();
        assertNotEquals("clave123", hash);
        assertTrue(PasswordUtil.verificar("clave123", hash));
    }

    // ---------- autenticar ----------

    @Test
    void autenticarConUsuarioInexistenteDevuelveNull() {
        when(usuarioDAO.buscarPorEmail("x@a.com")).thenReturn(null);

        assertNull(servicio.autenticar("x@a.com", "clave123"));
    }

    @Test
    void autenticarConContrasenaIncorrectaDevuelveNull() {
        Usuario usuario = new Usuario("Ana", "a@a.com", "PACIENTE", PasswordUtil.hash("clave123"));
        when(usuarioDAO.buscarPorEmail("a@a.com")).thenReturn(usuario);

        assertNull(servicio.autenticar("a@a.com", "otraClave"));
    }

    @Test
    void autenticarConCredencialesValidasDevuelveElUsuario() {
        Usuario usuario = new Usuario("Ana", "a@a.com", "PACIENTE", PasswordUtil.hash("clave123"));
        when(usuarioDAO.buscarPorEmail("a@a.com")).thenReturn(usuario);

        assertSame(usuario, servicio.autenticar("a@a.com", "clave123"));
    }
}
