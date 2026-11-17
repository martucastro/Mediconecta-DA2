package ar.edu.uade.da2.mediconecta.usuarios.datos;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * SCRUM-132: "Ana@Mail.com" y "ana@mail.com" son el mismo usuario. La
 * comparacion se hace en minusculas en la consulta, asi tambien encuentra las
 * filas guardadas antes de normalizar el registro.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioDAOTest {

    @Mock
    private EntityManager em;

    @InjectMocks
    private UsuarioDAO dao;

    @Test
    @SuppressWarnings("unchecked")
    void buscarPorEmailNoDistingueMayusculas() {
        TypedQuery<Usuario> consulta = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Usuario.class))).thenReturn(consulta);
        when(consulta.getResultList()).thenReturn(List.of());

        dao.buscarPorEmail("Ana@Mail.com");

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(jpql.capture(), eq(Usuario.class));
        assertTrue(jpql.getValue().contains("LOWER(u.email) = LOWER(:email)"), jpql.getValue());
    }
}
