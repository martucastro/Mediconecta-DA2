package ar.edu.uade.da2.mediconecta.facturacion.datos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reclamo de facturacion a la obra social por un turno con cobertura
 * autorizada.
 *
 * Se referencia al turno por id y no con @ManyToOne, igual que Pago: la tabla
 * de turnos pertenece a ServicioDeTurnos, no a este componente. turnoId es
 * UNIQUE porque el registro es idempotente por turno (ver
 * ServicioDeFacturacion.registrarReclamo).
 */
@Entity
@Table(name = "reclamos")
public class Reclamo {

    public static final int MAX_NUMERO_AUTORIZACION = 40;
    public static final int MAX_ULTIMO_ERROR = 500;
    public static final int MAX_NUMERO_PRESENTACION = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long turnoId;

    @Column(nullable = false)
    private Long pacienteId;

    @Column(length = MAX_NUMERO_AUTORIZACION)
    private String numeroAutorizacion;

    @Column(precision = 5, scale = 2)
    private BigDecimal coberturaPorcentaje;

    // Monto reconocido por la obra social. MediConecta no es dueno de los
    // aranceles (los tiene el legado, PadronDeAfiliados.ARANCELES), asi que
    // queda nulo hasta que el canal responde con el monto al enviar.
    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReclamo estado;

    @Column(nullable = false)
    private Integer intentos;

    @Column(length = MAX_ULTIMO_ERROR)
    private String ultimoError;

    @Column(length = MAX_NUMERO_PRESENTACION)
    private String numeroPresentacion;

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    // Constructor vacío (obligatorio para JPA)
    public Reclamo() {
    }

    public Reclamo(Long turnoId, Long pacienteId, String numeroAutorizacion,
            BigDecimal coberturaPorcentaje) {
        this.turnoId = turnoId;
        this.pacienteId = pacienteId;
        this.numeroAutorizacion = numeroAutorizacion;
        this.coberturaPorcentaje = coberturaPorcentaje;
        this.estado = EstadoReclamo.PENDIENTE;
        this.intentos = 0;
        LocalDateTime ahora = LocalDateTime.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }

    public BigDecimal getCoberturaPorcentaje() {
        return coberturaPorcentaje;
    }

    public void setCoberturaPorcentaje(BigDecimal coberturaPorcentaje) {
        this.coberturaPorcentaje = coberturaPorcentaje;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public void setEstado(EstadoReclamo estado) {
        this.estado = estado;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public String getNumeroPresentacion() {
        return numeroPresentacion;
    }

    public void setNumeroPresentacion(String numeroPresentacion) {
        this.numeroPresentacion = numeroPresentacion;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
