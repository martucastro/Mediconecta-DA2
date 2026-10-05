package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

/**
 * Respuesta del proveedor de video: id de sala, enlace del anfitrion (hostUrl,
 * el profesional) y del invitado (guestUrl, el paciente). Mismo shape JSON que
 * externos.video.SalaExternaResponse, sin importarla.
 */
public class SalaExternaResponse {

    private String roomId;
    private String hostUrl;
    private String guestUrl;

    public SalaExternaResponse() {
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
