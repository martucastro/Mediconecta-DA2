package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import java.time.LocalDateTime;

/**
 * Cuerpo de POST /api/turnos/disponibilidad.
 *
 * El profesional se resuelve del usuario autenticado, no del cuerpo: un
 * profesional solo abre franjas en su propia agenda.
 *
 * modalidad es opcional: si no viene, la franja es PRESENCIAL. Asi los clientes
 * que ya mandaban solo la fechaHora siguen funcionando igual que antes. Viaja
 * como texto y no como el enum para que un valor mal escrito termine en un 400
 * con un mensaje claro (lo traduce TurnosResource) y no en un error de
 * deserializacion de JSON-B.
 *
 * consultorio solo se acepta en las franjas presenciales.
 */
public class NuevaDisponibilidadRequest {

    private LocalDateTime fechaHora;
    private String modalidad;
    private String consultorio;

    public NuevaDisponibilidadRequest() {
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(String consultorio) {
        this.consultorio = consultorio;
    }
}
