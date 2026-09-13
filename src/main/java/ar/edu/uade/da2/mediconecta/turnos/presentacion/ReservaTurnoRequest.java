package ar.edu.uade.da2.mediconecta.turnos.presentacion;

/**
 * Cuerpo de POST /api/turnos.
 *
 * Ya no lleva pacienteId: el paciente se resuelve del usuario autenticado en
 * ServicioDeTurnos. Si viniera del cuerpo de la peticion, cualquiera podria
 * reservar un turno a nombre de otra persona.
 */
public class ReservaTurnoRequest {

    private Long turnoId;

    public ReservaTurnoRequest() {
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }
}
