package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "historias_clinicas")
public class HistoriaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Se referencia al paciente por id y no con @ManyToOne: la tabla de usuarios
    // pertenece al componente ServicioDeUsuarios, no a este.
    @Column(unique = true, nullable = false)
    private Long pacienteId;

    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "historia", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private List<EntradaClinica> entradas = new ArrayList<>();

    // Constructor vacío (obligatorio para JPA)
    public HistoriaClinica() {
    }

    public HistoriaClinica(Long pacienteId) {
        this.pacienteId = pacienteId;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void agregarEntrada(EntradaClinica entrada) {
        entradas.add(entrada);
        entrada.setHistoria(this);
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<EntradaClinica> getEntradas() {
        return entradas;
    }

    public void setEntradas(List<EntradaClinica> entradas) {
        this.entradas = entradas;
    }
}
