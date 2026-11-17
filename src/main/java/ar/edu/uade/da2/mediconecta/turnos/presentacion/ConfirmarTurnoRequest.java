package ar.edu.uade.da2.mediconecta.turnos.presentacion;

/**
 * Cuerpo opcional de PUT /api/turnos/{id}/confirmar.
 *
 * Solo lleva el token del medio de pago, que viaja para cobrar el copago del
 * turno al confirmar (SCRUM-93). Es un token que devuelve el frontend de la
 * pasarela, nunca el numero de tarjeta: MediConecta no toca datos sensibles.
 *
 * El cuerpo es opcional: un turno sin copago (cobertura total o aun sin
 * cotizar) se confirma sin enviar nada, igual que antes.
 */
public class ConfirmarTurnoRequest {

    private String tokenMedioDePago;

    public ConfirmarTurnoRequest() {
    }

    public String getTokenMedioDePago() {
        return tokenMedioDePago;
    }

    public void setTokenMedioDePago(String tokenMedioDePago) {
        this.tokenMedioDePago = tokenMedioDePago;
    }
}
