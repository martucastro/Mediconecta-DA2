package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Doble de prueba de jakarta.enterprise.event.Event que se comporta como un
 * evento CDI sincronico: fire() invoca a los observadores registrados, en el
 * orden en que se registraron, en el mismo hilo, y deja salir sus excepciones.
 * Es lo que hace el contenedor con @Observes, sin necesidad de levantarlo.
 */
class EventoSincronico<T> implements Event<T> {

    private final List<Consumer<T>> observadores = new ArrayList<>();
    private final List<T> disparados = new ArrayList<>();

    EventoSincronico<T> observadoPor(Consumer<T> observador) {
        observadores.add(observador);
        return this;
    }

    List<T> disparados() {
        return disparados;
    }

    @Override
    public void fire(T evento) {
        disparados.add(evento);
        for (Consumer<T> observador : observadores) {
            observador.accept(evento);
        }
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U evento) {
        throw new UnsupportedOperationException("Los puntos de extension son sincronicos");
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U evento, NotificationOptions opciones) {
        throw new UnsupportedOperationException("Los puntos de extension son sincronicos");
    }

    @Override
    public Event<T> select(Annotation... calificadores) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtipo, Annotation... calificadores) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtipo, Annotation... calificadores) {
        throw new UnsupportedOperationException();
    }
}
