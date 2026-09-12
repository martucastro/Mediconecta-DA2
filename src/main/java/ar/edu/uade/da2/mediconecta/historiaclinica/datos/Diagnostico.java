package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("DIAGNOSTICO")
public class Diagnostico extends EntradaClinica {

    // Un codigo CIE-10 mide a lo sumo 8 caracteres (ej. "J18.9", "M79.604").
    public static final int MAX_CODIGO_CIE10 = 10;
    // Texto libre del profesional: 255 caracteres son dos oraciones.
    public static final int MAX_DESCRIPCION = 2000;

    @Column(name = "codigo_cie10", length = MAX_CODIGO_CIE10)
    private String codigoCIE10;

    @Column(length = MAX_DESCRIPCION)
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
