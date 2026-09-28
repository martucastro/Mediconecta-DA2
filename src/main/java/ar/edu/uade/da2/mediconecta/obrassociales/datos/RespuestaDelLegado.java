package ar.edu.uade.da2.mediconecta.obrassociales.datos;

import java.math.BigDecimal;

/**
 * Lo que respondió el legado, ya fuera del sobre SOAP. Es un valor plano, sin
 * anotaciones de XML: la traducción desde las clases del contrato ocurre en
 * SistemaDeObraSocialSoap y no sale de ahí.
 */
public record RespuestaDelLegado(
        boolean autorizado,
        String plan,
        int porcentajeCobertura,
        BigDecimal copago,
        String numeroAutorizacion,
        String mensaje) {
}
