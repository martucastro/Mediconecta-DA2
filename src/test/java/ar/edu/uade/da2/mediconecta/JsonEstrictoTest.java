package ar.edu.uade.da2.mediconecta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

/**
 * SCRUM-132: los deserializadores estrictos rechazan el tipo JSON incorrecto
 * en vez de convertirlo. Yasson no esta en el classpath de los tests, asi que
 * se le da al deserializador el valor que le pasaria el parser; la prueba
 * contra el Yasson de WildFly 41 esta en la descripcion del PR.
 */
class JsonEstrictoTest {

    @Test
    void unEnteroLargoAceptaSoloNumerosEnteros() {
        JsonEstricto.EnteroLargo deserializador = new JsonEstricto.EnteroLargo();

        assertEquals(5L, deserializador.deserialize(parserCon(numero("5")), null, Long.class));
        assertThrows(JsonbException.class, () -> deserializador.deserialize(parserCon(numero("1.5")), null, Long.class));
        assertThrows(JsonbException.class, () -> deserializador.deserialize(parserCon(texto("5")), null, Long.class));
        assertThrows(JsonbException.class, () -> deserializador.deserialize(parserCon(JsonValue.TRUE), null, Long.class));
    }

    @Test
    void unNumeroQueNoEntraNoSeDesborda() {
        assertThrows(JsonbException.class, () -> new JsonEstricto.EnteroLargo()
                .deserialize(parserCon(numero("99999999999999999999")), null, Long.class));
        assertThrows(JsonbException.class, () -> new JsonEstricto.Entero()
                .deserialize(parserCon(numero("3000000000")), null, Integer.class));
    }

    @Test
    void unTextoNoAceptaUnNumero() {
        JsonEstricto.Texto deserializador = new JsonEstricto.Texto();

        assertEquals("tok", deserializador.deserialize(parserCon(texto("tok")), null, String.class));
        assertThrows(JsonbException.class, () -> deserializador.deserialize(parserCon(numero("123")), null, String.class));
    }

    @Test
    void nullSigueValiendoNull() {
        assertNull(new JsonEstricto.EnteroLargo().deserialize(parserCon(JsonValue.NULL), null, Long.class));
        assertNull(new JsonEstricto.Entero().deserialize(parserCon(JsonValue.NULL), null, Integer.class));
        assertNull(new JsonEstricto.Texto().deserialize(parserCon(JsonValue.NULL), null, String.class));
    }

    private static JsonParser parserCon(JsonValue valor) {
        JsonParser parser = mock(JsonParser.class);
        when(parser.getValue()).thenReturn(valor);
        return parser;
    }

    private static JsonNumber numero(String literal) {
        BigDecimal valor = new BigDecimal(literal);
        JsonNumber numero = mock(JsonNumber.class);
        when(numero.getValueType()).thenReturn(JsonValue.ValueType.NUMBER);
        when(numero.isIntegral()).thenReturn(valor.scale() <= 0);
        when(numero.longValueExact()).thenAnswer(invocacion -> valor.longValueExact());
        when(numero.intValueExact()).thenAnswer(invocacion -> valor.intValueExact());
        return numero;
    }

    private static JsonString texto(String valor) {
        JsonString texto = mock(JsonString.class);
        when(texto.getValueType()).thenReturn(JsonValue.ValueType.STRING);
        when(texto.getString()).thenReturn(valor);
        return texto;
    }
}
