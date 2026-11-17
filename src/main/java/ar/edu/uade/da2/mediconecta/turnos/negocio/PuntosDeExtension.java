package ar.edu.uade.da2.mediconecta.turnos.negocio;

/**
 * Orden de los observadores de TurnoEnReserva, TurnoEnConfirmacion y
 * TurnoEnCancelacion.
 *
 * Los componentes que se enganchan al flujo de turnos lo hacen con un
 * observador sincronico:
 *
 * <pre>
 * public void alConfirmar(
 *         &#64;Observes &#64;Priority(PuntosDeExtension.SALA_DE_VIDEO) TurnoEnConfirmacion evento)
 * </pre>
 *
 * CDI invoca primero a los de menor prioridad. El orden no es cosmetico: el
 * copago se cobra antes de pedir la sala, y el reclamo a la obra social se
 * encola al final, cuando todo lo anterior salio bien. Los valores dejan huecos
 * para poder intercalar un paso sin renumerar.
 *
 * Por que eventos CDI y no llamadas directas: ServicioDeTurnos no importa a
 * ninguno de los componentes que reaccionan a sus pasos, asi que cada card
 * agrega su observador en su propio componente sin tocar confirmarTurno. Ver
 * docs/documento-tecnico.md, seccion 9.2.
 *
 * Cualquier observador que corra aca tiene que ser sincronico (@Observes, no
 * @ObservesAsync): un observador asincronico corre en otro hilo y fuera de la
 * transaccion, asi que su falla ya no podria impedir la confirmacion.
 */
public final class PuntosDeExtension {

    /** TurnoEnReserva: validacion de cobertura con la obra social (SCRUM-91). */
    public static final int COBERTURA = 100;

    /** TurnoEnConfirmacion: cobro del copago (SCRUM-93). Va antes que la sala. */
    public static final int COBRO_COPAGO = 100;

    /** TurnoEnConfirmacion: creacion de la sala de video (SCRUM-95). */
    public static final int SALA_DE_VIDEO = 200;

    /** TurnoEnConfirmacion: encolado del reclamo a la obra social. */
    public static final int RECLAMO = 300;

    /** TurnoEnCancelacion: anulacion del reclamo a la obra social (SCRUM-124). */
    public static final int ANULACION_RECLAMO = 100;

    /** TurnoEnCancelacion: la sala de video queda CANCELADA (SCRUM-132). */
    public static final int CIERRE_SALA = 150;

    /**
     * TurnoEnCancelacion: reembolso del copago (SCRUM-124). Va al final: es lo
     * unico que el rollback no deshace.
     */
    public static final int REEMBOLSO_COPAGO = 200;

    private PuntosDeExtension() {
    }
}
