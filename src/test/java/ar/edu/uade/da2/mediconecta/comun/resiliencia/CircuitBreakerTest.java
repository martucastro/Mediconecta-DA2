package ar.edu.uade.da2.mediconecta.comun.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.resiliencia.CircuitBreaker.Estado;

class CircuitBreakerTest {

    static class Caido extends RuntimeException { }
    static class Rechazado extends RuntimeException { }

    private final AtomicLong reloj = new AtomicLong();
    private final AtomicInteger llamadasReales = new AtomicInteger();
    private CircuitBreaker cb;

    // Las transiciones que el breaker deja en el log, como "NIVEL ESTADO".
    private final List<String> transiciones = new ArrayList<>();
    private final Logger logger = Logger.getLogger(CircuitBreaker.class.getName());
    private final Handler capturador = new Handler() {
        @Override
        public void publish(LogRecord registro) {
            String mensaje = registro.getMessage();
            for (Estado estado : Estado.values()) {
                if (mensaje.contains("': " + estado + " ") || mensaje.contains("': " + estado + ",")) {
                    transiciones.add(registro.getLevel() + " " + estado);
                }
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void preparar() {
        cb = new CircuitBreaker("test", "test.cb", 3, 1000, reloj::get);
        logger.addHandler(capturador);
    }

    @AfterEach
    void limpiar() {
        logger.removeHandler(capturador);
        System.clearProperty("test.cb.cb.umbral");
        System.clearProperty("test.cb.cb.abiertoMs");
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

    @Test
    void cadaTransicionQuedaEnElLogConSuNivel() {
        abrir();
        reloj.set(1000);
        llamar(false);

        assertEquals(List.of("WARNING ABIERTO", "INFO SEMIABIERTO", "INFO CERRADO"), transiciones);
    }

    @Test
    void elUmbralYElTiempoAbiertoSeLeenEnCadaLlamada() {
        // Como las URL de los adaptadores: se cambian en caliente con jboss-cli.
        CircuitBreaker configurable = CircuitBreaker.deConfiguracion("test", "test.cb", 3, 60_000);
        System.setProperty("test.cb.cb.umbral", "1");
        System.setProperty("test.cb.cb.abiertoMs", "0");

        assertThrows(Caido.class, () -> configurable.ejecutar(() -> {
            throw new Caido();
        }, Caido.class, Rechazado::new));
        assertEquals(Estado.ABIERTO, configurable.estado());

        assertEquals("ok", configurable.ejecutar(() -> "ok", Caido.class, Rechazado::new));
        assertEquals(Estado.CERRADO, configurable.estado());
    }

    @Test
    void unaConfiguracionInvalidaUsaElValorPorDefecto() {
        System.setProperty("test.cb.cb.umbral", "0");
        CircuitBreaker configurable = CircuitBreaker.deConfiguracion("test", "test.cb", 3, 60_000);

        assertThrows(Caido.class, () -> configurable.ejecutar(() -> {
            throw new Caido();
        }, Caido.class, Rechazado::new));
        assertEquals(Estado.CERRADO, configurable.estado());
    }

    @Test
    void unaFallaQueTerminaConElCircuitoAbiertoNoAlargaLaApertura() {
        // Una llamada que salio con el circuito cerrado y termina despues de
        // que se abrio no reinicia el tiempo de apertura ni lo vuelve a loguear.
        assertThrows(Caido.class, () -> cb.ejecutar(() -> {
            abrir();
            reloj.set(900);
            throw new Caido();
        }, Caido.class, Rechazado::new));

        reloj.set(1000);
        assertEquals("ok", llamar(false));
        assertEquals(List.of("WARNING ABIERTO", "INFO SEMIABIERTO", "INFO CERRADO"), transiciones);
    }

    @Test
    void unErrorEnLaLlamadaDePruebaNoDejaElCircuitoTrabado() {
        abrir();
        reloj.set(1000);
        assertThrows(StackOverflowError.class, () -> cb.ejecutar(() -> {
            throw new StackOverflowError();
        }, Caido.class, Rechazado::new));

        // El Error no prueba que el sistema este vivo: cuenta como falla.
        assertEquals(Estado.ABIERTO, cb.estado());
        reloj.set(2000);
        assertEquals("ok", llamar(false));
    }

    @Test
    void conVariosHilosPasaUnaSolaLlamadaDePrueba() throws Exception {
        abrir();
        reloj.set(1000);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            Future<String> prueba = hilos.submit(() -> cb.ejecutar(() -> {
                dentro.countDown();
                esperar(soltar);
                return "ok";
            }, Caido.class, Rechazado::new));
            assertEquals(true, dentro.await(5, TimeUnit.SECONDS));

            List<Future<String>> otras = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                otras.add(hilos.submit(() -> llamar(false)));
            }
            for (Future<String> otra : otras) {
                ExecutionException e = assertThrows(ExecutionException.class, () -> otra.get(5, TimeUnit.SECONDS));
                assertInstanceOf(Rechazado.class, e.getCause());
            }

            soltar.countDown();
            assertEquals("ok", prueba.get(5, TimeUnit.SECONDS));
            assertEquals(Estado.CERRADO, cb.estado());
        } finally {
            hilos.shutdownNow();
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
