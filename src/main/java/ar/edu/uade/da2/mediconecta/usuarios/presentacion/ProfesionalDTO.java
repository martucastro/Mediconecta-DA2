package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;

/**
 * Vista publica de un profesional: solo id y nombre. A proposito no tiene
 * email ni rol, porque el endpoint que lo devuelve no exige autenticacion.
 */
public class ProfesionalDTO {

    private Long id;
    private String nombre;

    public ProfesionalDTO() {
    }

    public ProfesionalDTO(Usuario usuario) {
        this.id = usuario.getId();
        this.nombre = usuario.getNombre();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
