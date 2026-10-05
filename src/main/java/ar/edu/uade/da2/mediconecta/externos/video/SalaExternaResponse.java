package ar.edu.uade.da2.mediconecta.externos.video;

/**
 * Sala creada por el proveedor de video: su id y un enlace de acceso por
 * participante. hostUrl es el del anfitrion (el profesional) y guestUrl el del
 * invitado (el paciente).
 */
public class SalaExternaResponse {

    private String roomId;
    private String hostUrl;
    private String guestUrl;

    public SalaExternaResponse() {
    }

    public SalaExternaResponse(String roomId, String hostUrl, String guestUrl) {
        this.roomId = roomId;
        this.hostUrl = hostUrl;
        this.guestUrl = guestUrl;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    public void setHostUrl(String hostUrl) {
        this.hostUrl = hostUrl;
    }

    public String getGuestUrl() {
        return guestUrl;
    }

    public void setGuestUrl(String guestUrl) {
        this.guestUrl = guestUrl;
    }
}
