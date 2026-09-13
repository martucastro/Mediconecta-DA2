package ar.edu.uade.da2.mediconecta.turnos.presentacion;

public class ReservaTurnoRequest {

    private Long turnoId;
    private Long pacienteId;

    public ReservaTurnoRequest() {
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
}
