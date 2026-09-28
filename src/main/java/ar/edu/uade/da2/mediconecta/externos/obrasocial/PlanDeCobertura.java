package ar.edu.uade.da2.mediconecta.externos.obrasocial;

/**
 * Planes del sistema legado. El porcentaje de cobertura depende del plan, no
 * del afiliado: dos afiliados del mismo plan reciben siempre la misma respuesta.
 */
public enum PlanDeCobertura {

    PLAN_ALTO(100),
    PLAN_MEDIO(70),
    PLAN_BASICO(40),
    SIN_COBERTURA(0);

    private final int porcentaje;

    PlanDeCobertura(int porcentaje) {
        this.porcentaje = porcentaje;
    }

    public int getPorcentaje() {
        return porcentaje;
    }
}
