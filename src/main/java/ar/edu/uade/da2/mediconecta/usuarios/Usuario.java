package ar.edu.uade.da2.mediconecta.usuarios;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String email;
    private String rol; // por ejemplo: "PACIENTE", "PROFESIONAL", "ADMINISTRADOR"
    private String contrasenaHash;
    
    // Constructor vacío (obligatorio para JPA)
    public Usuario() {
    }

    public Usuario(String nombre, String email, String rol, String contrasenaHash) {
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
        this.contrasenaHash = contrasenaHash;
    }

    // Getters y setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
    
    public String getContrasenaHash() {
    	return contrasenaHash;
    }
    
    public void setContrasenaHash(String contrasenaHash) {
    	this.contrasenaHash = contrasenaHash;
    }
}