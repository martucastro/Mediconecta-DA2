package ar.edu.uade.da2.mediconecta.obrassociales.presentacion;

/**
 * Cuerpo de POST /api/afiliaciones: con qué DNI y número de afiliado conoce la
 * obra social al paciente.
 */
public class RegistroAfiliacionRequest {

    private Long pacienteId;
    private String dni;
    private String numeroAfiliado;

    public RegistroAfiliacionRequest() {
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getNumeroAfiliado() {
        return numeroAfiliado;
    }

    public void setNumeroAfiliado(String numeroAfiliado) {
        this.numeroAfiliado = numeroAfiliado;
    }
}
