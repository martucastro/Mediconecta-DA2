package ar.edu.uade.da2.mediconecta.externos.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

class ProveedorDeVideoExternoResourceTest {

    private final ProveedorDeVideoExternoResource proveedor = new ProveedorDeVideoExternoResource();

    @AfterEach
    void restaurar() {
        System.clearProperty(ProveedorDeVideoExternoResource.PROPIEDAD_SIMULAR_CAIDA);
    }

    @Test
    void creaUnaSalaConUnEnlacePorParticipante() {
        Response respuesta = proveedor.crearSala(new SalaExternaRequest("turno-7", "2027-03-16T10:00"));

        assertEquals(200, respuesta.getStatus());
        SalaExternaResponse sala = (SalaExternaResponse) respuesta.getEntity();
        assertTrue(sala.getRoomId().startsWith("MediConecta-"));
        assertTrue(sala.getHostUrl().contains(sala.getRoomId()));
        assertTrue(sala.getGuestUrl().contains(sala.getRoomId()));
        assertNotEquals(sala.getHostUrl(), sala.getGuestUrl());
    }

    @Test
    void cadaPedidoEsUnaSalaDistinta() {
        SalaExternaResponse a = (SalaExternaResponse) proveedor
                .crearSala(new SalaExternaRequest("turno-7", null)).getEntity();
        SalaExternaResponse b = (SalaExternaResponse) proveedor
                .crearSala(new SalaExternaRequest("turno-7", null)).getEntity();

        assertNotEquals(a.getRoomId(), b.getRoomId());
    }

    @Test
    void sinReferenceEs400() {
        assertEquals(400, proveedor.crearSala(null).getStatus());
        assertEquals(400, proveedor.crearSala(new SalaExternaRequest(" ", null)).getStatus());
    }

    @Test
    void simulaUnaCaidaConLaReferenceOConLaPropiedad() {
        assertEquals(503, proveedor.crearSala(new SalaExternaRequest("caer-1", null)).getStatus());

        System.setProperty(ProveedorDeVideoExternoResource.PROPIEDAD_SIMULAR_CAIDA, "true");
        assertEquals(503, proveedor.crearSala(new SalaExternaRequest("turno-7", null)).getStatus());
    }
}
