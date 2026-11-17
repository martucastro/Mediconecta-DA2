package ar.edu.uade.da2.mediconecta.turnos.negocio;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;

/**
 * Punto de extension de ServicioDeTurnos.confirmarTurno.
 *
 * Evento CDI sincronico: se dispara dentro de la transaccion de la
 * confirmacion, despues de validar el hold y ANTES de marcar el turno como
 * CONFIRMADO. Si un observador lanza una excepcion, la confirmacion entera se
 * revierte: el turno sigue EN_HOLD, su temporizador sigue corriendo y el
 * mensaje TurnoConfirmado no sale.
 *
 * Observadores, en el orden de PuntosDeExtension:
 * 1. COBRO_COPAGO: ServicioDePagos (SCRUM-93), via CobroAlConfirmar.
 * 2. SALA_DE_VIDEO: ServicioDeTelemedicina (SCRUM-95), via SalaAlConfirmar.
 * 3. RECLAMO: encolado del reclamo a la obra social. Pendiente.
 *
 * tokenMedioDePago viaja en el cuerpo del PUT /turnos/{id}/confirmar y lo usa
 * CobroAlConfirmar para cobrar el copago. Es null cuando no se envio (por
 * ejemplo, un turno sin copago): el turno nunca lo guarda, solo lo necesita el
 * observador del cobro durante esta confirmacion.
 */
public class TurnoEnConfirmacion {

    private final Turno turno;
    private final String tokenMedioDePago;

    public TurnoEnConfirmacion(Turno turno) {
        this(turno, null);
    }

    public TurnoEnConfirmacion(Turno turno, String tokenMedioDePago) {
        this.turno = turno;
        this.tokenMedioDePago = tokenMedioDePago;
    }

    public Turno getTurno() {
        return turno;
    }

    public String getTokenMedioDePago() {
        return tokenMedioDePago;
    }
}
