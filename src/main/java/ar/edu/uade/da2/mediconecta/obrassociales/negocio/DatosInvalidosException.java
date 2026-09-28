package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El pedido no se puede evaluar con los datos que tiene: paciente inexistente,
 * sin afiliación registrada, o datos que la obra social no reconoce.
 *
 * @ApplicationException para que el contenedor la propague tal cual en vez de
 * envolverla en EJBException; rollback = true deshace la operación en curso.
 */
@ApplicationException(rollback = true)
public class DatosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
