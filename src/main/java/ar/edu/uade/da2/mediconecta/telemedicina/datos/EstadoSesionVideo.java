package ar.edu.uade.da2.mediconecta.telemedicina.datos;

/**
 * Estado de la sesion de video. Una sesion existe solo si el proveedor creo la
 * sala (CREADA); queda CANCELADA cuando el paciente cancela el turno
 * confirmado (SalaAlCancelar). Los estados del resto del ciclo de vida (en
 * curso, finalizada) se suman cuando haya algo que los registre.
 */
public enum EstadoSesionVideo {
    CREADA,
    CANCELADA
}
