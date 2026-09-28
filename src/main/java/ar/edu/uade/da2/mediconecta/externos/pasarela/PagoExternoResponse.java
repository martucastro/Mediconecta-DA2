package ar.edu.uade.da2.mediconecta.externos.pasarela;

/**
 * Respuesta de la pasarela externa. status vale "approved", "rejected" o
 * "refunded"; transactionId viene sólo cuando el pago se aprobó.
 */
public class PagoExternoResponse {

    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String REFUNDED = "refunded";

    private String status;
    private String transactionId;

    public PagoExternoResponse() {
    }

    public PagoExternoResponse(String status, String transactionId) {
        this.status = status;
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
