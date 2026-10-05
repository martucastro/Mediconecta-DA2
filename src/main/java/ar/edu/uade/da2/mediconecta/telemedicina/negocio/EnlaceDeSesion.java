package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import ar.edu.uade.da2.mediconecta.telemedicina.datos.EstadoSesionVideo;

/**
 * Lo que un participante ve de la sesion: su propio enlace y con que rol entra.
 * Nunca lleva el enlace del otro participante.
 */
public record EnlaceDeSesion(Long turnoId, String rol, String enlace, EstadoSesionVideo estado) {
}
