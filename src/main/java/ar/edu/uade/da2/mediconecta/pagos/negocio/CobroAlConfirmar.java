package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnConfirmacion;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Cobra el copago cuando se confirma un turno (SCRUM-93).
 *
 * Observa TurnoEnConfirmacion con prioridad COBRO_COPAGO, el primer paso de la
 * confirmacion, antes de la sala (SALA_DE_VIDEO). Asi ServicioDeTurnos no
 * conoce a este componente. Mismo estilo que SalaAlConfirmar.
 *
 * Corre DENTRO de la transaccion de confirmarTurno (@Transactional MANDATORY) y
 * en la llamada del propio paciente que confirma, que es quien reservo el
 * turno: ServicioDePagos valida que el turno sea suyo con esa identidad.
 *
 * Que se cobra: turno.getCopago(), el monto que paga el paciente segun la
 * cobertura (SCRUM-91). Un turno sin copago (cobertura total, copago 0, o aun
 * sin cotizar, copago null) se confirma sin cobrar y sin pedir medio de pago.
 *
 * Que pasa si la pasarela falla a mitad del flujo (ver docs/arquitectura.md):
 *   - Pasarela caida -> ServicioDePagos lanza PasarelaNoDisponibleException
 *     (rollback=true): sale por el fire(), la confirmacion entera se revierte,
 *     el turno sigue EN_HOLD y el paciente recibe 502. Nada se cobro.
 *   - Pasarela rechaza el cobro -> ServicioDePagos NO lanza, devuelve el Pago
 *     en RECHAZADO; aca se lo traduce a ConflictoDeNegocioException (409) para
 *     no confirmar un turno impago, y la transaccion se revierte.
 *   - Cobro aprobado pero un paso posterior (la sala) falla -> JTA revierte la
 *     fila Pago, pero el cobro externo ya salio (cobro huerfano). El rastro
 *     PENDIENTE que ServicioDePagos inserta en REQUIRES_NEW sobrevive al
 *     rollback para poder reconciliar/compensar con ServicioDePagos.reembolsar.
 */
@ApplicationScoped
public class CobroAlConfirmar {

    // El copago se cotiza en pesos (SCRUM-91). La pasarela pide la moneda ISO.
    static final String MONEDA_COPAGO = "ARS";

    @Inject
    private ServicioDePagosLocal servicioDePagos;

    @Transactional(TxType.MANDATORY)
    public void alConfirmar(
            @Observes @Priority(PuntosDeExtension.COBRO_COPAGO) TurnoEnConfirmacion evento) {
        Turno turno = evento.getTurno();
        BigDecimal copago = turno.getCopago();
        if (copago == null || copago.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String token = evento.getTokenMedioDePago();
        if (token == null || token.isBlank()) {
            throw new DatosInvalidosException("El turno " + turno.getId() + " tiene un copago de "
                    + copago + " y la confirmacion no incluyo un medio de pago.");
        }

        CobroDTO cobro = new CobroDTO();
        cobro.setTurnoId(turno.getId());
        cobro.setMonto(copago);
        cobro.setMoneda(MONEDA_COPAGO);
        cobro.setTokenMedioDePago(token);

        Pago pago = servicioDePagos.cobrar(cobro);
        if (pago.getEstado() != EstadoPago.APROBADO) {
            throw new ConflictoDeNegocioException("El copago del turno " + turno.getId()
                    + " fue rechazado por la pasarela (estado " + pago.getEstado() + ").");
        }
    }
}
