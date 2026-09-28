package ar.edu.uade.da2.mediconecta.externos.pasarela;

import java.math.BigDecimal;

/**
 * Cuerpo de la petición que espera la pasarela externa. Es el contrato del
 * sistema ajeno (nombres en inglés a propósito): el Adapter traduce nuestro
 * dominio a esta forma, no al revés.
 */
public class PagoExternoRequest {

    private BigDecimal amount;
    private String currency;
    private String cardToken;

    public PagoExternoRequest() {
    }

    public PagoExternoRequest(BigDecimal amount, String currency, String cardToken) {
        this.amount = amount;
        this.currency = currency;
        this.cardToken = cardToken;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }
}
