package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.time.LocalDateTime;

/**
 * Cuerpo de POST /api/turnos/disponibilidad.
 *
 * El profesional se resuelve del usuario autenticado, no del cuerpo: un
 * profesional solo abre franjas en su propia agenda.
 */
public class NuevaDisponibilidadRequest {

    private LocalDateTime fechaHora;

    public NuevaDisponibilidadRequest() {
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
