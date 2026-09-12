package ar.edu.uade.da2.mediconecta.historiaclinica;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HistoriaClinicaDTO {

    private Long id;
    private Long pacienteId;
    private LocalDateTime fechaCreacion;
    private List<EntradaClinicaDTO> entradas = new ArrayList<>();

    public HistoriaClinicaDTO() {
    }

    public HistoriaClinicaDTO(HistoriaClinica historia) {
        this.id = historia.getId();
        this.pacienteId = historia.getPacienteId();
        this.fechaCreacion = historia.getFechaCreacion();
        for (EntradaClinica entrada : historia.getEntradas()) {
            this.entradas.add(new EntradaClinicaDTO(entrada));
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<EntradaClinicaDTO> getEntradas() {
        return entradas;
    }

    public void setEntradas(List<EntradaClinicaDTO> entradas) {
        this.entradas = entradas;
    }
}
