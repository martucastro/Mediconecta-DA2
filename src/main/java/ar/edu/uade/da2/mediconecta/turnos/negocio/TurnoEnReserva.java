package ar.edu.uade.da2.mediconecta.turnos.negocio;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;

/**
 * Punto de extension de ServicioDeTurnos.reservarTurno.
 *
 * Evento CDI sincronico: se dispara dentro de la transaccion de la reserva,
 * despues de validar que el turno esta disponible y antes de retenerlo. Los
 * observadores corren en el mismo hilo y la misma transaccion JTA, asi que una
 * excepcion no controlada en cualquiera de ellos revierte la reserva completa.
 *
 * Lo observa CoberturaEnLaReserva (obrassociales, SCRUM-91): consulta la
 * cobertura del paciente y completa coberturaAutorizada, coberturaPorcentaje,
 * copago y numeroAutorizacion sobre el turno. Si la obra social no responde, su
 * excepcion revierte la reserva entera.
 *
 * Ver PuntosDeExtension para el orden y el criterio de la decision.
 */
public class TurnoEnReserva {

    private final Turno turno;

    public TurnoEnReserva(Turno turno) {
        this.turno = turno;
    }

    /**
     * El turno administrado por el EntityManager: lo que un observador escriba
     * en los campos de cobertura se persiste con la reserva.
     */
    public Turno getTurno() {
        return turno;
    }
}
