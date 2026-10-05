package ar.edu.uade.da2.mediconecta.obrassociales.negocio.soap;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Espejo del tipo respuestaReclamo del WSDL. Sólo existe para que JAXB
 * deserialice la respuesta de presentarReclamo; no sale de este paquete.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "respuestaReclamo", propOrder = {"numeroPresentacion", "montoReconocido"})
public class RespuestaReclamoXml {

    public String numeroPresentacion;
    public BigDecimal montoReconocido;
}
