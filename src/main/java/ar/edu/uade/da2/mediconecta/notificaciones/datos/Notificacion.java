package ar.edu.uade.da2.mediconecta.notificaciones.datos;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro de que un recordatorio de turno confirmado se proceso, sin
 * depender del log para demostrarlo. El envio en si (email/SMS) es simulado
 * por ahora; este registro es lo que la demo puede mostrar como evidencia de
 * que el mensaje JMS se consumio.
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long turnoId;
    private Long pacienteId;
    private String canal; // por ahora siempre "LOG", punto de extension para EMAIL/SMS
    private String mensaje;
    private LocalDateTime fechaEnvio;

    public Notificacion() {
    }

    public Notificacion(Long turnoId, Long pacienteId, String canal, String mensaje) {
        this.turnoId = turnoId;
        this.pacienteId = pacienteId;
        this.canal = canal;
        this.mensaje = mensaje;
        this.fechaEnvio = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getCanal() {
        return canal;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }
}