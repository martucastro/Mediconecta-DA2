package ar.edu.uade.da2.mediconecta.turnos.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;

class TurnoDTOTest {

    @Test
    void exponeModalidadConsultorioYCobertura() {
        Turno turno = new Turno(new Usuario("Dra. X", "p@m.com", "PROFESIONAL", "hash"),
                LocalDateTime.now().plusDays(1), ModalidadTurno.PRESENCIAL, "Consultorio 3");
        turno.setCoberturaAutorizada(true);
        turno.setCoberturaPorcentaje(new BigDecimal("70.00"));
        turno.setCopago(new BigDecimal("3500.00"));
        turno.setNumeroAutorizacion("AUT-123");

        TurnoDTO dto = new TurnoDTO(turno);

        assertEquals("PRESENCIAL", dto.getModalidad());
        assertEquals("Consultorio 3", dto.getConsultorio());
        assertTrue(dto.getCoberturaAutorizada());
        assertEquals(new BigDecimal("70.00"), dto.getCoberturaPorcentaje());
        assertEquals(new BigDecimal("3500.00"), dto.getCopago());
        assertEquals("AUT-123", dto.getNumeroAutorizacion());
    }

    @Test
    void laCoberturaSinCompletarViajaVacia() {
        TurnoDTO dto = new TurnoDTO(new Turno(null, LocalDateTime.now().plusDays(1),
                ModalidadTurno.TELEMEDICINA, null));

        assertEquals("TELEMEDICINA", dto.getModalidad());
        assertNull(dto.getConsultorio());
        assertNull(dto.getCoberturaAutorizada());
        assertNull(dto.getCopago());
    }
}
