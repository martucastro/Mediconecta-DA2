package ar.edu.uade.da2.mediconecta.notificaciones.presentacion;

import ar.edu.uade.da2.mediconecta.notificaciones.datos.Notificacion;

import java.time.LocalDateTime;

public class NotificacionDTO {

    private Long id;
    private Long turnoId;
    private String canal;
    private String mensaje;
    private LocalDateTime fechaEnvio;

    public NotificacionDTO() {
    }

    public NotificacionDTO(Notificacion notificacion) {
        this.id = notificacion.getId();
        this.turnoId = notificacion.getTurnoId();
        this.canal = notificacion.getCanal();
        this.mensaje = notificacion.getMensaje();
        this.fechaEnvio = notificacion.getFechaEnvio();
    }

    public Long getId() {
        return id;
    }

    public Long getTurnoId() {
        return turnoId;
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