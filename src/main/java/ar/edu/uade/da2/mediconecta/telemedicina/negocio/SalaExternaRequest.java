package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

/**
 * Cuerpo del pedido que espera el proveedor de video. Es el contrato del
 * sistema ajeno (nombres en ingles a proposito): el Adapter traduce nuestro
 * dominio a esta forma.
 *
 * Vive en telemedicina.negocio y no se importa de externos.video por el mismo
 * motivo que PagoExternoRequest en pagos: esa clase modela el contrato del lado
 * del tercero, un sistema aparte que se alcanza por HTTP.
 */
public class SalaExternaRequest {

    private String reference;
    private String scheduledAt;

    public SalaExternaRequest() {
    }

    public SalaExternaRequest(String reference, String scheduledAt) {
        this.reference = reference;
        this.scheduledAt = scheduledAt;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(String scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}
