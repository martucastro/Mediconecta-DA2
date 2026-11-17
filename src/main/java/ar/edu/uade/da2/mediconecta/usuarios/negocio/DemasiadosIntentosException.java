package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El email supero el limite de intentos fallidos de login (SCRUM-127). Se
 * traduce a HTTP 429.
 *
 * @ApplicationException para que el contenedor la propague tal cual y el
 * mapper la reconozca; sin la anotacion llegaria envuelta en EJBException y
 * saldria como 500. El login no escribe nada, asi que no hace falta rollback.
 */
@ApplicationException
public class DemasiadosIntentosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DemasiadosIntentosException() {
        super("Demasiados intentos fallidos. Probá de nuevo en unos minutos.");
    }
}
