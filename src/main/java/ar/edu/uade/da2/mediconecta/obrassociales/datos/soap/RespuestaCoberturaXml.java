package ar.edu.uade.da2.mediconecta.obrassociales.datos.soap;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Espejo del tipo respuestaCobertura del WSDL. Sólo existe para que JAXB
 * deserialice la respuesta; no sale de este paquete.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "respuestaCobertura", propOrder = {
        "autorizado", "plan", "porcentajeCobertura", "arancel", "copago",
        "numeroAutorizacion", "mensaje"})
public class RespuestaCoberturaXml {

    public boolean autorizado;
    public String plan;
    public int porcentajeCobertura;
    public BigDecimal arancel;
    public BigDecimal copago;
    public String numeroAutorizacion;
    public String mensaje;
}
