package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Respuesta del sistema legado a presentarReclamo. Es el tipo que queda
 * publicado en el WSDL, así que el orden de los campos es parte del contrato.
 *
 * A diferencia de RespuestaCobertura, no hay nada que evaluar: la prestación
 * ya fue autorizada antes (autorizarPrestacion). Esta operación sólo
 * confirma cuánto reconoce la obra social por esa autorización y con qué
 * número queda presentada.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "respuestaReclamo", propOrder = {"numeroPresentacion", "montoReconocido"})
public class RespuestaReclamo {

    private String numeroPresentacion;
    private BigDecimal montoReconocido;

    public String getNumeroPresentacion() {
        return numeroPresentacion;
    }

    public void setNumeroPresentacion(String numeroPresentacion) {
        this.numeroPresentacion = numeroPresentacion;
    }

    public BigDecimal getMontoReconocido() {
        return montoReconocido;
    }

    public void setMontoReconocido(BigDecimal montoReconocido) {
        this.montoReconocido = montoReconocido;
    }
}
