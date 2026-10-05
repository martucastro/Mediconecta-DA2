package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnReserva;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/** El observador que engancha la cobertura al punto de extensión de la reserva. */
@ExtendWith(MockitoExtension.class)
class CoberturaEnLaReservaTest {

    private static final Long PACIENTE_ID = 3L;

    @Mock
    private ServicioDeObrasSociales obrasSociales;

    @InjectMocks
    private CoberturaEnLaReserva observador;

    @Test
    void escribeEnElTurnoLaCoberturaParcial() {
        Turno turno = turno(ModalidadTurno.PRESENCIAL);
        when(obrasSociales.cotizarReserva(PACIENTE_ID, Prestacion.CONSULTA)).thenReturn(
                new Cobertura(true, 70, new BigDecimal("6000.00"), "AUT-OS-2002-CONSULTA", "ok"));

        observador.alReservar(new TurnoEnReserva(turno));

        assertEquals(Boolean.TRUE, turno.getCoberturaAutorizada());
        assertEquals(new BigDecimal("70"), turno.getCoberturaPorcentaje());
        assertEquals(new BigDecimal("6000.00"), turno.getCopago());
        assertEquals("AUT-OS-2002-CONSULTA", turno.getNumeroAutorizacion());
    }

    @Test
    void sinCoberturaElCopagoEsElValorTotalYNoHayNumero() {
        Turno turno = turno(ModalidadTurno.PRESENCIAL);
        when(obrasSociales.cotizarReserva(PACIENTE_ID, Prestacion.CONSULTA)).thenReturn(
                new Cobertura(false, 0, new BigDecimal("20000.00"), null, "sin cobertura"));

        observador.alReservar(new TurnoEnReserva(turno));

        assertEquals(Boolean.FALSE, turno.getCoberturaAutorizada());
        assertEquals(new BigDecimal("0"), turno.getCoberturaPorcentaje());
        assertEquals(new BigDecimal("20000.00"), turno.getCopago());
        assertNull(turno.getNumeroAutorizacion());
    }

    @Test
    void unTurnoDeTelemedicinaSeCotizaComoTeleconsulta() {
        Turno turno = turno(ModalidadTurno.TELEMEDICINA);
        when(obrasSociales.cotizarReserva(PACIENTE_ID, Prestacion.TELECONSULTA)).thenReturn(
                new Cobertura(true, 100, new BigDecimal("0.00"), "AUT-1", "ok"));

        observador.alReservar(new TurnoEnReserva(turno));

        assertEquals(new BigDecimal("0.00"), turno.getCopago());
    }

    @Test
    void siLaObraSocialNoRespondeLaExcepcionSaleYElTurnoNoSeToca() {
        Turno turno = turno(ModalidadTurno.PRESENCIAL);
        when(obrasSociales.cotizarReserva(PACIENTE_ID, Prestacion.CONSULTA))
                .thenThrow(new ObraSocialNoDisponibleException("no respondió"));

        assertThrows(ObraSocialNoDisponibleException.class,
                () -> observador.alReservar(new TurnoEnReserva(turno)));

        assertNull(turno.getCoberturaAutorizada());
        assertNull(turno.getCopago());
    }

    @Test
    void esSincronicoConLaPrioridadDeCoberturaYExigeTransaccion() throws Exception {
        Method metodo = CoberturaEnLaReserva.class.getDeclaredMethod("alReservar", TurnoEnReserva.class);

        assertTrue(metodo.getParameters()[0].isAnnotationPresent(Observes.class));
        assertEquals(PuntosDeExtension.COBERTURA,
                metodo.getParameters()[0].getAnnotation(Priority.class).value());
        assertEquals(TxType.MANDATORY, metodo.getAnnotation(Transactional.class).value());
    }

    private static Turno turno(ModalidadTurno modalidad) {
        Usuario paciente = new Usuario("Paciente", "p@mediconecta.com", "PACIENTE", "hash");
        paciente.setId(PACIENTE_ID);
        Turno turno = new Turno(new Usuario("Prof", "d@mediconecta.com", "PROFESIONAL", "hash"),
                LocalDateTime.now().plusDays(2), modalidad, null);
        turno.setPaciente(paciente);
        turno.setEstado(EstadoTurno.DISPONIBLE);
        return turno;
    }
}
