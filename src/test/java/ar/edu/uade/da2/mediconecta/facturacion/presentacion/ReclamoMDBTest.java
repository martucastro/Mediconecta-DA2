package ar.edu.uade.da2.mediconecta.facturacion.presentacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.facturacion.negocio.ServicioDeFacturacion;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.TextMessage;

/**
 * Parseo de ReclamoMDB. No se levanta ningun contenedor: Message es una
 * interfaz de Jakarta Messaging, se mockea igual que cualquier otra
 * dependencia (mismo criterio que ServicioDeTurnosTest con JMSContext).
 */
@ExtendWith(MockitoExtension.class)
class ReclamoMDBTest {

    @Mock
    private ServicioDeFacturacion servicio;

    @InjectMocks
    private ReclamoMDB mdb;

    @Test
    void unMensajeBienFormadoDelegaElReclamoIdYElIntento() throws JMSException {
        MapMessage mensaje = org.mockito.Mockito.mock(MapMessage.class);
        when(mensaje.getLong("reclamoId")).thenReturn(7L);
        when(mensaje.getIntProperty("JMSXDeliveryCount")).thenReturn(2);

        mdb.onMessage(mensaje);

        verify(servicio).procesarReclamo(7L, 2);
    }

    @Test
    void unMensajeQueNoEsMapMessageSeDescartaSinExcepcionNiLlamarAlServicio() {
        // Un TextMessage (o cualquier otro tipo) nunca deberia llegar a esta
        // cola, pero si llega no puede quedar reintentando para siempre: se
        // loguea y se descarta (ack), no se relanza.
        TextMessage mensaje = org.mockito.Mockito.mock(TextMessage.class);

        assertDoesNotThrow(() -> mdb.onMessage(mensaje));

        verify(servicio, never()).procesarReclamo(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unMensajeAlQueFallaLaLecturaSeDescartaSinExcepcion() throws JMSException {
        MapMessage mensaje = org.mockito.Mockito.mock(MapMessage.class);
        when(mensaje.getLong("reclamoId")).thenThrow(new JMSException("campo ausente"));

        assertDoesNotThrow(() -> mdb.onMessage(mensaje));

        verify(servicio, never()).procesarReclamo(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }
}
