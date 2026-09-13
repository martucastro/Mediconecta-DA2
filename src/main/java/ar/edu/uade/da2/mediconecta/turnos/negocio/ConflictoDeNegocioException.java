package ar.edu.uade.da2.mediconecta.turnos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La operacion es valida pero choca con el estado actual del turno: reservar
 * uno que ya no esta disponible, confirmar uno sin hold activo, cancelar uno
 * que nadie reservo.
 *
 * @ApplicationException con rollback=true para que el contenedor deshaga la
 * transaccion en vez de tratarla como una falla del sistema. Sin esa anotacion,
 * el contenedor la envolveria en una EJBException y el cliente recibiria un 500
 * cuando el problema es del pedido, no del servidor.
 */
@ApplicationException(rollback = true)
public class ConflictoDeNegocioException extends RuntimeException {

    public ConflictoDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
