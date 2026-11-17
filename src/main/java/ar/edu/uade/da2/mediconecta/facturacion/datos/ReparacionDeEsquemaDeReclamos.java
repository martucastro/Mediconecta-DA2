package ar.edu.uade.da2.mediconecta.facturacion.datos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;

/**
 * Quita, al desplegar, el CHECK de reclamos.estado que quedo desactualizado.
 *
 * Hibernate crea ese CHECK con los valores de EstadoReclamo cuando crea la
 * tabla, y hbm2ddl.auto=update nunca lo vuelve a tocar. En una base creada
 * antes de que existiera ANULADO, cancelar un turno confirmado con el reclamo
 * PENDIENTE reembolsaba en la pasarela y despues fallaba al commit por la
 * restriccion. Se borra solo el CHECK sobre estado al que le falta algun valor
 * del enum: en una base nueva no hay ninguno y no hace nada.
 *
 * Transaccion manejada por el bean y no por el contenedor: si el ALTER falla,
 * con CMT la transaccion de @PostConstruct queda marcada para rollback y el
 * despliegue entero falla. Aca se deshace, se registra un WARNING y la
 * aplicacion arranca igual.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class ReparacionDeEsquemaDeReclamos {

    private static final Logger LOGGER = Logger.getLogger(ReparacionDeEsquemaDeReclamos.class.getName());

    // to_regclass devuelve null si la tabla todavia no existe: cero filas.
    private static final String CHECKS_DE_RECLAMOS =
            "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conrelid = to_regclass('reclamos') AND contype = 'c'";

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Resource
    private UserTransaction transaccion;

    @PostConstruct
    public void quitarRestriccionesDesactualizadas() {
        try {
            transaccion.begin();
            List<String> quitadas = new ArrayList<>();
            for (Object fila : em.createNativeQuery(CHECKS_DE_RECLAMOS).getResultList()) {
                Object[] columnas = (Object[]) fila;
                String nombre = (String) columnas[0];
                if (desactualizada((String) columnas[1])) {
                    em.createNativeQuery("ALTER TABLE reclamos DROP CONSTRAINT IF EXISTS \""
                            + nombre.replace("\"", "\"\"") + "\"").executeUpdate();
                    quitadas.add(nombre);
                }
            }
            transaccion.commit();
            LOGGER.info(quitadas.isEmpty()
                    ? "Esquema de reclamos: sin restricciones desactualizadas."
                    : "Esquema de reclamos: se quito la restriccion desactualizada " + quitadas + ".");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Esquema de reclamos: no se pudo revisar el CHECK de estado; "
                    + "la aplicacion sigue arrancando.", e);
            try {
                transaccion.rollback();
            } catch (Exception yaTerminada) {
                // Fallo el begin o el commit: no queda transaccion que deshacer.
            }
        }
    }

    private static boolean desactualizada(String definicion) {
        return definicion.contains("estado") && !Arrays.stream(EstadoReclamo.values())
                .allMatch(valor -> definicion.contains("'" + valor.name() + "'"));
    }
}
