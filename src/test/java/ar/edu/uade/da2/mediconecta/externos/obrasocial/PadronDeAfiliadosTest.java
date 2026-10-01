package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Los datos de prueba del legado simulado. Son deterministas a proposito: la
 * demo y el smoke test dependen de que cada afiliado de siempre la misma
 * respuesta. No se prueba OS-5005, el afiliado lento, porque duerme 30 s.
 */
class PadronDeAfiliadosTest {

    // dni, afiliado, prestacion, plan, porcentaje, arancel, copago, autorizado
    @ParameterizedTest
    @CsvSource({
            "30111222, OS-1001, CONSULTA,     PLAN_ALTO,     100, 20000.00,     0.00, true",
            "30333444, OS-2002, CONSULTA,     PLAN_MEDIO,     70, 20000.00,  6000.00, true",
            "30444555, OS-3003, CONSULTA,     PLAN_BASICO,    40, 20000.00, 12000.00, true",
            "30555666, OS-4004, CONSULTA,     SIN_COBERTURA,   0, 20000.00, 20000.00, false",
            "30111222, OS-1001, TELECONSULTA, PLAN_ALTO,     100, 15000.00,     0.00, true",
            "30333444, OS-2002, TELECONSULTA, PLAN_MEDIO,     70, 15000.00,  4500.00, true",
            "30444555, OS-3003, TELECONSULTA, PLAN_BASICO,    40, 15000.00,  9000.00, true",
            "30555666, OS-4004, TELECONSULTA, SIN_COBERTURA,   0, 15000.00, 15000.00, false"
    })
    void calculaElCopagoSegunElPlanYLaPrestacion(String dni, String afiliado, String prestacion,
            String plan, int porcentaje, String arancel, String copago, boolean autorizado)
            throws PedidoInvalidoException {
        RespuestaCobertura respuesta = PadronDeAfiliados.evaluar(dni, afiliado, prestacion);

        assertEquals(plan, respuesta.getPlan());
        assertEquals(porcentaje, respuesta.getPorcentajeCobertura());
        assertEquals(new BigDecimal(arancel), respuesta.getArancel());
        // equals y no compareTo: el copago sale siempre con dos decimales.
        assertEquals(new BigDecimal(copago), respuesta.getCopago());
        assertEquals(autorizado, respuesta.isAutorizado());
    }

    @Test
    void elCopagoMasLaPartedeLaObraSocialSumaElArancel() throws PedidoInvalidoException {
        RespuestaCobertura respuesta = PadronDeAfiliados.evaluar("30333444", "OS-2002", "CONSULTA");

        BigDecimal cubierto = respuesta.getArancel()
                .multiply(BigDecimal.valueOf(respuesta.getPorcentajeCobertura()))
                .divide(new BigDecimal("100"));
        assertEquals(0, respuesta.getArancel().compareTo(cubierto.add(respuesta.getCopago())));
    }

    @Test
    void elPadronNuncaEmiteElNumeroDeAutorizacion() throws PedidoInvalidoException {
        // Lo agrega el endpoint, y solo al autorizar.
        assertNull(PadronDeAfiliados.evaluar("30333444", "OS-2002", "CONSULTA").getNumeroAutorizacion());
    }

    @Test
    void unAfiliadoInexistenteEsUnPedidoInvalido() {
        PedidoInvalidoException e = assertThrows(PedidoInvalidoException.class,
                () -> PadronDeAfiliados.evaluar("1", "OS-9999", "CONSULTA"));

        assertTrue(e.getMessage().contains("OS-9999"));
    }

    @Test
    void unAfiliadoNuloEsUnPedidoInvalido() {
        assertThrows(PedidoInvalidoException.class,
                () -> PadronDeAfiliados.evaluar("30333444", null, "CONSULTA"));
    }

    @Test
    void unDniQueNoCorrespondeAlAfiliadoEsUnPedidoInvalido() {
        PedidoInvalidoException e = assertThrows(PedidoInvalidoException.class,
                () -> PadronDeAfiliados.evaluar("30111222", "OS-2002", "CONSULTA"));

        assertTrue(e.getMessage().contains("30111222"));
    }

    @Test
    void unaPrestacionDesconocidaEsUnPedidoInvalido() {
        PedidoInvalidoException e = assertThrows(PedidoInvalidoException.class,
                () -> PadronDeAfiliados.evaluar("30333444", "OS-2002", "CIRUGIA"));

        assertTrue(e.getMessage().contains("CIRUGIA"));
    }

    @Test
    void unaPrestacionNulaEsUnPedidoInvalido() {
        assertThrows(PedidoInvalidoException.class,
                () -> PadronDeAfiliados.evaluar("30333444", "OS-2002", null));
    }
}
