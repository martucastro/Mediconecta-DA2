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
 * 1. COBRO_COPAGO: ServicioDePagos (SCRUM-93). Pendiente.
 * 2. SALA_DE_VIDEO: ServicioDeTelemedicina (SCRUM-95). Pendiente.
 * 3. RECLAMO: encolado del reclamo a la obra social. Pendiente.
 */
public class TurnoEnConfirmacion {

    private final Turno turno;

    public TurnoEnConfirmacion(Turno turno) {
        this.turno = turno;
    }

    public Turno getTurno() {
        return turno;
    }
}
