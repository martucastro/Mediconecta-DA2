package ar.edu.uade.da2.mediconecta.obrassociales.datos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Autorización emitida por la obra social. Se guarda porque facturación la
 * necesita después: el número es lo que se le presenta a la obra social para
 * cobrarle su parte, y el copago es lo que le corresponde al paciente.
 *
 * Sólo existen filas de prestaciones autorizadas: una consulta de cobertura o
 * una negativa no generan autorización.
 */
@Entity
@Table(name = "autorizaciones_prestacion")
public class AutorizacionDePrestacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String codigoPrestacion;

    @Column(nullable = false)
    private String numeroAutorizacion;

    @Column(nullable = false)
    private LocalDateTime fechaAutorizacion;

    private int porcentajeCobertura;

    @Column(precision = 12, scale = 2)
    private BigDecimal copago;

    // Constructor vacío (obligatorio para JPA)
    public AutorizacionDePrestacion() {
    }

    public AutorizacionDePrestacion(Long pacienteId, String codigoPrestacion, String numeroAutorizacion,
            LocalDateTime fechaAutorizacion, int porcentajeCobertura, BigDecimal copago) {
        this.pacienteId = pacienteId;
        this.codigoPrestacion = codigoPrestacion;
        this.numeroAutorizacion = numeroAutorizacion;
        this.fechaAutorizacion = fechaAutorizacion;
        this.porcentajeCobertura = porcentajeCobertura;
        this.copago = copago;
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getCodigoPrestacion() {
        return codigoPrestacion;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public int getPorcentajeCobertura() {
        return porcentajeCobertura;
    }

    public BigDecimal getCopago() {
        return copago;
    }
}
