package ar.edu.uade.da2.mediconecta;

import java.lang.reflect.Type;

import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * JSON-B estricto con los tipos de los cuerpos de pedido (SCRUM-132).
 *
 * Yasson convierte en silencio: {"turnoId":1.5} llegaba como 1, un numero que
 * no entra en un Long se desbordaba, y {"tokenMedioDePago":123} se aceptaba
 * como "123". Yasson no tiene una opcion de configuracion para impedirlo, asi
 * que se registran deserializadores propios para Long, Integer y String que
 * exigen el tipo JSON correcto (null sigue valiendo null). Un tipo incorrecto
 * lanza JsonbException, que ErrorInesperadoMapper ya traduce a 400.
 *
 * JAX-RS usa este Jsonb para todo application/json gracias al
 * ContextResolver. Solo cambia la lectura: la escritura de respuestas queda
 * igual que con el Jsonb por defecto.
 */
@Provider
public class JsonEstricto implements ContextResolver<Jsonb> {

    private final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
            .withDeserializers(new EnteroLargo(), new Entero(), new Texto()));

    @Override
    public Jsonb getContext(Class<?> tipo) {
        return jsonb;
    }

    static final class EnteroLargo implements JsonbDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto, Type tipo) {
            try {
                JsonNumber numero = entero(parser);
                return numero == null ? null : numero.longValueExact();
            } catch (ArithmeticException fueraDeRango) {
                throw new JsonbException("El número no entra en un entero largo.", fueraDeRango);
            }
        }
    }

    static final class Entero implements JsonbDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext contexto, Type tipo) {
            try {
                JsonNumber numero = entero(parser);
                return numero == null ? null : numero.intValueExact();
            } catch (ArithmeticException fueraDeRango) {
                throw new JsonbException("El número no entra en un entero.", fueraDeRango);
            }
        }
    }

    static final class Texto implements JsonbDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext contexto, Type tipo) {
            JsonValue valor = parser.getValue();
            if (valor.getValueType() == JsonValue.ValueType.NULL) {
                return null;
            }
            if (!(valor instanceof JsonString texto)) {
                throw new JsonbException("Se esperaba un texto y llegó " + valor.getValueType() + ".");
            }
            return texto.getString();
        }
    }

    /** El numero entero del valor actual, o null si el JSON trae null. */
    private static JsonNumber entero(JsonParser parser) {
        JsonValue valor = parser.getValue();
        if (valor.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        if (!(valor instanceof JsonNumber numero) || !numero.isIntegral()) {
            throw new JsonbException("Se esperaba un número entero y llegó " + valor + ".");
        }
        return numero;
    }
}
