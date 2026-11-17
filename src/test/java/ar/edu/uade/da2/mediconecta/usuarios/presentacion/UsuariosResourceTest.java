package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Un POST sin cuerpo llega como DTO nulo: tiene que ser 400 (via
 * DatosInvalidosMapper), no un NullPointerException que sale como 500.
 */
@ExtendWith(MockitoExtension.class)
class UsuariosResourceTest {

    @Mock
    private ServicioDeUsuarios servicio;

    @InjectMocks
    private UsuariosResource recurso;

    @Test
    void registrarSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.registrar(null));
    }

    @Test
    void loginSinCuerpoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class, () -> recurso.login(null));
    }

    // ---- Errores en JSON (SCRUM-132) ------------------------------------------

    @Test
    void unLoginIncorrectoEs401EnJson() {
        LoginDTO credenciales = new LoginDTO();
        credenciales.setEmail("ana@test.com");
        credenciales.setContrasena("incorrecta");
        when(servicio.autenticar("ana@test.com", "incorrecta")).thenReturn(null);

        Response respuesta = recurso.login(credenciales);

        assertEquals(401, respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "Email o contraseña incorrectos."), respuesta.getEntity());
    }

    @Test
    void unUsuarioInexistenteEs404EnJson() {
        when(servicio.consultarUsuario(99L)).thenReturn(null);

        Response respuesta = recurso.obtener(99L);

        assertEquals(404, respuesta.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, respuesta.getMediaType().toString());
        assertEquals(Map.of("error", "Usuario no encontrado."), respuesta.getEntity());
    }
}
