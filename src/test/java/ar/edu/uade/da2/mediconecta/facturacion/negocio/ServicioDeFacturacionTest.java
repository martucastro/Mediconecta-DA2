package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.facturacion.datos.EstadoReclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;
import ar.edu.uade.da2.mediconecta.facturacion.datos.ReclamoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnosLocal;
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
    private ServicioDeTurnosLocal servicioDeTurnos;
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

    // ---- anularReclamo (SCRUM-124) ------------------------------------------

    // Las transiciones son UPDATE condicionales (ReclamoDAO.cambiarEstado):
    // ganan contra un ReclamoMDB que este presentando el mismo reclamo sin
    // tomar el lock de la fila (SCRUM-128). Los dobles devuelven si el
    // reclamo estaba en el estado esperado.

    @Test
    void cancelarElTurnoAnulaElReclamoPendienteYYaNoSeEnvia() {
        Reclamo reclamo = reclamo(5L, 50L, EstadoReclamo.PENDIENTE);
        when(reclamoDAO.buscarPorTurno(5L)).thenReturn(reclamo);
        when(reclamoDAO.cambiarEstado(eq(50L), eq(EstadoReclamo.PENDIENTE), eq(EstadoReclamo.ANULADO),
                anyString())).thenReturn(true);

        servicio.anularReclamo(5L);

        verify(reclamoDAO, never()).cambiarEstado(eq(50L), eq(EstadoReclamo.ENVIADO), any(), any());
        // Lo que dejo en la base: procesarReclamo ya no lo presenta.
        reclamo.setEstado(EstadoReclamo.ANULADO);
        when(reclamoDAO.buscar(50L)).thenReturn(reclamo);
        servicio.procesarReclamo(50L, 1);
        verifyNoInteractions(canal);
    }

    @Test
    void unReclamoYaEnviadoPasaARevisionManual() {
        when(reclamoDAO.buscarPorTurno(6L)).thenReturn(reclamo(6L, 60L, EstadoReclamo.ENVIADO));
        when(reclamoDAO.cambiarEstado(eq(60L), eq(EstadoReclamo.PENDIENTE), eq(EstadoReclamo.ANULADO),
                anyString())).thenReturn(false);
        when(reclamoDAO.cambiarEstado(eq(60L), eq(EstadoReclamo.ENVIADO),
                eq(EstadoReclamo.EN_REVISION_MANUAL), contains("cancel"))).thenReturn(true);

        servicio.anularReclamo(6L);

        // El legado no tiene operacion de anulacion: lo resuelve un administrador.
        verify(reclamoDAO).cambiarEstado(eq(60L), eq(EstadoReclamo.ENVIADO),
                eq(EstadoReclamo.EN_REVISION_MANUAL), contains("cancel"));
    }

    @Test
    void siElMDBLoEnvioMientrasSeCancelabaTambienPasaARevisionManual() {
        // Se leyo PENDIENTE, pero el ReclamoMDB confirmo ENVIADO antes del
        // UPDATE de la cancelacion: ese UPDATE no toca nada y cae en el
        // siguiente, en vez de pisar ENVIADO con ANULADO.
        when(reclamoDAO.buscarPorTurno(7L)).thenReturn(reclamo(7L, 70L, EstadoReclamo.PENDIENTE));
        when(reclamoDAO.cambiarEstado(eq(70L), eq(EstadoReclamo.PENDIENTE), eq(EstadoReclamo.ANULADO),
                anyString())).thenReturn(false);
        when(reclamoDAO.cambiarEstado(eq(70L), eq(EstadoReclamo.ENVIADO),
                eq(EstadoReclamo.EN_REVISION_MANUAL), anyString())).thenReturn(true);

        servicio.anularReclamo(7L);

        verify(reclamoDAO).cambiarEstado(eq(70L), eq(EstadoReclamo.ENVIADO),
                eq(EstadoReclamo.EN_REVISION_MANUAL), contains("cancel"));
    }

    @Test
    void unReclamoEnRevisionManualQuedaMarcadoParaNoPresentarlo() {
        Reclamo reclamo = reclamo(8L, 80L, EstadoReclamo.EN_REVISION_MANUAL);
        reclamo.setUltimoError("autorizacion invalida");
        when(reclamoDAO.buscarPorTurno(8L)).thenReturn(reclamo);

        servicio.anularReclamo(8L);

        // Antes no se le dejaba nada y el administrador podia presentarlo
        // para un turno que ya se cancelo. Conserva el error anterior.
        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        verify(reclamoDAO).cambiarEstado(eq(80L), eq(EstadoReclamo.EN_REVISION_MANUAL),
                eq(EstadoReclamo.EN_REVISION_MANUAL), motivo.capture());
        assertTrue(motivo.getValue().contains("no presentar"), motivo.getValue());
        assertTrue(motivo.getValue().contains("autorizacion invalida"), motivo.getValue());
    }

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
    void unTurnoYaCanceladoNoSeFacturaAunqueConserveLaCobertura() {
        // Un TurnoConfirmado tardio o reentregado puede llegar despues de que el
        // paciente cancelo y se le reembolso: cancelar conserva la cobertura.
        Turno turno = turno(5L, true);
        turno.setEstado(EstadoTurno.CANCELADO);
        when(servicioDeTurnos.obtenerTurno(5L)).thenReturn(turno);

        servicio.registrarReclamo(5L);

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
        when(reclamoDAO.marcarEnviado(10L, new BigDecimal("1500.00"), "PRES-9")).thenReturn(true);

        servicio.procesarReclamo(10L, 1);

        verify(reclamoDAO).marcarEnviado(10L, new BigDecimal("1500.00"), "PRES-9");
        verify(reclamoDAO, never()).cambiarEstado(any(), any(), any(), any());
        verify(reclamoDAO, never()).registrarIntentoFallido(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void siElTurnoSeCancelaMientrasSePresentaElReclamoQuedaEnRevisionManual() {
        // SCRUM-128: el MDB leyo PENDIENTE y llamo al legado; mientras tanto el
        // paciente cancelo y el reclamo paso a ANULADO. Antes el MDB lo dejaba
        // ENVIADO para un turno cancelado. La obra social ya lo recibio, asi
        // que no basta con ANULADO: lo tiene que anular un administrador.
        when(reclamoDAO.buscar(16L)).thenReturn(pendiente(16L));
        when(canal.presentarReclamo(16L, 1L, "AUT-1", new BigDecimal("70.00")))
                .thenReturn(new ResultadoReclamo(new BigDecimal("1500.00"), "PRES-16"));
        when(reclamoDAO.marcarEnviado(16L, new BigDecimal("1500.00"), "PRES-16")).thenReturn(false);

        servicio.procesarReclamo(16L, 1);

        verify(reclamoDAO).cambiarEstado(eq(16L), eq(EstadoReclamo.ANULADO),
                eq(EstadoReclamo.EN_REVISION_MANUAL), contains("PRES-16"));
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
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turno.setCoberturaAutorizada(coberturaAutorizada);
        turno.setCoberturaPorcentaje(new BigDecimal("70.00"));
        turno.setNumeroAutorizacion("AUT-1");
        return turno;
    }

    private static Reclamo reclamo(Long turnoId, Long id, EstadoReclamo estado) {
        Reclamo reclamo = new Reclamo(turnoId, 1L, "AUT-" + turnoId, null);
        reclamo.setId(id);
        reclamo.setEstado(estado);
        return reclamo;
    }

    private Reclamo pendiente(Long turnoId) {
        return new Reclamo(turnoId, 1L, "AUT-1", new BigDecimal("70.00"));
    }
}
