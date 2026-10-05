package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Respuesta del sistema legado a las dos operaciones. Es el tipo que queda
 * publicado en el WSDL, así que el orden de los campos es parte del contrato.
 *
 * numeroAutorizacion sólo viaja cuando se pidió autorizar y la prestación quedó
 * autorizada; en una consulta de cobertura siempre es nulo.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "respuestaCobertura", propOrder = {
        "autorizado", "plan", "porcentajeCobertura", "arancel", "copago",
        "numeroAutorizacion", "mensaje"})
public class RespuestaCobertura {

    private boolean autorizado;
    private String plan;
    private int porcentajeCobertura;
    private BigDecimal arancel;
    private BigDecimal copago;
    private String numeroAutorizacion;
    private String mensaje;

    public boolean isAutorizado() {
        return autorizado;
    }

    public void setAutorizado(boolean autorizado) {
        this.autorizado = autorizado;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public int getPorcentajeCobertura() {
        return porcentajeCobertura;
    }

    public void setPorcentajeCobertura(int porcentajeCobertura) {
        this.porcentajeCobertura = porcentajeCobertura;
    }

    public BigDecimal getArancel() {
        return arancel;
    }

    public void setArancel(BigDecimal arancel) {
        this.arancel = arancel;
    }

    public BigDecimal getCopago() {
        return copago;
    }

    public void setCopago(BigDecimal copago) {
        this.copago = copago;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
