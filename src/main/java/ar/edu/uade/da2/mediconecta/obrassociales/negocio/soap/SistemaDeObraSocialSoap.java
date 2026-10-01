package ar.edu.uade.da2.mediconecta.obrassociales.datos.soap;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import ar.edu.uade.da2.mediconecta.obrassociales.datos.LegadoNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.PedidoRechazadoPorLegadoException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.RespuestaDelLegado;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.SistemaDeObraSocial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPBinding;
import jakarta.xml.ws.soap.SOAPFaultException;

/**
 * Patrón Adapter, lado de afuera: traduce la interfaz SistemaDeObraSocial a
 * llamadas SOAP contra el legado, y las respuestas y errores SOAP de vuelta a
 * tipos propios. Es la única clase del componente que conoce JAX-WS.
 *
 * El Service se crea sin pedir el WSDL al legado (el contrato sale de las
 * anotaciones de ObraSocialLegadoPort). Si lo descargara, construir el cliente
 * ya fallaría con el legado caído, y ese error ocurriría fuera de los timeouts.
 *
 * Los timeouts son explícitos: sin ellos, un legado que acepta la conexión y no
 * contesta deja al hilo del pedido esperando indefinidamente, y con él a la
 * transacción y al usuario.
 *
 * URL y timeouts se leen de propiedades de sistema en cada llamada, así que se
 * pueden cambiar en caliente desde jboss-cli (por ejemplo, para simular el
 * legado caído apuntando a un puerto cerrado).
 */
@ApplicationScoped
public class SistemaDeObraSocialSoap implements SistemaDeObraSocial {

    private static final Logger LOGGER = Logger.getLogger(SistemaDeObraSocialSoap.class.getName());

    static final String PROPIEDAD_URL = "mediconecta.obrasocial.url";
    static final String PROPIEDAD_TIMEOUT_CONEXION = "mediconecta.obrasocial.timeoutConexionMs";
    static final String PROPIEDAD_TIMEOUT_RESPUESTA = "mediconecta.obrasocial.timeoutRespuestaMs";

    private static final String URL_POR_DEFECTO = "http://localhost:8080/mediconecta/legado/obrasocial";
    private static final int TIMEOUT_CONEXION_POR_DEFECTO_MS = 2000;
    private static final int TIMEOUT_RESPUESTA_POR_DEFECTO_MS = 5000;

    private static final QName SERVICIO =
            new QName(ObraSocialLegadoPort.NAMESPACE, "ObraSocialLegadoService");
    private static final QName PUERTO =
            new QName(ObraSocialLegadoPort.NAMESPACE, "ObraSocialLegadoPort");

    // Nombres estándar de JAX-WS que CXF respeta. Se ponen ambas variantes
    // (javax y jakarta) porque según la versión CXF lee una u otra.
    private static final String[] CLAVES_TIMEOUT_CONEXION = {
            "jakarta.xml.ws.client.connectionTimeout", "javax.xml.ws.client.connectionTimeout"};
    private static final String[] CLAVES_TIMEOUT_RESPUESTA = {
            "jakarta.xml.ws.client.receiveTimeout", "javax.xml.ws.client.receiveTimeout"};

    private volatile Service servicio;

    @Override
    public RespuestaDelLegado consultarCobertura(String dni, String numeroAfiliado, String codigoPrestacion) {
        return invocar("validarCobertura",
                puerto -> puerto.validarCobertura(dni, numeroAfiliado, codigoPrestacion));
    }

    @Override
    public RespuestaDelLegado solicitarAutorizacion(String dni, String numeroAfiliado, String codigoPrestacion) {
        return invocar("autorizarPrestacion",
                puerto -> puerto.autorizarPrestacion(dni, numeroAfiliado, codigoPrestacion));
    }

    private RespuestaDelLegado invocar(String operacion,
            Function<ObraSocialLegadoPort, RespuestaCoberturaXml> llamada) {
        String url = System.getProperty(PROPIEDAD_URL, URL_POR_DEFECTO);
        try {
            RespuestaCoberturaXml respuesta = llamada.apply(nuevoPuerto(url));
            return traducir(respuesta);
        } catch (SOAPFaultException e) {
            // El legado respondió: el pedido le resulta inválido.
            throw new PedidoRechazadoPorLegadoException(e.getFault().getFaultString());
        } catch (WebServiceException e) {
            // No hubo respuesta utilizable: conexión rechazada, timeout, etc.
            // La causa técnica queda en el log; hacia arriba sube un mensaje limpio.
            LOGGER.log(Level.WARNING, "El legado de la obra social no respondió a " + operacion
                    + " en " + url + ": " + e.getMessage(), e);
            throw new LegadoNoDisponibleException(
                    "El sistema de la obra social no respondió a tiempo.", e);
        }
    }

    /**
     * Un puerto por llamada: el request context de un proxy JAX-WS no es seguro
     * para usar desde varios hilos, y este bean es compartido.
     */
    private ObraSocialLegadoPort nuevoPuerto(String url) {
        ObraSocialLegadoPort puerto = servicio().getPort(PUERTO, ObraSocialLegadoPort.class);
        Map<String, Object> contexto = ((BindingProvider) puerto).getRequestContext();
        contexto.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        int conexion = entero(PROPIEDAD_TIMEOUT_CONEXION, TIMEOUT_CONEXION_POR_DEFECTO_MS);
        int respuesta = entero(PROPIEDAD_TIMEOUT_RESPUESTA, TIMEOUT_RESPUESTA_POR_DEFECTO_MS);
        for (String clave : CLAVES_TIMEOUT_CONEXION) {
            contexto.put(clave, conexion);
        }
        for (String clave : CLAVES_TIMEOUT_RESPUESTA) {
            contexto.put(clave, respuesta);
        }
        return puerto;
    }

    private Service servicio() {
        Service actual = servicio;
        if (actual == null) {
            synchronized (this) {
                actual = servicio;
                if (actual == null) {
                    actual = Service.create(SERVICIO);
                    actual.addPort(PUERTO, SOAPBinding.SOAP11HTTP_BINDING, URL_POR_DEFECTO);
                    servicio = actual;
                }
            }
        }
        return actual;
    }

    private static RespuestaDelLegado traducir(RespuestaCoberturaXml xml) {
        if (xml == null) {
            throw new LegadoNoDisponibleException("El sistema de la obra social respondió vacío.", null);
        }
        return new RespuestaDelLegado(xml.autorizado, xml.plan, xml.porcentajeCobertura,
                xml.copago, xml.numeroAutorizacion, xml.mensaje);
    }

    private static int entero(String propiedad, int porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Valor inválido para " + propiedad + ": '" + valor + "'. Se usa " + porDefecto + ".");
            return porDefecto;
        }
    }
}
