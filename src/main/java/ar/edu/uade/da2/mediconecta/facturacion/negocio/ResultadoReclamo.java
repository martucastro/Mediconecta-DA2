package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.math.BigDecimal;

/**
 * Respuesta del canal ya traducida al dominio de MediConecta, igual que
 * ResultadoPasarela en pagos: la fachada no conoce el formato del proveedor.
 */
public class ResultadoReclamo {

    private final BigDecimal monto;
    private final String numeroPresentacion;

    public ResultadoReclamo(BigDecimal monto, String numeroPresentacion) {
        this.monto = monto;
        this.numeroPresentacion = numeroPresentacion;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getNumeroPresentacion() {
        return numeroPresentacion;
    }
}
