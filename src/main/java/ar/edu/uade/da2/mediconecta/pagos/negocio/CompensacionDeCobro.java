package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Compensa el cobro huerfano: la pasarela aprobo el copago, pero la transaccion
 * de la confirmacion no llego a confirmarse (fallo la sala, el envio al topico
 * TurnoConfirmado o el propio commit). El rollback de JTA alcanza a PostgreSQL y
 * al JMSContext, no a una llamada HTTP ya hecha: sin esto el dinero quedaba
 * cobrado y un reintento del paciente cobraba otra vez.
 *
 * Observa CobroAprobado con TransactionPhase.AFTER_FAILURE: el contenedor lo
 * invoca recien cuando la transaccion termino en rollback, y nunca si se
 * confirmo. Corre fuera de esa transaccion, que ya no existe; por eso no es
 * transaccional y el resultado se guarda con PagoDAO.registrarCompensacion, en
 * una transaccion propia (REQUIRES_NEW).
 *
 * Resultado durable en la fila del pago, que el rastro PENDIENTE de
 * ServicioDePagos ya habia dejado:
 *   - reembolso confirmado: REEMBOLSADO, con el id de transaccion. Un reintento
 *     de la confirmacion hace un cobro nuevo y el paciente termina pagando una
 *     sola vez.
 *   - reembolso fallido o no confirmado: COMPENSACION_PENDIENTE, con el id de
 *     transaccion y un WARNING en el log. El administrador lo devuelve con
 *     PUT /api/pagos/{id}/reembolso.
 * Si tampoco se puede escribir en la base, queda un SEVERE con todos los datos
 * para conciliar a mano: no hay nadie arriba que pueda atrapar la excepcion.
 *
 * No usa ServicioDePagos ni su seguridad: puede correr en el hilo que hace el
 * rollback, sin el usuario autenticado de la confirmacion.
 */
@ApplicationScoped
public class CompensacionDeCobro {

    private static final Logger LOGGER = Logger.getLogger(CompensacionDeCobro.class.getName());

    @Inject
    private PasarelaDePagoAdapter pasarela;

    @Inject
    private PagoDAO pagoDAO;

    public void alFallarLaTransaccion(
            @Observes(during = TransactionPhase.AFTER_FAILURE) CobroAprobado cobro) {
        EstadoPago resultado = reembolsar(cobro);
        try {
            pagoDAO.registrarCompensacion(cobro.pagoId(), resultado, cobro.idTransaccionExterna());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Compensacion del cobro huerfano: no se pudo guardar el resultado "
                    + resultado + " del pago " + cobro.pagoId() + " (turno " + cobro.turnoId()
                    + ", transaccion " + cobro.idTransaccionExterna() + "). Conciliar a mano.", e);
        }
    }

    private EstadoPago reembolsar(CobroAprobado cobro) {
        try {
            ResultadoPasarela resultado = pasarela.reembolsar(cobro.idTransaccionExterna());
            if (resultado.getEstado() == EstadoPago.REEMBOLSADO) {
                LOGGER.info(() -> "Compensacion del cobro huerfano: se reembolso el pago "
                        + cobro.pagoId() + " del turno " + cobro.turnoId() + " (transaccion "
                        + cobro.idTransaccionExterna() + ").");
                return EstadoPago.REEMBOLSADO;
            }
            LOGGER.warning(() -> "Compensacion del cobro huerfano: la pasarela no confirmo el reembolso"
                    + " del pago " + cobro.pagoId() + " del turno " + cobro.turnoId() + " (estado "
                    + resultado.getEstado() + ", transaccion " + cobro.idTransaccionExterna()
                    + "). Queda COMPENSACION_PENDIENTE para el administrador.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Compensacion del cobro huerfano: fallo el reembolso del pago "
                    + cobro.pagoId() + " del turno " + cobro.turnoId() + " (transaccion "
                    + cobro.idTransaccionExterna() + "). Queda COMPENSACION_PENDIENTE para el administrador.", e);
        }
        return EstadoPago.COMPENSACION_PENDIENTE;
    }
}
