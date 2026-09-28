package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.math.BigDecimal;

/**
 * Resultado de preguntarle a la obra social por una prestación, en los términos
 * de MediConecta. Es lo único que sale del componente: ningún tipo del contrato
 * SOAP llega a quien lo invoca.
 *
 * numeroAutorizacion es nulo en una consulta, y también cuando la prestación no
 * quedó autorizada.
 */
public record Cobertura(
        boolean autorizada,
        int porcentaje,
        BigDecimal copago,
        String numeroAutorizacion,
        String mensaje) {
}
