package ar.edu.uade.da2.mediconecta.telemedicina.datos;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sala de video de un turno de telemedicina.
 *
 * Referencia al turno, al paciente y al profesional por id, no con @ManyToOne:
 * las tablas turnos y usuarios son de otros componentes. Mismo criterio que
 * HistoriaClinica y Pago.
 *
 * Guarda los dos enlaces del proveedor, pero ninguno sale junto con el otro:
 * cada participante recibe solo el suyo (ServicioDeTelemedicina.obtenerEnlace).
 *
 * turnoId es unico: un turno tiene a lo sumo una sala.
 */
@Entity
@Table(name = "sesiones_video")
public class SesionVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long turnoId;

    // El paciente para el que se creo la sala. Si el hold vence y otro paciente
    // toma el turno, la sala se renueva: el enlace del primero no le sirve al
    // segundo, ni el segundo tiene que recibir un enlace que el primero conoce.
    @Column(nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private Long profesionalId;

    // Identificador de la sala del lado del proveedor de video.
    @Column(nullable = false, length = 120)
    private String salaId;

    @Column(nullable = false, length = 500)
    private String enlaceProfesional;

    @Column(nullable = false, length = 500)
    private String enlacePaciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSesionVideo estado;

    private LocalDateTime creadaEn;

    // Constructor vacío (obligatorio para JPA)
    public SesionVideo() {
    }

    public SesionVideo(Long turnoId, Long pacienteId, Long profesionalId, String salaId,
            String enlaceProfesional, String enlacePaciente) {
        this.turnoId = turnoId;
        this.profesionalId = profesionalId;
        asignarSala(pacienteId, salaId, enlaceProfesional, enlacePaciente);
    }

    /**
     * Reemplaza la sala por una nueva para otro paciente del mismo turno.
     */
    public void asignarSala(Long pacienteId, String salaId, String enlaceProfesional,
            String enlacePaciente) {
        this.pacienteId = pacienteId;
        this.salaId = salaId;
        this.enlaceProfesional = enlaceProfesional;
        this.enlacePaciente = enlacePaciente;
        this.estado = EstadoSesionVideo.CREADA;
        this.creadaEn = LocalDateTime.now();
    }

    // Getters y setters
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

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public String getSalaId() {
        return salaId;
    }

    public void setSalaId(String salaId) {
        this.salaId = salaId;
    }

    public String getEnlaceProfesional() {
        return enlaceProfesional;
    }

    public void setEnlaceProfesional(String enlaceProfesional) {
        this.enlaceProfesional = enlaceProfesional;
    }

    public String getEnlacePaciente() {
        return enlacePaciente;
    }

    public void setEnlacePaciente(String enlacePaciente) {
        this.enlacePaciente = enlacePaciente;
    }

    public EstadoSesionVideo getEstado() {
        return estado;
    }

    public void setEstado(EstadoSesionVideo estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }
}
