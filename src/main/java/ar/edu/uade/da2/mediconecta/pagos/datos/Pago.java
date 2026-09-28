package ar.edu.uade.da2.mediconecta.pagos.datos;

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

@Entity
@Table(name = "pagos")
public class Pago {

    public static final int MAX_MONEDA = 3;
    public static final int MAX_ID_TRANSACCION = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Se referencia al turno por id y no con @ManyToOne: la tabla de turnos
    // pertenece a ServicioDeTurnos, no a este componente.
    @Column(nullable = false)
    private Long turnoId;

    // BigDecimal y no double: el dinero no se representa en punto flotante.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = MAX_MONEDA, nullable = false)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    // Identificador que devuelve la pasarela externa. Es la referencia con la que
    // se consulta o se reembolsa el cobro; nulo mientras el pago no llegó a la
    // pasarela (por ejemplo si fue rechazado por validación antes de salir).
    @Column(length = MAX_ID_TRANSACCION)
    private String idTransaccionExterna;

    private LocalDateTime fecha;

    // Constructor vacío (obligatorio para JPA)
    public Pago() {
    }

    public Pago(Long turnoId, BigDecimal monto, String moneda) {
        this.turnoId = turnoId;
        this.monto = monto;
        this.moneda = moneda;
        this.estado = EstadoPago.PENDIENTE;
        this.fecha = LocalDateTime.now();
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

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public void setEstado(EstadoPago estado) {
        this.estado = estado;
    }

    public String getIdTransaccionExterna() {
        return idTransaccionExterna;
    }

    public void setIdTransaccionExterna(String idTransaccionExterna) {
        this.idTransaccionExterna = idTransaccionExterna;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
