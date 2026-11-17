package ar.edu.uade.da2.mediconecta.turnos.datos;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

/**
 * El lock de la fila tiene que traer el estado confirmado, no la copia que ya
 * estaba en el contexto de persistencia: em.find sobre una entidad ya cargada
 * toma el lock pero no relee la fila.
 */
@ExtendWith(MockitoExtension.class)
class TurnoDAOTest {

    @Mock
    private EntityManager em;

    @InjectMocks
    private TurnoDAO dao;

    @Test
    void buscarParaActualizarReleeElTurnoDespuesDeTomarElLock() {
        Turno turno = new Turno();
        when(em.find(Turno.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(turno);

        assertSame(turno, dao.buscarParaActualizar(1L));

        InOrder orden = inOrder(em);
        orden.verify(em).find(Turno.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).refresh(turno);
    }

    @Test
    void unTurnoInexistenteNoSeRelee() {
        assertNull(dao.buscarParaActualizar(2L));

        verify(em, never()).refresh(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void losTurnosDelPacienteIncluyenLosQueCancelo() {
        // Cancelar un turno confirmado conserva al paciente: lo sigue viendo,
        // CANCELADO, en su lista.
        TypedQuery<Turno> consulta = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Turno.class))).thenReturn(consulta);
        when(consulta.getResultList()).thenReturn(List.of());

        dao.listarPorPaciente(3L);

        verify(consulta).setParameter("estados",
                List.of(EstadoTurno.EN_HOLD, EstadoTurno.CONFIRMADO, EstadoTurno.CANCELADO));
    }
}
