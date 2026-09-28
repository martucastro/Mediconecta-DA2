package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;

/**
 * Datos de entrada para iniciar un cobro. El medio de pago viaja como token
 * (lo que devuelve el frontend de la pasarela), nunca como número de tarjeta:
 * este componente no toca datos sensibles de la tarjeta.
 */
public class CobroDTO {

    private Long turnoId;
    private BigDecimal monto;
    private String moneda;
    private String tokenMedioDePago;

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getTokenMedioDePago() {
        return tokenMedioDePago;
    }

    public void setTokenMedioDePago(String tokenMedioDePago) {
        this.tokenMedioDePago = tokenMedioDePago;
    }
}
