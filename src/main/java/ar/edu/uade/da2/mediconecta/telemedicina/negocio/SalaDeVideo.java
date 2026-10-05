package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

/**
 * Sala creada por el proveedor, ya traducida al dominio de MediConecta: su id
 * del lado del proveedor y el enlace de cada participante.
 */
public record SalaDeVideo(String salaId, String enlaceProfesional, String enlacePaciente) {
}
