package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Datos de prueba del sistema legado, fijos y en memoria. No hay base de datos a
 * propósito: la persistencia del legado es opaca para MediConecta, lo único que
 * vemos de él es su contrato SOAP.
 *
 * Hay un afiliado por plan, para poder mostrar en la demo cada respuesta posible.
 */
class PadronDeAfiliados {

    private record Afiliado(String dni, PlanDeCobertura plan) {
    }

    // Clave: número de afiliado
    private static final Map<String, Afiliado> AFILIADOS = Map.of(
            "OS-1001", new Afiliado("30111222", PlanDeCobertura.PLAN_ALTO),
            "OS-2002", new Afiliado("30333444", PlanDeCobertura.PLAN_MEDIO),
            "OS-3003", new Afiliado("30444555", PlanDeCobertura.PLAN_BASICO),
            "OS-4004", new Afiliado("30555666", PlanDeCobertura.SIN_COBERTURA),
            "OS-5005", new Afiliado("30777888", PlanDeCobertura.PLAN_ALTO));

    /**
     * Afiliado con el que el legado tarda en contestar más que cualquier timeout
     * razonable. Sirve para mostrar qué hace MediConecta cuando el legado no
     * responde, sin tener que bajar nada.
     */
    static final String AFILIADO_LENTO = "OS-5005";
    static final long DEMORA_AFILIADO_LENTO_MS = 30_000;

    // Clave: código de prestación
    private static final Map<String, BigDecimal> ARANCELES = Map.of(
            "CONSULTA", new BigDecimal("20000.00"),
            "TELECONSULTA", new BigDecimal("15000.00"));

    private static final BigDecimal CIEN = new BigDecimal("100");

    static RespuestaCobertura evaluar(String dni, String numeroAfiliado, String codigoPrestacion)
            throws PedidoInvalidoException {
        if (AFILIADO_LENTO.equals(numeroAfiliado)) {
            demorar();
        }
        Afiliado afiliado = numeroAfiliado == null ? null : AFILIADOS.get(numeroAfiliado);
        if (afiliado == null) {
            throw new PedidoInvalidoException("No existe el afiliado " + numeroAfiliado);
        }
        if (!afiliado.dni().equals(dni)) {
            throw new PedidoInvalidoException("El DNI " + dni + " no corresponde al afiliado " + numeroAfiliado);
        }
        BigDecimal arancel = codigoPrestacion == null ? null : ARANCELES.get(codigoPrestacion);
        if (arancel == null) {
            throw new PedidoInvalidoException("No existe la prestación " + codigoPrestacion);
        }

        PlanDeCobertura plan = afiliado.plan();
        BigDecimal copago = arancel
                .multiply(BigDecimal.valueOf(100 - plan.getPorcentaje()))
                .divide(CIEN, 2, RoundingMode.HALF_UP);

        RespuestaCobertura respuesta = new RespuestaCobertura();
        respuesta.setAutorizado(plan.getPorcentaje() > 0);
        respuesta.setPlan(plan.name());
        respuesta.setPorcentajeCobertura(plan.getPorcentaje());
        respuesta.setArancel(arancel);
        respuesta.setCopago(copago);
        respuesta.setMensaje(respuesta.isAutorizado()
                ? "Prestación cubierta al " + plan.getPorcentaje() + "%"
                : "El plan del afiliado no cubre la prestación");
        return respuesta;
    }

    /**
     * Resuelve lo que presentarReclamo necesita: valida el afiliado y el DNI
     * igual que evaluar, y además que numeroAutorizacion corresponda a ese
     * afiliado (formato AUT-&lt;afiliado&gt;-&lt;prestación&gt;, el mismo que
     * emite autorizarPrestacion) y a una prestación con arancel conocido.
     * montoReconocido es lo que paga la obra social por esa prestación:
     * arancel x porcentaje del plan, el complemento del copago.
     */
    static RespuestaReclamo presentarReclamo(String dni, String numeroAfiliado, String numeroAutorizacion)
            throws PedidoInvalidoException {
        Afiliado afiliado = numeroAfiliado == null ? null : AFILIADOS.get(numeroAfiliado);
        if (afiliado == null) {
            throw new PedidoInvalidoException("No existe el afiliado " + numeroAfiliado);
        }
        if (!afiliado.dni().equals(dni)) {
            throw new PedidoInvalidoException("El DNI " + dni + " no corresponde al afiliado " + numeroAfiliado);
        }
        String codigoPrestacion = codigoPrestacionAutorizada(numeroAfiliado, numeroAutorizacion);
        BigDecimal arancel = codigoPrestacion == null ? null : ARANCELES.get(codigoPrestacion);
        if (arancel == null) {
            throw new PedidoInvalidoException(
                    "La autorización " + numeroAutorizacion + " no corresponde al afiliado " + numeroAfiliado);
        }

        BigDecimal montoReconocido = arancel
                .multiply(BigDecimal.valueOf(afiliado.plan().getPorcentaje()))
                .divide(CIEN, 2, RoundingMode.HALF_UP);

        RespuestaReclamo respuesta = new RespuestaReclamo();
        respuesta.setMontoReconocido(montoReconocido);
        respuesta.setNumeroPresentacion("PRES-" + numeroAutorizacion);
        return respuesta;
    }

    /** Extrae la prestación de una autorización con el formato esperado, o null si no corresponde al afiliado. */
    private static String codigoPrestacionAutorizada(String numeroAfiliado, String numeroAutorizacion) {
        String prefijo = "AUT-" + numeroAfiliado + "-";
        if (numeroAutorizacion == null || !numeroAutorizacion.startsWith(prefijo)) {
            return null;
        }
        return numeroAutorizacion.substring(prefijo.length());
    }

    private static void demorar() {
        try {
            Thread.sleep(DEMORA_AFILIADO_LENTO_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
