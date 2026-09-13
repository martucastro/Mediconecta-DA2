package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.time.LocalDateTime;

import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;

/**
 * Vista de un turno hacia afuera del sistema.
 *
 * Existe por el mismo motivo que UsuarioDTO y HistoriaClinicaDTO: la entidad
 * Turno referencia a Usuario, y Usuario expone getContrasenaHash(). Devolver la
 * entidad directamente hacia JAX-RS hacia que JSON-B serializara el hash de
 * contrasena del paciente y del profesional en cada respuesta.
 *
 * De la relacion solo se exponen el id y el nombre, que es lo unico que la
 * capa de presentacion necesita mostrar.
 */
public class TurnoDTO {

    private Long id;
    private LocalDateTime fechaHora;
    private String estado;
    private Long profesionalId;
    private String profesionalNombre;
    private Long pacienteId;
    private String pacienteNombre;
    private LocalDateTime inicioHold;

    public TurnoDTO() {
    }

    public TurnoDTO(Turno turno) {
        this.id = turno.getId();
        this.fechaHora = turno.getFechaHora();
        this.estado = turno.getEstado() != null ? turno.getEstado().name() : null;
        this.inicioHold = turno.getInicioHold();

        if (turno.getProfesional() != null) {
            this.profesionalId = turno.getProfesional().getId();
            this.profesionalNombre = turno.getProfesional().getNombre();
        }
        if (turno.getPaciente() != null) {
            this.pacienteId = turno.getPaciente().getId();
            this.pacienteNombre = turno.getPaciente().getNombre();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public String getProfesionalNombre() {
        return profesionalNombre;
    }

    public void setProfesionalNombre(String profesionalNombre) {
        this.profesionalNombre = profesionalNombre;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getPacienteNombre() {
        return pacienteNombre;
    }

    public void setPacienteNombre(String pacienteNombre) {
        this.pacienteNombre = pacienteNombre;
    }

    public LocalDateTime getInicioHold() {
        return inicioHold;
    }

    public void setInicioHold(LocalDateTime inicioHold) {
        this.inicioHold = inicioHold;
    }
}
