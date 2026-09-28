package ar.edu.uade.da2.mediconecta.turnos.datos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;

@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "paciente_id")
    private Usuario paciente;

    @ManyToOne
    @JoinColumn(name = "profesional_id")
    private Usuario profesional;

    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    private EstadoTurno estado;

    private LocalDateTime inicioHold;

    /**
     * Default PRESENCIAL en los dos niveles, y los dos hacen falta:
     *
     * - En Java, para que un Turno nuevo nunca quede sin modalidad.
     * - En la base (columnDefinition), porque hbm2ddl.auto=update agrega la
     *   columna con un ALTER TABLE sobre una tabla que ya tiene filas. Un NOT
     *   NULL sin DEFAULT haria fallar ese ALTER en PostgreSQL; con el DEFAULT,
     *   el motor completa los turnos existentes como PRESENCIAL al agregarla y
     *   no hace falta ninguna migracion manual.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) not null default 'PRESENCIAL'")
    private ModalidadTurno modalidad = ModalidadTurno.PRESENCIAL;

    // Solo tiene sentido en los turnos presenciales (ver abrirDisponibilidad).
    @Column(length = 60)
    private String consultorio;

    // Cobertura de la obra social. Quedan en null hasta que la complete
    // ServicioDeObrasSociales (SCRUM-91) desde el punto de extension de la
    // reserva; null significa "todavia no se consulto", no "sin cobertura".
    private Boolean coberturaAutorizada;

    @Column(precision = 5, scale = 2)
    private BigDecimal coberturaPorcentaje;

    @Column(precision = 12, scale = 2)
    private BigDecimal copago;

    @Column(length = 40)
    private String numeroAutorizacion;

    // Constructor vacío (obligatorio para JPA)
    public Turno() {
    }

    public Turno(Usuario profesional, LocalDateTime fechaHora) {
        this(profesional, fechaHora, ModalidadTurno.PRESENCIAL, null);
    }

    public Turno(Usuario profesional, LocalDateTime fechaHora, ModalidadTurno modalidad,
            String consultorio) {
        this.profesional = profesional;
        this.fechaHora = fechaHora;
        this.modalidad = modalidad;
        this.consultorio = consultorio;
        this.estado = EstadoTurno.DISPONIBLE;
    }

    // Suelta al paciente junto con todo lo que era suyo: la cobertura, el copago
    // y la autorizacion. Si quedaran, el proximo paciente los heredaria y el
    // listado publico de disponibilidad los mostraria. El estado lo decide quien
    // llama (DISPONIBLE al vencer el hold, CANCELADO al cancelar).
    public void liberar() {
        this.paciente = null;
        this.inicioHold = null;
        this.coberturaAutorizada = null;
        this.coberturaPorcentaje = null;
        this.copago = null;
        this.numeroAutorizacion = null;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getPaciente() {
        return paciente;
    }

    public void setPaciente(Usuario paciente) {
        this.paciente = paciente;
    }

    public Usuario getProfesional() {
        return profesional;
    }

    public void setProfesional(Usuario profesional) {
        this.profesional = profesional;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public LocalDateTime getInicioHold() {
        return inicioHold;
    }

    public void setInicioHold(LocalDateTime inicioHold) {
        this.inicioHold = inicioHold;
    }

    public ModalidadTurno getModalidad() {
        return modalidad;
    }

    public void setModalidad(ModalidadTurno modalidad) {
        this.modalidad = modalidad;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(String consultorio) {
        this.consultorio = consultorio;
    }

    public Boolean getCoberturaAutorizada() {
        return coberturaAutorizada;
    }

    public void setCoberturaAutorizada(Boolean coberturaAutorizada) {
        this.coberturaAutorizada = coberturaAutorizada;
    }

    public BigDecimal getCoberturaPorcentaje() {
        return coberturaPorcentaje;
    }

    public void setCoberturaPorcentaje(BigDecimal coberturaPorcentaje) {
        this.coberturaPorcentaje = coberturaPorcentaje;
    }

    public BigDecimal getCopago() {
        return copago;
    }

    public void setCopago(BigDecimal copago) {
        this.copago = copago;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }
}
