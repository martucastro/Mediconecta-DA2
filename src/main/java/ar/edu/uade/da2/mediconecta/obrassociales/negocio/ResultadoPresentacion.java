package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.math.BigDecimal;

/**
 * Resultado de presentar ante la obra social una prestación ya autorizada,
 * en los términos de MediConecta. Igual que Cobertura, es lo único que sale
 * del componente: ningún tipo del contrato SOAP llega a quien lo invoca.
 */
public record ResultadoPresentacion(String numeroPresentacion, BigDecimal monto) {
}
