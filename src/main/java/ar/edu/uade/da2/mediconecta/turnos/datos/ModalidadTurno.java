package ar.edu.uade.da2.mediconecta.turnos.datos;

/**
 * Como se atiende el turno. Lo decide el profesional al abrir la franja, no el
 * paciente al reservarla: la modalidad es parte de la oferta de la agenda.
 */
public enum ModalidadTurno {
    PRESENCIAL,
    TELEMEDICINA
}
