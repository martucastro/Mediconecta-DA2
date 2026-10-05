package ar.edu.uade.da2.mediconecta.telemedicina.datos;

/**
 * Estado de la sesion de video. Hoy una sesion existe solo si el proveedor creo
 * la sala, asi que el unico estado es CREADA; los estados del resto del ciclo
 * de vida (en curso, finalizada) se suman cuando haya algo que los registre.
 */
public enum EstadoSesionVideo {
    CREADA
}
