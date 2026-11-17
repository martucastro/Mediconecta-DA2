package ar.edu.uade.da2.mediconecta.obrassociales.presentacion;

import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;

/**
 * Con qué datos conoce la obra social al paciente. No expone el id interno de
 * la fila.
 */
public class AfiliacionDTO {

    private Long pacienteId;
    private String dni;
    private String numeroAfiliado;

    public AfiliacionDTO() {
    }

    public AfiliacionDTO(AfiliacionDePaciente afiliacion) {
        this.pacienteId = afiliacion.getPacienteId();
        this.dni = afiliacion.getDni();
        this.numeroAfiliado = afiliacion.getNumeroAfiliado();
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getDni() {
        return dni;
    }

    public String getNumeroAfiliado() {
        return numeroAfiliado;
    }
}
