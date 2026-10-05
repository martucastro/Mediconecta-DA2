package ar.edu.uade.da2.mediconecta.obrassociales.datos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Con qué datos conoce la obra social a un paciente de MediConecta.
 *
 * El paciente se referencia por id y no con @ManyToOne: la tabla de usuarios es
 * de otro componente. Mismo criterio que HistoriaClinica.
 */
@Entity
@Table(name = "afiliaciones_obra_social")
public class AfiliacionDePaciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pacienteId;

    @Column(nullable = false)
    private String dni;

    @Column(nullable = false)
    private String numeroAfiliado;

    // Constructor vacío (obligatorio para JPA)
    public AfiliacionDePaciente() {
    }

    public AfiliacionDePaciente(Long pacienteId, String dni, String numeroAfiliado) {
        this.pacienteId = pacienteId;
        this.dni = dni;
        this.numeroAfiliado = numeroAfiliado;
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getNumeroAfiliado() {
        return numeroAfiliado;
    }

    public void setNumeroAfiliado(String numeroAfiliado) {
        this.numeroAfiliado = numeroAfiliado;
    }
}
