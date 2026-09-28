package ar.edu.uade.da2.mediconecta.pagos.presentacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;

/**
 * Vista de salida de un pago. No expone la entidad JPA directamente al cliente.
 */
public class PagoDTO {

    private Long id;
    private Long turnoId;
    private BigDecimal monto;
    private String moneda;
    private EstadoPago estado;
    private String idTransaccionExterna;
    private LocalDateTime fecha;

    public PagoDTO() {
    }

    public PagoDTO(Pago pago) {
        this.id = pago.getId();
        this.turnoId = pago.getTurnoId();
        this.monto = pago.getMonto();
        this.moneda = pago.getMoneda();
        this.estado = pago.getEstado();
        this.idTransaccionExterna = pago.getIdTransaccionExterna();
        this.fecha = pago.getFecha();
    }

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
