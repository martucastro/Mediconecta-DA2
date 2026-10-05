package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AutenticacionBasica reemplaza a @BasicAuthenticationMechanismDefinition
 * (SCRUM-103): un 401 contra la API no debe disparar el popup nativo del
 * navegador. La diferencia con el mecanismo estandar de Soteria es unicamente
 * el encabezado WWW-Authenticate: se omite cuando el pedido viene marcado por
 * el SPA con X-Requested-With: XMLHttpRequest. El resto del comportamiento
 * (validar, notificar login, proteger recursos, doNothing en los demas casos)
 * tiene que ser idéntico al mecanismo Basic estandar.
 */
@ExtendWith(MockitoExtension.class)
class AutenticacionBasicaTest {

    private static final String REALM_ESPERADO = "Basic realm=\"MediConecta\"";

    @Mock
    private IdentityStoreHandler manejadorDeIdentidades;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpMessageContext contexto;

    @InjectMocks
    private AutenticacionBasica mecanismo;

    @BeforeEach
    void preparar() throws Exception {
        // Por defecto no hay encabezado AJAX; cada test lo pisa si lo necesita.
        // lenient() porque los casos que no llegan a tocar el encabezado
        // (login valido, recurso no protegido) no lo consumen.
        lenient().when(request.getHeader("X-Requested-With")).thenReturn(null);
    }

    private void conAuthorization(String email, String contrasena) {
        String credenciales = email + ":" + contrasena;
        String base64 = Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8));
        when(request.getHeader("Authorization")).thenReturn("Basic " + base64);
    }

    /** UsernamePasswordCredential no tiene equals(): se compara por contenido. */
    private UsernamePasswordCredential credencialCapturada() {
        ArgumentCaptor<UsernamePasswordCredential> captor = ArgumentCaptor.forClass(UsernamePasswordCredential.class);
        verify(manejadorDeIdentidades).validate(captor.capture());
        return captor.getValue();
    }

    @Test
    void credencialesValidas_notificaAlContenedor() throws Exception {
        conAuthorization("admin@mediconecta.com", "cambiar123");
        CredentialValidationResult valido = new CredentialValidationResult("admin@mediconecta.com");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(valido);
        when(contexto.notifyContainerAboutLogin(valido)).thenReturn(AuthenticationStatus.SUCCESS);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SUCCESS, resultado);
        verify(contexto).notifyContainerAboutLogin(valido);
        verify(contexto, never()).responseUnauthorized();
        verify(response, never()).setHeader(any(), any());
    }

    @Test
    void credencialesValidas_decodificaUsuarioYContrasenaCorrectamente() throws Exception {
        conAuthorization("admin@mediconecta.com", "cambiar123");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        mecanismo.validateRequest(request, response, contexto);

        UsernamePasswordCredential capturada = credencialCapturada();
        assertEquals("admin@mediconecta.com", capturada.getCaller());
        assertEquals("cambiar123", capturada.getPasswordAsString());
    }

    @Test
    void contrasenaConDosPuntos_separaSoloEnElPrimerDosPuntos() throws Exception {
        // "clave:con:dos:puntos" debe quedar como password completo, no truncado.
        conAuthorization("admin@mediconecta.com", "clave:con:dos:puntos");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(false);

        mecanismo.validateRequest(request, response, contexto);

        UsernamePasswordCredential capturada = credencialCapturada();
        assertEquals("admin@mediconecta.com", capturada.getCaller());
        assertEquals("clave:con:dos:puntos", capturada.getPasswordAsString());
    }

    @Test
    void usuarioConCaracterUtf8_seDecodificaCorrectamente() throws Exception {
        conAuthorization("ñandu@mediconecta.com", "cambiar123");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(false);

        mecanismo.validateRequest(request, response, contexto);

        UsernamePasswordCredential capturada = credencialCapturada();
        assertEquals("ñandu@mediconecta.com", capturada.getCaller());
        assertEquals("cambiar123", capturada.getPasswordAsString());
    }

    @Test
    void credencialesInvalidas_recursoProtegido_sinEncabezadoAjax_devuelve401ConWwwAuthenticate() throws Exception {
        conAuthorization("admin@mediconecta.com", "incorrecta");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SEND_FAILURE, resultado);
        verify(response).setHeader("WWW-Authenticate", REALM_ESPERADO);
        verify(contexto, never()).notifyContainerAboutLogin(any(CredentialValidationResult.class));
    }

    @Test
    void credencialesInvalidas_recursoProtegido_conEncabezadoAjax_devuelve401SinWwwAuthenticate() throws Exception {
        conAuthorization("admin@mediconecta.com", "incorrecta");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SEND_FAILURE, resultado);
        verify(response, never()).setHeader(any(), any());
    }

    @Test
    void credencialesInvalidas_recursoNoProtegido_noHaceNada() throws Exception {
        conAuthorization("admin@mediconecta.com", "incorrecta");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(CredentialValidationResult.INVALID_RESULT);
        when(contexto.isProtected()).thenReturn(false);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.NOT_DONE, resultado);
        verify(response, never()).setHeader(any(), any());
        verify(contexto, never()).responseUnauthorized();
    }

    @Test
    void sinEncabezadoAuthorization_recursoProtegido_sinAjax_devuelve401ConWwwAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SEND_FAILURE, resultado);
        verify(response).setHeader("WWW-Authenticate", REALM_ESPERADO);
        verify(manejadorDeIdentidades, never()).validate(any());
    }

    @Test
    void sinEncabezadoAuthorization_recursoProtegido_conAjax_devuelve401SinWwwAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SEND_FAILURE, resultado);
        verify(response, never()).setHeader(any(), any());
    }

    @Test
    void sinEncabezadoAuthorization_recursoNoProtegido_noHaceNada() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(contexto.isProtected()).thenReturn(false);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.NOT_DONE, resultado);
        verify(manejadorDeIdentidades, never()).validate(any());
        verify(response, never()).setHeader(any(), any());
    }

    @Test
    void encabezadoMalformado_noBase64_seTrataComoCredencialInvalida() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic esto-no-es-base64-valido!!");
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SEND_FAILURE, resultado);
        verify(manejadorDeIdentidades, never()).validate(any());
        verify(response).setHeader("WWW-Authenticate", REALM_ESPERADO);
    }

    @Test
    void encabezadoMalformado_sinDosPuntos_seTrataComoCredencialInvalida() throws Exception {
        String base64SinDosPuntos = Base64.getEncoder()
                .encodeToString("usuariosincontrasena".getBytes(StandardCharsets.UTF_8));
        when(request.getHeader("Authorization")).thenReturn("Basic " + base64SinDosPuntos);
        when(contexto.isProtected()).thenReturn(false);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.NOT_DONE, resultado);
        verify(manejadorDeIdentidades, never()).validate(any());
    }

    @Test
    void credencialesValidas_seNotificaAunCuandoElRecursoNoEsProtegido() throws Exception {
        // Un login valido siempre se propaga: Soteria no condiciona
        // notifyContainerAboutLogin a isProtected().
        conAuthorization("admin@mediconecta.com", "cambiar123");
        CredentialValidationResult valido = new CredentialValidationResult("admin@mediconecta.com");
        when(manejadorDeIdentidades.validate(any(Credential.class))).thenReturn(valido);
        when(contexto.notifyContainerAboutLogin(valido)).thenReturn(AuthenticationStatus.SUCCESS);

        AuthenticationStatus resultado = mecanismo.validateRequest(request, response, contexto);

        assertEquals(AuthenticationStatus.SUCCESS, resultado);
        verify(contexto, never()).isProtected();
    }
}
