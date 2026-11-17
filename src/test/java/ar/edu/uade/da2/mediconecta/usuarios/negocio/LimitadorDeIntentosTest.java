package ar.edu.uade.da2.mediconecta.usuarios.negocio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitadorDeIntentosTest {

    private static final Instant INICIO = Instant.parse("2026-11-17T10:00:00Z");

    private final Clock reloj = mock(Clock.class);
    private final LimitadorDeIntentos limitador = new LimitadorDeIntentos(reloj);

    @BeforeEach
    void preparar() {
        when(reloj.instant()).thenReturn(INICIO);
    }

    @Test
    void bloqueaAlLlegarAlMaximoDeFallos() {
        fallar("ana@test.com", LimitadorDeIntentos.MAXIMO_FALLOS - 1);
        assertFalse(limitador.estaBloqueado("ana@test.com"));

        fallar("ana@test.com", 1);
        assertTrue(limitador.estaBloqueado("ana@test.com"));
        assertFalse(limitador.estaBloqueado("otro@test.com"));
    }

    @Test
    void elEmailNoDistingueMayusculas() {
        fallar("Ana@Test.com", LimitadorDeIntentos.MAXIMO_FALLOS);

        assertTrue(limitador.estaBloqueado("ana@test.com"));
    }

    @Test
    void alVencerLaVentanaSeDesbloqueaYVuelveAContarDeCero() {
        fallar("ana@test.com", LimitadorDeIntentos.MAXIMO_FALLOS);

        when(reloj.instant()).thenReturn(INICIO.plus(LimitadorDeIntentos.VENTANA));
        assertFalse(limitador.estaBloqueado("ana@test.com"));

        fallar("ana@test.com", 1);
        assertFalse(limitador.estaBloqueado("ana@test.com"));
    }

    @Test
    void unExitoReiniciaElContador() {
        fallar("ana@test.com", LimitadorDeIntentos.MAXIMO_FALLOS - 1);
        limitador.registrarExito("ana@test.com");
        fallar("ana@test.com", LimitadorDeIntentos.MAXIMO_FALLOS - 1);

        assertFalse(limitador.estaBloqueado("ana@test.com"));
    }

    @Test
    void elMapaNoCreceSinLimite() {
        fallar("victima@test.com", LimitadorDeIntentos.MAXIMO_FALLOS);
        for (int i = 0; i < LimitadorDeIntentos.MAXIMO_EMAILS; i++) {
            limitador.registrarFallo("inventado" + i + "@test.com");
        }

        // Al llegar al tope se vacio: el contador de la victima se perdio.
        assertFalse(limitador.estaBloqueado("victima@test.com"));
    }

    private void fallar(String email, int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrarFallo(email);
        }
    }
}
