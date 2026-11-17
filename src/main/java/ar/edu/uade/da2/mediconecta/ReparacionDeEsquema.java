package ar.edu.uade.da2.mediconecta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.facturacion.datos.EstadoReclamo;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.EstadoSesionVideo;
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
 * Quita, al desplegar, los CHECK de las columnas estado que quedaron
 * desactualizados.
 *
 * Hibernate crea ese CHECK con los valores del enum cuando crea la tabla, y
 * hbm2ddl.auto=update nunca lo vuelve a tocar. En una base creada antes de que
 * el enum sumara un valor, la primera fila con ese valor falla al commit. Paso
 * con ANULADO en reclamos: cancelar un turno confirmado reembolsaba en la
 * pasarela y despues fallaba por la restriccion. Pasaria igual con
 * COMPENSACION_PENDIENTE en pagos y CANCELADA en sesiones_video. Se borra solo
 * el CHECK sobre estado al que le falta algun valor del enum: en una base nueva
 * no hay ninguno y no hace nada.
 *
 * Vive en el paquete raiz y no en un componente porque es infraestructura de
 * despliegue que atraviesa a varios, igual que los ExceptionMapper.
 *
 * Transaccion manejada por el bean y no por el contenedor: si el ALTER falla,
 * con CMT la transaccion de @PostConstruct queda marcada para rollback y el
 * despliegue entero falla. Aca se deshace, se registra un WARNING y la
 * aplicacion arranca igual.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class ReparacionDeEsquema {

    private static final Logger LOGGER = Logger.getLogger(ReparacionDeEsquema.class.getName());

    // Tabla y enum de su columna estado.
    private static final Map<String, Class<? extends Enum<?>>> ESTADOS = Map.of(
            "reclamos", EstadoReclamo.class,
            "pagos", EstadoPago.class,
            "sesiones_video", EstadoSesionVideo.class);

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Resource
    private UserTransaction transaccion;

    @PostConstruct
    public void quitarRestriccionesDesactualizadas() {
        try {
            transaccion.begin();
            List<String> quitadas = new ArrayList<>();
            for (Map.Entry<String, Class<? extends Enum<?>>> estado : ESTADOS.entrySet()) {
                String tabla = estado.getKey();
                for (Object fila : em.createNativeQuery(checksDe(tabla)).getResultList()) {
                    Object[] columnas = (Object[]) fila;
                    String nombre = (String) columnas[0];
                    if (desactualizada((String) columnas[1], estado.getValue())) {
                        em.createNativeQuery("ALTER TABLE " + tabla + " DROP CONSTRAINT IF EXISTS \""
                                + nombre.replace("\"", "\"\"") + "\"").executeUpdate();
                        quitadas.add(tabla + "." + nombre);
                    }
                }
            }
            transaccion.commit();
            LOGGER.info(quitadas.isEmpty()
                    ? "Esquema: sin restricciones de estado desactualizadas."
                    : "Esquema: se quitaron las restricciones desactualizadas " + quitadas + ".");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Esquema: no se pudieron revisar los CHECK de estado; "
                    + "la aplicacion sigue arrancando.", e);
            try {
                transaccion.rollback();
            } catch (Exception yaTerminada) {
                // Fallo el begin o el commit: no queda transaccion que deshacer.
            }
        }
    }

    // to_regclass devuelve null si la tabla todavia no existe: cero filas. La
    // tabla sale de ESTADOS, nunca de un dato externo.
    private static String checksDe(String tabla) {
        return "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conrelid = to_regclass('" + tabla + "') AND contype = 'c'";
    }

    private static boolean desactualizada(String definicion, Class<? extends Enum<?>> estados) {
        return definicion.contains("estado") && !Arrays.stream(estados.getEnumConstants())
                .allMatch(valor -> definicion.contains("'" + valor.name() + "'"));
    }
}
