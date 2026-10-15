package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

/**
 * Resultado de crearSesion: el enlace que le corresponde a quien pidio la sala
 * y si la sala se creo en esta llamada (nueva) o ya existia.
 *
 * Lleva el enlace y no la entidad SesionVideo: presentacion no tiene por que
 * ver los dos enlaces, y asi responde 201 o 200 con lo que ya calculo la misma
 * transaccion, sin volver a consultar.
 */
public record SesionCreada(EnlaceDeSesion enlace, boolean nueva) {
}
