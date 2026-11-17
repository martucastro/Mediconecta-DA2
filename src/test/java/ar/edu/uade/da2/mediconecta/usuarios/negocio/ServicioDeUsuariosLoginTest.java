package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.datos.UsuarioDAO;
import jakarta.ejb.SessionContext;

/**
 * SCRUM-127: el login no delata por tiempo que emails existen y limita los
 * intentos fallidos por email.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeUsuariosLoginTest {

    private static final String CLAVE = "cambiar123";

    @Mock
    private UsuarioDAO usuarioDAO;
    @Mock
    private SessionContext contexto;
    @Spy
    private LimitadorDeIntentos intentos = new LimitadorDeIntentos();

    @InjectMocks
    private ServicioDeUsuarios servicio;

    @Test
    void unEmailInexistenteTambienPagaUnaVerificacionPbkdf2() {
        when(usuarioDAO.buscarPorEmail("nadie@test.com")).thenReturn(null);

        try (MockedStatic<PasswordUtil> util = mockStatic(PasswordUtil.class, CALLS_REAL_METHODS)) {
            assertNull(servicio.autenticar("nadie@test.com", CLAVE));

            util.verify(() -> PasswordUtil.verificar(eq(CLAVE), anyString()));
        }
    }

    @Test
    void trasElMaximoDeFallosSeRechazaAunConLaContrasenaCorrecta() {
        Usuario ana = new Usuario("Ana", "ana@test.com", "PACIENTE", PasswordUtil.hash(CLAVE));
        when(usuarioDAO.buscarPorEmail("ana@test.com")).thenReturn(ana);

        for (int i = 0; i < LimitadorDeIntentos.MAXIMO_FALLOS; i++) {
            assertNull(servicio.autenticar("ana@test.com", "incorrecta"));
        }

        assertThrows(DemasiadosIntentosException.class, () -> servicio.autenticar("ana@test.com", CLAVE));
    }

    @Test
    void unEmailInexistenteSeBloqueaIgualQueUnoReal() {
        // Si solo se bloquearan los emails reales, el 429 delataria cuales existen.
        when(usuarioDAO.buscarPorEmail("nadie@test.com")).thenReturn(null);

        for (int i = 0; i < LimitadorDeIntentos.MAXIMO_FALLOS; i++) {
            servicio.autenticar("nadie@test.com", "incorrecta");
        }

        assertThrows(DemasiadosIntentosException.class, () -> servicio.autenticar("nadie@test.com", CLAVE));
    }

    @Test
    void unLoginCorrectoReiniciaElContador() {
        Usuario ana = new Usuario("Ana", "ana@test.com", "PACIENTE", PasswordUtil.hash(CLAVE));
        when(usuarioDAO.buscarPorEmail("ana@test.com")).thenReturn(ana);

        servicio.autenticar("ana@test.com", "incorrecta");
        assertSame(ana, servicio.autenticar("ana@test.com", CLAVE));

        verify(intentos).registrarExito("ana@test.com");
    }
}
