package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ar.edu.uade.da2.mediconecta.facturacion.datos.EstadoReclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;

/**
 * Vista de salida de un reclamo. No expone la entidad JPA directamente al
 * cliente.
 */
public class ReclamoDTO {

    private Long id;
    private Long turnoId;
    private Long pacienteId;
    private String numeroAutorizacion;
    private BigDecimal coberturaPorcentaje;
    private BigDecimal monto;
    private EstadoReclamo estado;
    private Integer intentos;
    private String ultimoError;
    private String numeroPresentacion;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public ReclamoDTO() {
    }

    public ReclamoDTO(Reclamo reclamo) {
        this.id = reclamo.getId();
        this.turnoId = reclamo.getTurnoId();
        this.pacienteId = reclamo.getPacienteId();
        this.numeroAutorizacion = reclamo.getNumeroAutorizacion();
        this.coberturaPorcentaje = reclamo.getCoberturaPorcentaje();
        this.monto = reclamo.getMonto();
        this.estado = reclamo.getEstado();
        this.intentos = reclamo.getIntentos();
        this.ultimoError = reclamo.getUltimoError();
        this.numeroPresentacion = reclamo.getNumeroPresentacion();
        this.creadoEn = reclamo.getCreadoEn();
        this.actualizadoEn = reclamo.getActualizadoEn();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }

    public BigDecimal getCoberturaPorcentaje() {
        return coberturaPorcentaje;
    }

    public void setCoberturaPorcentaje(BigDecimal coberturaPorcentaje) {
        this.coberturaPorcentaje = coberturaPorcentaje;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public void setEstado(EstadoReclamo estado) {
        this.estado = estado;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public String getNumeroPresentacion() {
        return numeroPresentacion;
    }

    public void setNumeroPresentacion(String numeroPresentacion) {
        this.numeroPresentacion = numeroPresentacion;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
