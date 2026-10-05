package ar.edu.uade.da2.mediconecta.telemedicina.presentacion;

import ar.edu.uade.da2.mediconecta.telemedicina.negocio.EnlaceDeSesion;

/**
 * Vista de la sala de video hacia quien pregunta: su enlace y su rol en la
 * sala. No expone el enlace del otro participante ni el id de la sala del lado
 * del proveedor.
 */
public class EnlaceSesionDTO {

    private Long turnoId;
    private String rol;
    private String enlace;
    private String estado;

    public EnlaceSesionDTO() {
    }

    public EnlaceSesionDTO(EnlaceDeSesion enlace) {
        this.turnoId = enlace.turnoId();
        this.rol = enlace.rol();
        this.enlace = enlace.enlace();
        this.estado = enlace.estado() != null ? enlace.estado().name() : null;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getEnlace() {
        return enlace;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
