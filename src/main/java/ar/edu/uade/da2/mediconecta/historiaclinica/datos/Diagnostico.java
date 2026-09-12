package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("DIAGNOSTICO")
public class Diagnostico extends EntradaClinica {

    @Column(name = "codigo_cie10")
    private String codigoCIE10;

    private String descripcion;

    public Diagnostico() {
    }

    public Diagnostico(HistoriaClinica historia, Long profesionalId,
                       String codigoCIE10, String descripcion) {
        super(historia, profesionalId);
        this.codigoCIE10 = codigoCIE10;
        this.descripcion = descripcion;
    }

    @Override
    public TipoEntrada getTipo() {
        return TipoEntrada.DIAGNOSTICO;
    }

    public String getCodigoCIE10() {
        return codigoCIE10;
    }

    public void setCodigoCIE10(String codigoCIE10) {
        this.codigoCIE10 = codigoCIE10;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
