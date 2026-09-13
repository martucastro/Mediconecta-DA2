package ar.edu.uade.da2.mediconecta.historiaclinica.datos;

import java.time.LocalDateTime;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entrada de una historia clínica. Las tres variantes (antecedente, diagnóstico
 * y receta) comparten tabla y se distinguen por la columna discriminadora "tipo".
 */
@Entity
@Table(name = "entradas_clinicas")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class EntradaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "historia_id")
    private HistoriaClinica historia;

    // El profesional se referencia por id: su tabla pertenece a ServicioDeUsuarios.
    private Long profesionalId;

    private LocalDateTime fecha;

    // Constructor vacío (obligatorio para JPA)
    protected EntradaClinica() {
    }

    protected EntradaClinica(HistoriaClinica historia, Long profesionalId) {
        this.historia = historia;
        this.profesionalId = profesionalId;
        this.fecha = LocalDateTime.now();
    }

    /** Permite conocer el tipo sin recurrir a instanceof desde la capa de negocio. */
    public abstract TipoEntrada getTipo();

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public HistoriaClinica getHistoria() {
        return historia;
    }

    public void setHistoria(HistoriaClinica historia) {
        this.historia = historia;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
