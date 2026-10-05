package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.facturacion.datos.EstadoReclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.ReclamoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.MapMessage;
import jakarta.jms.Queue;

/**
 * Pruebas de ServicioDeFacturacion con dobles de Mockito, mismo estilo que
 * ServicioDeTurnosTest: sin contenedor, ServicioDeTurnos y CanalDeReclamos se
 * mockean como las fachadas de otros componentes que son.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeFacturacionTest {

    @Mock
    private ReclamoDAO reclamoDAO;
    @Mock
    private ServicioDeTurnos servicioDeTurnos;
    @Mock
    private CanalDeReclamos canal;
    @Mock
    private JMSContext jmsContext;
    @Mock
    private Queue colaReclamos;
    @Mock
    private JMSProducer productor;

    @InjectMocks
    private ServicioDeFacturacion servicio;

    // ---- registrarReclamo -------------------------------------------------

    @Test
    void sinCoberturaAutorizadaNoRegistraNingunReclamo() {
        Turno turno = turno(1L, null);
        when(servicioDeTurnos.obtenerTurno(1L)).thenReturn(turno);

        servicio.registrarReclamo(1L);

        verify(reclamoDAO, never()).guardar(any());
        verify(jmsContext, never()).createProducer();
    }

    @Test
    void unaCoberturaRechazadaNoRegistraNingunReclamo() {
        Turno turno = turno(2L, false);
        when(servicioDeTurnos.obtenerTurno(2L)).thenReturn(turno);

        servicio.registrarReclamo(2L);

        verify(reclamoDAO, never()).guardar(any());
    }

    @Test
    void siYaExisteUnReclamoParaElTurnoEsIdempotente() {
        Turno turno = turno(3L, true);
        when(servicioDeTurnos.obtenerTurno(3L)).thenReturn(turno);
        when(reclamoDAO.buscarPorTurno(3L)).thenReturn(new Reclamo(3L, 1L, "AUT-1", null));

        servicio.registrarReclamo(3L);

        verify(reclamoDAO, never()).guardar(any());
        verify(jmsContext, never()).createProducer();
    }

    @Test
    void conCoberturaAutorizadaPersisteElReclamoYLoEncolaEnLaMismaTransaccion() {
        Turno turno = turno(4L, true);
        when(servicioDeTurnos.obtenerTurno(4L)).thenReturn(turno);
        when(reclamoDAO.buscarPorTurno(4L)).thenReturn(null);
        when(jmsContext.createMapMessage()).thenReturn(mock(MapMessage.class));
        when(jmsContext.createProducer()).thenReturn(productor);
        // IDENTITY asigna el id al persistir: el mock lo simula.
        doAnswer(invocacion -> {
            Reclamo guardado = invocacion.getArgument(0);
            guardado.setId(99L);
            return null;
        }).when(reclamoDAO).guardar(any(Reclamo.class));

        servicio.registrarReclamo(4L);

        verify(reclamoDAO).guardar(any(Reclamo.class));
        verify(productor).send(eq(colaReclamos), any(MapMessage.class));
    }

    // ---- procesarReclamo ---------------------------------------------------

    @Test
    void procesarConExitoDejaElReclamoEnviadoConMontoYNumeroDePresentacion() {
        Reclamo reclamo = pendiente(10L);
        when(reclamoDAO.buscar(10L)).thenReturn(reclamo);
        when(canal.presentarReclamo(10L, 1L, "AUT-1", new BigDecimal("70.00")))
                .thenReturn(new ResultadoReclamo(new BigDecimal("1500.00"), "PRES-9"));

        servicio.procesarReclamo(10L, 1);

        assertEquals(EstadoReclamo.ENVIADO, reclamo.getEstado());
        assertEquals(new BigDecimal("1500.00"), reclamo.getMonto());
        assertEquals("PRES-9", reclamo.getNumeroPresentacion());
        verify(reclamoDAO, never()).registrarIntentoFallido(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void procesarConFallaTransitoriaPorDebajoDelMaximoRegistraElIntentoYRelanza() {
        Reclamo reclamo = pendiente(11L);
        when(reclamoDAO.buscar(11L)).thenReturn(reclamo);
        CanalDeReclamosNoDisponibleException falla = new CanalDeReclamosNoDisponibleException("caido");
        when(canal.presentarReclamo(11L, 1L, "AUT-1", new BigDecimal("70.00"))).thenThrow(falla);

        assertThrows(CanalDeReclamosNoDisponibleException.class,
                () -> servicio.procesarReclamo(11L, ServicioDeFacturacion.MAX_INTENTOS - 1));

        verify(reclamoDAO).registrarIntentoFallido(11L, "caido", false);
    }

    @Test
    void procesarConFallaTransitoriaEnElUltimoIntentoQuedaEnRevisionManualSinRelanzar() {
        Reclamo reclamo = pendiente(12L);
        when(reclamoDAO.buscar(12L)).thenReturn(reclamo);
        when(canal.presentarReclamo(12L, 1L, "AUT-1", new BigDecimal("70.00")))
                .thenThrow(new CanalDeReclamosNoDisponibleException("caido"));

        servicio.procesarReclamo(12L, ServicioDeFacturacion.MAX_INTENTOS);

        verify(reclamoDAO).registrarIntentoFallido(12L, "caido", true);
    }

    @Test
    void procesarConRechazoDefinitivoQuedaEnRevisionManualInmediatoSinRelanzar() {
        Reclamo reclamo = pendiente(13L);
        when(reclamoDAO.buscar(13L)).thenReturn(reclamo);
        when(canal.presentarReclamo(13L, 1L, "AUT-1", new BigDecimal("70.00")))
                .thenThrow(new ReclamoRechazadoException("autorizacion invalida"));

        servicio.procesarReclamo(13L, 1);

        verify(reclamoDAO).registrarIntentoFallido(13L, "autorizacion invalida", true);
    }

    @Test
    void procesarUnReclamoYaEnviadoEsIdempotenteYNoLlamaAlCanal() {
        Reclamo reclamo = pendiente(14L);
        reclamo.setEstado(EstadoReclamo.ENVIADO);
        when(reclamoDAO.buscar(14L)).thenReturn(reclamo);

        servicio.procesarReclamo(14L, 2);

        verify(canal, never()).presentarReclamo(any(), any(), any(), any());
    }

    @Test
    void procesarUnReclamoEnRevisionManualEsIdempotenteYNoLlamaAlCanal() {
        Reclamo reclamo = pendiente(15L);
        reclamo.setEstado(EstadoReclamo.EN_REVISION_MANUAL);
        when(reclamoDAO.buscar(15L)).thenReturn(reclamo);

        servicio.procesarReclamo(15L, 2);

        verify(canal, never()).presentarReclamo(any(), any(), any(), any());
    }

    // ---- helpers -------------------------------------------------------------

    private Turno turno(Long id, Boolean coberturaAutorizada) {
        Usuario paciente = new Usuario("Paciente", "p@m.com", "PACIENTE", "hash");
        paciente.setId(1L);
        Turno turno = new Turno(null, LocalDateTime.now().plusDays(1), ModalidadTurno.PRESENCIAL, null);
        turno.setId(id);
        turno.setPaciente(paciente);
        turno.setCoberturaAutorizada(coberturaAutorizada);
        turno.setCoberturaPorcentaje(new BigDecimal("70.00"));
        turno.setNumeroAutorizacion("AUT-1");
        return turno;
    }

    private Reclamo pendiente(Long turnoId) {
        return new Reclamo(turnoId, 1L, "AUT-1", new BigDecimal("70.00"));
    }
}
