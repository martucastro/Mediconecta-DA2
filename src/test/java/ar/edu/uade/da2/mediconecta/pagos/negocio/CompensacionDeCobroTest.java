package ar.edu.uade.da2.mediconecta.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.transaction.Transactional;

/**
 * Compensacion del cobro huerfano: la pasarela aprobo el copago, pero la
 * transaccion de la confirmacion no llego a confirmarse (fallo la sala, el
 * envio al topico o el commit). El rollback no alcanza a la pasarela, asi que
 * el reembolso lo pide CompensacionDeCobro y deja el resultado en la base.
 */
@ExtendWith(MockitoExtension.class)
class CompensacionDeCobroTest {

    private static final CobroAprobado COBRO = new CobroAprobado(30L, 7L, "tx-30");

    @Mock
    private PasarelaDePagoAdapter pasarela;
    @Mock
    private PagoDAO pagoDAO;

    @InjectMocks
    private CompensacionDeCobro compensacion;

    @Test
    void siLaConfirmacionFallaDespuesDelCobroElCobroSeReembolsa() {
        when(pasarela.reembolsar("tx-30"))
                .thenReturn(new ResultadoPasarela(EstadoPago.REEMBOLSADO, "tx-30"));

        compensacion.alFallarLaTransaccion(COBRO);

        verify(pagoDAO).registrarCompensacion(30L, EstadoPago.REEMBOLSADO, "tx-30");
    }

    @Test
    void siElReembolsoFallaElPagoQuedaConLaCompensacionPendiente() {
        when(pasarela.reembolsar("tx-30")).thenThrow(new PasarelaNoDisponibleException("caida"));

        compensacion.alFallarLaTransaccion(COBRO);

        // El dinero sigue cobrado: queda a la vista del administrador, con el
        // id de transaccion para que pueda reembolsarlo.
        verify(pagoDAO).registrarCompensacion(30L, EstadoPago.COMPENSACION_PENDIENTE, "tx-30");
    }

    @Test
    void siLaPasarelaNoConfirmaElReembolsoTambienQuedaPendiente() {
        when(pasarela.reembolsar("tx-30")).thenReturn(new ResultadoPasarela(EstadoPago.RECHAZADO, null));

        compensacion.alFallarLaTransaccion(COBRO);

        verify(pagoDAO).registrarCompensacion(30L, EstadoPago.COMPENSACION_PENDIENTE, "tx-30");
    }

    @Test
    void unaFallaAlRegistrarElResultadoNoSalePorElObservador() {
        // Corre despues del rollback, sin nadie arriba que pueda atrapar nada:
        // si la base tampoco responde, solo queda el log.
        when(pasarela.reembolsar("tx-30"))
                .thenReturn(new ResultadoPasarela(EstadoPago.REEMBOLSADO, "tx-30"));
        doThrow(new IllegalStateException("base caida"))
                .when(pagoDAO).registrarCompensacion(any(), any(), any());

        compensacion.alFallarLaTransaccion(COBRO);
    }

    @Test
    void soloCorreCuandoLaTransaccionFallaYFueraDeElla() throws Exception {
        Method metodo = CompensacionDeCobro.class.getMethod("alFallarLaTransaccion", CobroAprobado.class);

        // AFTER_FAILURE: con la confirmacion exitosa el contenedor no lo
        // invoca, asi que el camino feliz no reembolsa nada.
        assertEquals(TransactionPhase.AFTER_FAILURE,
                metodo.getParameters()[0].getAnnotation(Observes.class).during());
        // La transaccion que fallo ya termino: el resultado se guarda en una
        // propia (PagoDAO.registrarCompensacion, REQUIRES_NEW).
        assertNull(metodo.getAnnotation(Transactional.class));
    }
}
