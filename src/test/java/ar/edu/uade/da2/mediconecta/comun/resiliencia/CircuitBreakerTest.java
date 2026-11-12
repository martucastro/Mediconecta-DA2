package ar.edu.uade.da2.mediconecta.comun.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.resiliencia.CircuitBreaker.Estado;

class CircuitBreakerTest {

    static class Caido extends RuntimeException { }
    static class Rechazado extends RuntimeException { }

    private final AtomicLong reloj = new AtomicLong();
    private final AtomicInteger llamadasReales = new AtomicInteger();
    private CircuitBreaker cb;

    @BeforeEach
    void preparar() {
        cb = new CircuitBreaker("test", 3, 1000, reloj::get);
    }

    private String llamar(boolean falla) {
        return cb.ejecutar(() -> {
            llamadasReales.incrementAndGet();
            if (falla) {
                throw new Caido();
            }
            return "ok";
        }, Caido.class, Rechazado::new);
    }

    private void abrir() {
        for (int i = 0; i < 3; i++) {
            assertThrows(Caido.class, () -> llamar(true));
        }
    }

    @Test
    void seAbreAlLlegarAlUmbralYFallaRapidoSinLlamar() {
        abrir();
        assertEquals(Estado.ABIERTO, cb.estado());
        int antes = llamadasReales.get();

        assertThrows(Rechazado.class, () -> llamar(false));
        assertEquals(antes, llamadasReales.get());
    }

    @Test
    void unExitoReiniciaLaCuentaDeFallas() {
        assertThrows(Caido.class, () -> llamar(true));
        assertThrows(Caido.class, () -> llamar(true));
        assertEquals("ok", llamar(false));
        assertThrows(Caido.class, () -> llamar(true));
        assertThrows(Caido.class, () -> llamar(true));
        assertEquals(Estado.CERRADO, cb.estado());
    }

    @Test
    void unaExcepcionQueNoEsFallaNoAbreElCircuito() {
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> cb.ejecutar(() -> {
                throw new IllegalArgumentException();
            }, Caido.class, Rechazado::new));
        }
        assertEquals(Estado.CERRADO, cb.estado());
    }

    @Test
    void vencidoElTiempoUnaLlamadaDePruebaCierraElCircuito() {
        abrir();
        reloj.set(1000);
        assertEquals("ok", llamar(false));
        assertEquals(Estado.CERRADO, cb.estado());
    }

    @Test
    void siLaPruebaFallaVuelveAAbrirse() {
        abrir();
        reloj.set(1000);
        assertThrows(Caido.class, () -> llamar(true));
        assertEquals(Estado.ABIERTO, cb.estado());
        assertThrows(Rechazado.class, () -> llamar(false));
    }

    @Test
    void mientrasSePruebaLasOtrasLlamadasSeRechazan() {
        abrir();
        reloj.set(1000);
        cb.ejecutar(() -> {
            assertThrows(Rechazado.class, () -> llamar(false));
            return "ok";
        }, Caido.class, Rechazado::new);
        assertEquals(Estado.CERRADO, cb.estado());
    }
}
