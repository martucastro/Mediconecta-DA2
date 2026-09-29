package ar.edu.uade.da2.mediconecta.comun.negocio;

import jakarta.ejb.ApplicationException;

/**
 * La operacion es valida en sus datos pero choca con el estado actual del
 * sistema (por ejemplo, un email que ya esta registrado o un turno que ya no
 * esta disponible). Se traduce a HTTP 409.
 *
 * @ApplicationException con rollback = true para que el contenedor deshaga la
 * transaccion en vez de tratarla como una falla del sistema. Sin esa
 * anotacion, el contenedor la envolveria en una EJBException y el cliente
 * recibiria un 500 cuando el problema es del pedido, no del servidor.
 *
 * Compartida por los cuatro componentes de negocio (usuarios, turnos,
 * historia clinica y pagos): antes cada uno tenia su propia copia identica.
 */
@ApplicationException(rollback = true)
public class ConflictoDeNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictoDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
