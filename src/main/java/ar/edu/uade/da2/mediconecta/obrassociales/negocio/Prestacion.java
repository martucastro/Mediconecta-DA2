package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.math.BigDecimal;

/**
 * Prestaciones que MediConecta le pide cubrir a la obra social. El nombre de
 * cada constante es el código que espera el legado.
 *
 * El arancel es el precio particular de la clínica: lo que paga un paciente sin
 * cobertura. Para un afiliado manda el arancel que informa el legado, aunque el
 * simulador use los mismos valores.
 */
public enum Prestacion {
    CONSULTA("20000.00"),
    TELECONSULTA("15000.00");

    private final BigDecimal arancel;

    Prestacion(String arancel) {
        this.arancel = new BigDecimal(arancel);
    }

    public BigDecimal getArancel() {
        return arancel;
    }
}
