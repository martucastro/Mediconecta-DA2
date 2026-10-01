package ar.edu.uade.da2.mediconecta.obrassociales.negocio.soap;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Cobertura;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.Prestacion;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.SistemaDeObraSocial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPBinding;
import jakarta.xml.ws.soap.SOAPFaultException;

/**
 * Patrón Adapter, lado de afuera: traduce la interfaz SistemaDeObraSocial a
 * llamadas SOAP contra el legado, y las respuestas y errores SOAP de vuelta a
 * Cobertura y a excepciones de negocio. Es la única clase del componente que
 * conoce JAX-WS (junto con el port y el espejo JAXB de este paquete).
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
 *
 * Los faults SOAP se clasifican por su código, no todos son lo mismo: un fault
 * del cliente (Client en SOAP 1.1, Sender en 1.2) dice que el legado entendió el
 * pedido y lo rechaza, y es un dato inválido; cualquier otro (Server, por
 * ejemplo) es una falla del legado y se trata como no disponible.
 */
@ApplicationScoped
public class SistemaDeObraSocialSoap implements SistemaDeObraSocial {

    private static final Logger LOGGER = Logger.getLogger(SistemaDeObraSocialSoap.class.getName());

    static final String PROPIEDAD_URL = "mediconecta.obrasocial.url";
    static final String PROPIEDAD_TIMEOUT_CONEXION = "mediconecta.obrasocial.timeoutConexionMs";
    static final String PROPIEDAD_TIMEOUT_RESPUESTA = "mediconecta.obrasocial.timeoutRespuestaMs";

    static final String MENSAJE_NO_DISPONIBLE =
            "El sistema de la obra social no respondió. Intentá de nuevo en unos minutos.";

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
    public Cobertura consultar(String dni, String numeroAfiliado, Prestacion prestacion) {
        return invocar("validarCobertura",
                puerto -> puerto.validarCobertura(dni, numeroAfiliado, prestacion.name()));
    }

    @Override
    public Cobertura autorizar(String dni, String numeroAfiliado, Prestacion prestacion) {
        return invocar("autorizarPrestacion",
                puerto -> puerto.autorizarPrestacion(dni, numeroAfiliado, prestacion.name()));
    }

    private Cobertura invocar(String operacion,
            Function<ObraSocialLegadoPort, RespuestaCoberturaXml> llamada) {
        String url = System.getProperty(PROPIEDAD_URL, URL_POR_DEFECTO);
        try {
            return traducir(llamada.apply(nuevoPuerto(url)));
        } catch (SOAPFaultException e) {
            if (esFaultDelCliente(e)) {
                // El legado respondió y dice que el pedido no es válido.
                throw new DatosInvalidosException(
                        "La obra social rechazó el pedido: " + e.getFault().getFaultString());
            }
            throw noDisponible(operacion, url, e);
        } catch (WebServiceException e) {
            // No hubo respuesta utilizable: conexión rechazada, timeout, etc.
            throw noDisponible(operacion, url, e);
        }
    }

    /**
     * La causa técnica queda en el log y como causa de la excepción; hacia
     * arriba sube un mensaje limpio, apto para mostrarse tal cual.
     */
    private static ObraSocialNoDisponibleException noDisponible(String operacion, String url,
            WebServiceException causa) {
        LOGGER.log(Level.WARNING, "El legado de la obra social no respondió a " + operacion
                + " en " + url + ": " + causa.getMessage(), causa);
        return new ObraSocialNoDisponibleException(MENSAJE_NO_DISPONIBLE, causa);
    }

    private static boolean esFaultDelCliente(SOAPFaultException e) {
        QName codigo = e.getFault().getFaultCodeAsQName();
        if (codigo == null) {
            return false;
        }
        String local = codigo.getLocalPart();
        return local.startsWith("Client") || local.equals("Sender");
    }

    /**
     * Un puerto por llamada: el request context de un proxy JAX-WS no es seguro
     * para usar desde varios hilos, y este bean es compartido. Es la única parte
     * que necesita un runtime de JAX-WS, y por eso los tests la reemplazan.
     */
    ObraSocialLegadoPort nuevoPuerto(String url) {
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

    /** Del contrato SOAP al dominio: de acá para adentro ya no hay tipos del WSDL. */
    static Cobertura traducir(RespuestaCoberturaXml xml) {
        if (xml == null) {
            throw new ObraSocialNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        }
        return new Cobertura(xml.autorizado, xml.porcentajeCobertura, xml.copago,
                xml.numeroAutorizacion, xml.mensaje);
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
