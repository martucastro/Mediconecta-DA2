package ar.edu.uade.da2.mediconecta.historiaclinica.presentacion;

import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Antecedente;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Diagnostico;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Receta;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;

import java.time.LocalDateTime;

/**
 * Vista de salida de una entrada clínica. Aplana las tres subclases en una
 * única forma: los campos que no corresponden al tipo quedan nulos y JSON-B
 * no los serializa.
 */
public class EntradaClinicaDTO {

    private Long id;
    private TipoEntrada tipo;
    private Long profesionalId;
    private LocalDateTime fecha;

    private String tipoAntecedente;
    private String detalle;
    private String codigoCIE10;
    private String descripcion;
    private String medicamento;
    private String dosis;
    private Integer diasTratamiento;

    public EntradaClinicaDTO() {
    }

    public EntradaClinicaDTO(EntradaClinica entrada) {
        this.id = entrada.getId();
        this.tipo = entrada.getTipo();
        this.profesionalId = entrada.getProfesionalId();
        this.fecha = entrada.getFecha();

        if (entrada instanceof Antecedente antecedente) {
            this.tipoAntecedente = antecedente.getTipoAntecedente();
            this.detalle = antecedente.getDetalle();
        } else if (entrada instanceof Diagnostico diagnostico) {
            this.codigoCIE10 = diagnostico.getCodigoCIE10();
            this.descripcion = diagnostico.getDescripcion();
        } else if (entrada instanceof Receta receta) {
            this.medicamento = receta.getMedicamento();
            this.dosis = receta.getDosis();
            this.diasTratamiento = receta.getDiasTratamiento();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
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
