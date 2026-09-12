package ar.edu.uade.da2.mediconecta.historiaclinica;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ANTECEDENTE")
public class Antecedente extends EntradaClinica {

    private String tipoAntecedente; // por ejemplo: "ALERGIA", "QUIRURGICO", "FAMILIAR"
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
