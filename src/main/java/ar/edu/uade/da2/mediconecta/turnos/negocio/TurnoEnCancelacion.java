package ar.edu.uade.da2.mediconecta.turnos.negocio;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;

/**
 * Punto de extension de ServicioDeTurnos.cancelarTurno para un turno
 * CONFIRMADO (SCRUM-124): deshacer lo que dejo la confirmacion.
 *
 * Evento CDI sincronico, igual que TurnoEnConfirmacion: se dispara dentro de la
 * transaccion de la cancelacion, despues de validar que el turno es del caller
 * y ANTES de soltar al paciente y marcarlo CANCELADO. Si un observador lanza una
 * excepcion, la cancelacion entera se revierte y el turno sigue CONFIRMADO.
 *
 * Observadores, en el orden de PuntosDeExtension:
 * 1. ANULACION_RECLAMO: ServicioDeFacturacion, via ReclamoAlCancelar.
 * 2. REEMBOLSO_COPAGO: ServicioDePagos, via ReembolsoAlCancelar.
 *
 * Liberar un hold (EN_HOLD) no lo dispara: todavia no se cobro ni se reclamo nada.
 */
public class TurnoEnCancelacion {

    private final Turno turno;

    public TurnoEnCancelacion(Turno turno) {
        this.turno = turno;
    }

    public Turno getTurno() {
        return turno;
    }
}
