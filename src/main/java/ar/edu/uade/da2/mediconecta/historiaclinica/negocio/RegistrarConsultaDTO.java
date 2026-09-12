package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import java.util.List;

/**
 * Cierre de una consulta: un diagnóstico y las recetas que se emiten con él.
 * Todo se persiste dentro de una única transacción.
 */
public class RegistrarConsultaDTO {

    private Long profesionalId;
    private NuevaEntradaDTO diagnostico;
    private List<NuevaEntradaDTO> recetas;

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public NuevaEntradaDTO getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(NuevaEntradaDTO diagnostico) {
        this.diagnostico = diagnostico;
    }

    public List<NuevaEntradaDTO> getRecetas() {
        return recetas;
    }

    public void setRecetas(List<NuevaEntradaDTO> recetas) {
        this.recetas = recetas;
    }
}
