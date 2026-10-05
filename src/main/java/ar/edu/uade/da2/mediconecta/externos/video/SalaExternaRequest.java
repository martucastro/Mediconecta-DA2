package ar.edu.uade.da2.mediconecta.externos.video;

/**
 * Pedido de creacion de sala que recibe el proveedor de video. Es el contrato
 * del tercero, con sus nombres en ingles.
 *
 * reference es un identificador opaco del cliente (MediConecta manda el id del
 * turno); el proveedor no lo interpreta. scheduledAt es la fecha y hora de la
 * consulta en ISO-8601, solo informativa.
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
