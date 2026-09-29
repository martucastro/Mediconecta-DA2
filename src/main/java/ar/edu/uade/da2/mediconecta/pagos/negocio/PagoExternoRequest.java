package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;

/**
 * Cuerpo de la petición que espera la pasarela externa. Es el contrato del
 * sistema ajeno (nombres en inglés a propósito): el Adapter traduce nuestro
 * dominio a esta forma, no al revés.
 *
 * Vive en pagos.negocio, no en externos.pasarela: esa clase modela el mismo
 * contrato del lado de la simulación del partner, un sistema aparte que se
 * alcanza por HTTP. Que las dos JVM convivan en el mismo proceso de prueba es
 * un detalle del entorno; el Adapter no tiene que importar el tipo interno de
 * la simulación para hablarle en su idioma.
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
