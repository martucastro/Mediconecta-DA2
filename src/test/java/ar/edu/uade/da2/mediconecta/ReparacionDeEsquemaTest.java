package ar.edu.uade.da2.mediconecta;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.transaction.UserTransaction;

/**
 * Una base creada antes de que un enum de estado sumara un valor conserva el
 * CHECK viejo (hbm2ddl.auto=update no lo actualiza) y la primera fila con el
 * valor nuevo falla al commit. Paso con ANULADO en reclamos; ahora tambien
 * aplica a COMPENSACION_PENDIENTE en pagos y CANCELADA en sesiones_video. El
 * bean lo quita al desplegar.
 */
@ExtendWith(MockitoExtension.class)
class ReparacionDeEsquemaTest {

    private static final String RECLAMOS_VIEJO = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'ENVIADO'::character varying, "
            + "'EN_REVISION_MANUAL'::character varying])::text[])))";
    private static final String RECLAMOS_ACTUAL = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'ENVIADO'::character varying, "
            + "'EN_REVISION_MANUAL'::character varying, 'ANULADO'::character varying])::text[])))";
    private static final String PAGOS_VIEJO = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'APROBADO'::character varying, "
            + "'RECHAZADO'::character varying, 'REEMBOLSADO'::character varying])::text[])))";
    private static final String PAGOS_ACTUAL = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'APROBADO'::character varying, "
            + "'RECHAZADO'::character varying, 'REEMBOLSADO'::character varying, "
            + "'COMPENSACION_PENDIENTE'::character varying])::text[])))";

    @Mock
    private EntityManager em;
    @Mock
    private UserTransaction transaccion;

    @InjectMocks
    private ReparacionDeEsquema reparacion;

    @Test
    void quitaElCheckDeReclamosQueNoIncluyeAnulado() throws Exception {
        conRestricciones("reclamos", new Object[] {"reclamos_estado_check", RECLAMOS_VIEJO});
        conRestricciones("pagos", new Object[] {"pagos_estado_check", PAGOS_ACTUAL});
        conRestricciones("sesiones_video");
        Query alter = mock(Query.class);
        when(em.createNativeQuery(
                "ALTER TABLE reclamos DROP CONSTRAINT IF EXISTS \"reclamos_estado_check\""))
                .thenReturn(alter);

        reparacion.quitarRestriccionesDesactualizadas();

        verify(alter).executeUpdate();
        verify(em, never()).createNativeQuery(startsWith("ALTER TABLE pagos"));
        verify(transaccion).commit();
    }

    @Test
    void quitaElCheckDePagosQueNoIncluyeCompensacionPendiente() throws Exception {
        conRestricciones("reclamos", new Object[] {"reclamos_estado_check", RECLAMOS_ACTUAL});
        conRestricciones("pagos", new Object[] {"pagos_estado_check", PAGOS_VIEJO});
        conRestricciones("sesiones_video");
        Query alter = mock(Query.class);
        when(em.createNativeQuery("ALTER TABLE pagos DROP CONSTRAINT IF EXISTS \"pagos_estado_check\""))
                .thenReturn(alter);

        reparacion.quitarRestriccionesDesactualizadas();

        verify(alter).executeUpdate();
        verify(transaccion).commit();
    }

    @Test
    void enUnaBaseNuevaNoTocaNada() throws Exception {
        conRestricciones("reclamos", new Object[] {"reclamos_estado_check", RECLAMOS_ACTUAL});
        conRestricciones("pagos", new Object[] {"pagos_estado_check", PAGOS_ACTUAL});
        conRestricciones("sesiones_video");

        reparacion.quitarRestriccionesDesactualizadas();

        verify(em, never()).createNativeQuery(startsWith("ALTER"));
        verify(transaccion).commit();
    }

    @Test
    void unaFallaNoFrenaElDespliegueYDeshaceLaTransaccion() throws Exception {
        when(em.createNativeQuery(startsWith("SELECT")))
                .thenThrow(new PersistenceException("permiso denegado"));

        reparacion.quitarRestriccionesDesactualizadas();

        verify(transaccion, never()).commit();
        verify(transaccion).rollback();
    }

    private void conRestricciones(String tabla, Object[]... filas) {
        Query consulta = mock(Query.class);
        when(consulta.getResultList()).thenReturn(List.of(filas));
        when(em.createNativeQuery(contains("to_regclass('" + tabla + "')"))).thenReturn(consulta);
    }
}
