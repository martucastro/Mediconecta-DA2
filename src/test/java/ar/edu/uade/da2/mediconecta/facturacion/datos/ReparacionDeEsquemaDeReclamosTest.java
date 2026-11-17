package ar.edu.uade.da2.mediconecta.facturacion.datos;

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
 * Una base creada antes de que EstadoReclamo tuviera ANULADO conserva el CHECK
 * viejo (hbm2ddl.auto=update no lo actualiza) y la cancelacion de un turno con
 * reclamo PENDIENTE falla al commit. El bean lo quita al desplegar.
 */
@ExtendWith(MockitoExtension.class)
class ReparacionDeEsquemaDeReclamosTest {

    private static final String CHECK_VIEJO = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'ENVIADO'::character varying, "
            + "'EN_REVISION_MANUAL'::character varying])::text[])))";
    private static final String CHECK_ACTUAL = "CHECK (((estado)::text = ANY ((ARRAY["
            + "'PENDIENTE'::character varying, 'ENVIADO'::character varying, "
            + "'EN_REVISION_MANUAL'::character varying, 'ANULADO'::character varying])::text[])))";

    @Mock
    private EntityManager em;
    @Mock
    private UserTransaction transaccion;

    @InjectMocks
    private ReparacionDeEsquemaDeReclamos reparacion;

    @Test
    void quitaElCheckDeEstadoQueNoIncluyeAnulado() throws Exception {
        conRestricciones(new Object[] {"reclamos_estado_check", CHECK_VIEJO});
        Query alter = mock(Query.class);
        when(em.createNativeQuery(
                "ALTER TABLE reclamos DROP CONSTRAINT IF EXISTS \"reclamos_estado_check\""))
                .thenReturn(alter);

        reparacion.quitarRestriccionesDesactualizadas();

        verify(alter).executeUpdate();
        verify(transaccion).commit();
    }

    @Test
    void enUnaBaseNuevaNoTocaNada() throws Exception {
        conRestricciones(new Object[] {"reclamos_estado_check", CHECK_ACTUAL});

        reparacion.quitarRestriccionesDesactualizadas();

        verify(em, never()).createNativeQuery(startsWith("ALTER"));
        verify(transaccion).commit();
    }

    @Test
    void unaFallaNoFrenaElDespliegueYDeshaceLaTransaccion() throws Exception {
        conRestricciones(new Object[] {"reclamos_estado_check", CHECK_VIEJO});
        when(em.createNativeQuery(startsWith("ALTER")))
                .thenThrow(new PersistenceException("permiso denegado"));

        reparacion.quitarRestriccionesDesactualizadas();

        verify(transaccion, never()).commit();
        verify(transaccion).rollback();
    }

    private void conRestricciones(Object[]... filas) {
        Query consulta = mock(Query.class);
        when(consulta.getResultList()).thenReturn(List.of(filas));
        when(em.createNativeQuery(startsWith("SELECT"))).thenReturn(consulta);
    }
}
