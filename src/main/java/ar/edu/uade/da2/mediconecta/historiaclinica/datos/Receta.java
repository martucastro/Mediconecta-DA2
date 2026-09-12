package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("RECETA")
public class Receta extends EntradaClinica {

    private String medicamento;
    private String dosis;

    // Integer y no int: con SINGLE_TABLE la columna queda nula en las filas
    // que son antecedentes o diagnósticos.
    private Integer diasTratamiento;

    public Receta() {
    }

    public Receta(HistoriaClinica historia, Long profesionalId,
                  String medicamento, String dosis, Integer diasTratamiento) {
        super(historia, profesionalId);
        this.medicamento = medicamento;
        this.dosis = dosis;
        this.diasTratamiento = diasTratamiento;
    }

    @Override
    public TipoEntrada getTipo() {
        return TipoEntrada.RECETA;
    }

    public String getMedicamento() {
        return medicamento;
    }

    public void setMedicamento(String medicamento) {
        this.medicamento = medicamento;
    }

    public String getDosis() {
        return dosis;
    }

    public void setDosis(String dosis) {
        this.dosis = dosis;
    }

    public Integer getDiasTratamiento() {
        return diasTratamiento;
    }

    public void setDiasTratamiento(Integer diasTratamiento) {
        this.diasTratamiento = diasTratamiento;
    }
}
