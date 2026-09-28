package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.util.List;

import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDePagos.
 *
 * Es el único contrato que los consumidores conocen: la implementación, el DAO
 * y el adapter hacia la pasarela quedan ocultos detrás de estas operaciones.
 */
@Local
public interface ServicioDePagosLocal {

    /**
     * Cobra un turno contra la pasarela externa y registra el pago. Falla con
     * DatosInvalidosException si el pedido es inválido y con
     * PasarelaNoDisponibleException si la pasarela no responde. Un rechazo de la
     * pasarela no es una excepción: devuelve el Pago en estado RECHAZADO.
     */
    Pago cobrar(CobroDTO datos);

    /** Devuelve un pago por su id, o null si no existe. */
    Pago consultarPago(Long pagoId);

    /** Pagos asociados a un turno, ordenados por fecha. */
    List<Pago> listarPorTurno(Long turnoId);

    /**
     * Reembolsa un pago aprobado. Falla con ConflictoDeNegocioException si el
     * pago no está en estado APROBADO.
     */
    Pago reembolsar(Long pagoId);
}
