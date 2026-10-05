package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import ar.edu.uade.da2.mediconecta.telemedicina.datos.SesionVideo;

/**
 * Resultado de crearSesion: la sesion del turno y si se creo en esta llamada
 * (nueva) o ya existia. Le permite a presentacion responder 201 o 200 sin tener
 * que adivinarlo.
 */
public record SesionCreada(SesionVideo sesion, boolean nueva) {
}
