package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.CredentialValidationResult.Status;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mecanismo de autenticación Basic, a mano en vez de declarado con
 * @BasicAuthenticationMechanismDefinition (SCRUM-103).
 *
 * El mecanismo estandar de Soteria siempre agrega WWW-Authenticate en cada
 * 401, y eso es lo que hace que el navegador muestre su popup nativo de
 * credenciales antes de que el fetch del SPA pueda manejar la respuesta. La
 * unica diferencia de esta clase contra ese estandar es que omite ese
 * encabezado cuando el pedido viene marcado por el frontend con
 * X-Requested-With: XMLHttpRequest (ver frontend/src/api.ts). Un curl o un
 * Postman que no mandan ese encabezado siguen recibiendo WWW-Authenticate,
 * como corresponde a HTTP Basic.
 *
 * Todo el resto del comportamiento replica al mecanismo Basic estandar:
 * credenciales validas notifican el login al contenedor sin importar si el
 * recurso esta protegido; credenciales invalidas o ausentes solo devuelven
 * 401 si el recurso esta protegido, y si no lo esta se deja pasar el pedido
 * (doNothing) para que @DatabaseIdentityStoreDefinition en
 * ConfiguracionDeSeguridad siga siendo el unico lugar que valida contra la
 * tabla usuarios.
 */
@ApplicationScoped
public class AutenticacionBasica implements HttpAuthenticationMechanism {

    private static final String REALM = "MediConecta";
    private static final String ENCABEZADO_AUTORIZACION = "Authorization";
    private static final String PREFIJO_BASIC = "Basic ";
    private static final String ENCABEZADO_PEDIDO_AJAX = "X-Requested-With";
    private static final String VALOR_PEDIDO_AJAX = "XMLHttpRequest";

    @Inject
    private IdentityStoreHandler manejadorDeIdentidades;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
            HttpMessageContext httpMessageContext) throws AuthenticationException {

        UsernamePasswordCredential credencial = credencialDe(request);
        if (credencial != null) {
            CredentialValidationResult resultado = manejadorDeIdentidades.validate(credencial);
            if (resultado.getStatus() == Status.VALID) {
                return httpMessageContext.notifyContainerAboutLogin(resultado);
            }
        }

        if (!httpMessageContext.isProtected()) {
            return httpMessageContext.doNothing();
        }
        if (!esPedidoAjax(request)) {
            response.setHeader("WWW-Authenticate", PREFIJO_BASIC.trim() + " realm=\"" + REALM + "\"");
        }
        return httpMessageContext.responseUnauthorized();
    }

    private boolean esPedidoAjax(HttpServletRequest request) {
        return VALOR_PEDIDO_AJAX.equals(request.getHeader(ENCABEZADO_PEDIDO_AJAX));
    }

    /**
     * Decodifica el encabezado Authorization: Basic &lt;base64&gt; en
     * usuario y contraseña. Devuelve null ante cualquier forma de ausencia o
     * malformacion (sin encabezado, sin prefijo Basic, base64 invalido, o
     * sin ":" separando usuario de contraseña): esos casos se tratan igual
     * que una credencial invalida, nunca se llama a
     * IdentityStoreHandler.validate con basura.
     */
    private UsernamePasswordCredential credencialDe(HttpServletRequest request) {
        String encabezado = request.getHeader(ENCABEZADO_AUTORIZACION);
        if (encabezado == null || !encabezado.startsWith(PREFIJO_BASIC)) {
            return null;
        }

        byte[] decodificado;
        try {
            decodificado = Base64.getDecoder().decode(encabezado.substring(PREFIJO_BASIC.length()));
        } catch (IllegalArgumentException baseInvalida) {
            return null;
        }

        String credenciales = new String(decodificado, StandardCharsets.UTF_8);
        int separador = credenciales.indexOf(':');
        if (separador < 0) {
            return null;
        }

        return new UsernamePasswordCredential(
                credenciales.substring(0, separador),
                credenciales.substring(separador + 1));
    }
}
