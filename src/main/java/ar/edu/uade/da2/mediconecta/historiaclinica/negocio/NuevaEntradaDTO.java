package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;

/**
 * Datos de entrada para crear una entrada clínica de cualquiera de los tres
 * tipos. Sólo los campos del tipo indicado son relevantes; el resto viaja nulo
 * y es EntradaClinicaFactory quien decide cuáles leer y cuáles exigir.
 */
public class NuevaEntradaDTO {

    private TipoEntrada tipo;
    private Long profesionalId;

    // ANTECEDENTE
    private String tipoAntecedente;
    private String detalle;

    // DIAGNOSTICO
    private String codigoCIE10;
    private String descripcion;

    // RECETA
    private String medicamento;
    private String dosis;
    private Integer diasTratamiento;

    public TipoEntrada getTipo() {
        return tipo;
    }

    public void setTipo(TipoEntrada tipo) {
        this.tipo = tipo;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
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
