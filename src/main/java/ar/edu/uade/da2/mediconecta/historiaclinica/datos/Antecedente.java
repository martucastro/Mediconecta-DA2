package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ANTECEDENTE")
public class Antecedente extends EntradaClinica {

    public static final int MAX_TIPO_ANTECEDENTE = 60;
    // Texto libre del profesional: 255 caracteres son dos oraciones.
    public static final int MAX_DETALLE = 2000;

    @Column(length = MAX_TIPO_ANTECEDENTE)
    private String tipoAntecedente; // por ejemplo: "ALERGIA", "QUIRURGICO", "FAMILIAR"

    @Column(length = MAX_DETALLE)
    private String detalle;

    public Antecedente() {
    }

    public Antecedente(HistoriaClinica historia, Long profesionalId,
                       String tipoAntecedente, String detalle) {
        super(historia, profesionalId);
        this.tipoAntecedente = tipoAntecedente;
        this.detalle = detalle;
    }

    @Override
    public TipoEntrada getTipo() {
        return TipoEntrada.ANTECEDENTE;
    }

    public String getTipoAntecedente() {
        return tipoAntecedente;
    }

    public void setTipoAntecedente(String tipoAntecedente) {
        this.tipoAntecedente = tipoAntecedente;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }
}
