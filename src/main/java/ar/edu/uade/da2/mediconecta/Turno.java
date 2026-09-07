package ar.edu.uade.da2.mediconecta;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "paciente_id")
    private Usuario paciente;

    @ManyToOne
    @JoinColumn(name = "profesional_id")
    private Usuario profesional;

    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    private EstadoTurno estado;

    private LocalDateTime inicioHold;

    // Constructor vacío (obligatorio para JPA)
    public Turno() {
    }

    public Turno(Usuario profesional, LocalDateTime fechaHora) {
        this.profesional = profesional;
        this.fechaHora = fechaHora;
        this.estado = EstadoTurno.DISPONIBLE;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getPaciente() {
        return paciente;
    }

    public void setPaciente(Usuario paciente) {
        this.paciente = paciente;
    }

    public Usuario getProfesional() {
        return profesional;
    }

    public void setProfesional(Usuario profesional) {
        this.profesional = profesional;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public LocalDateTime getInicioHold() {
        return inicioHold;
    }

    public void setInicioHold(LocalDateTime inicioHold) {
        this.inicioHold = inicioHold;
    }
}
