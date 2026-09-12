package ar.edu.uade.da2.mediconecta.historiaclinica;

import java.util.List;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.usuarios.ServicioDeUsuarios;
import ar.edu.uade.da2.mediconecta.usuarios.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Patrón Facade: única puerta de entrada al componente de historia clínica.
 *
 * Es stateless porque cada operación es autocontenida — no hay conversación con
 * el cliente que haya que recordar entre llamadas. El componente que sí va a ser
 * stateful es ServicioDeTurnos, que mantiene el hold del turno mientras el
 * paciente confirma.
 */
@Stateless
public class ServicioDeHistoriaClinica implements ServicioDeHistoriaClinicaLocal {

    private static final Logger LOGGER =
            Logger.getLogger(ServicioDeHistoriaClinica.class.getName());

    private static final String ROL_PACIENTE = "PACIENTE";
    private static final String ROL_PROFESIONAL = "PROFESIONAL";

    @Inject
    private HistoriaClinicaDAO historiaDAO;

    @Inject
    private EntradaClinicaDAO entradaDAO;

    @Inject
    private EntradaClinicaFactory factory;

    // Dependencia hacia otro componente, siempre a través de su fachada:
    // nunca se consulta la tabla de usuarios directamente desde acá.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @PostConstruct
    public void inicializar() {
        LOGGER.info("ServicioDeHistoriaClinica: instancia creada por el contenedor.");
    }

    @PreDestroy
    public void liberar() {
        LOGGER.info("ServicioDeHistoriaClinica: instancia destruida por el contenedor.");
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public HistoriaClinica crearHistoria(Long pacienteId) {
        validarPaciente(pacienteId);

        if (historiaDAO.buscarPorPaciente(pacienteId) != null) {
            throw new ConflictoDeNegocioException(
                    "El paciente " + pacienteId + " ya tiene una historia clínica abierta.");
        }

        HistoriaClinica historia = new HistoriaClinica(pacienteId);
        historiaDAO.guardar(historia);
        return historia;
    }

    @Override
    @RolesAllowed({"PROFESIONAL", "PACIENTE", "ADMINISTRADOR"})
    public HistoriaClinica obtenerHistoriaDePaciente(Long pacienteId) {
        if (pacienteId == null) {
            throw new DatosInvalidosException("El id del paciente es obligatorio.");
        }
        return historiaDAO.buscarPorPaciente(pacienteId);
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public EntradaClinica agregarEntrada(Long pacienteId, NuevaEntradaDTO datos) {
        if (datos == null) {
            throw new DatosInvalidosException("Los datos de la entrada son obligatorios.");
        }
        validarPaciente(pacienteId);
        validarProfesional(datos.getProfesionalId());

        HistoriaClinica historia = obtenerOCrearHistoria(pacienteId);

        EntradaClinica entrada = factory.crear(datos, historia, datos.getProfesionalId());
        entradaDAO.guardar(entrada);
        historia.agregarEntrada(entrada);
        return entrada;
    }

    /**
     * Flujo crítico de varios pasos con transacción declarativa.
     *
     * Los pasos —abrir la historia si hace falta, persistir el diagnóstico y
     * persistir cada receta— ocurren dentro de la misma transacción que demarca
     * el contenedor. Si el factory rechaza la tercera receta, el rollback deshace
     * también el diagnóstico y las dos recetas anteriores: la historia nunca
     * queda con una consulta a medio registrar.
     */
    @Override
    @RolesAllowed("PROFESIONAL")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public HistoriaClinica registrarConsulta(Long pacienteId, RegistrarConsultaDTO datos) {
        if (datos == null) {
            throw new DatosInvalidosException("Los datos de la consulta son obligatorios.");
        }
        if (datos.getDiagnostico() == null) {
            throw new DatosInvalidosException("La consulta debe incluir un diagnóstico.");
        }
        validarPaciente(pacienteId);
        validarProfesional(datos.getProfesionalId());

        HistoriaClinica historia = obtenerOCrearHistoria(pacienteId);

        datos.getDiagnostico().setTipo(TipoEntrada.DIAGNOSTICO);
        EntradaClinica diagnostico =
                factory.crear(datos.getDiagnostico(), historia, datos.getProfesionalId());
        entradaDAO.guardar(diagnostico);
        historia.agregarEntrada(diagnostico);

        if (datos.getRecetas() != null) {
            for (NuevaEntradaDTO datosReceta : datos.getRecetas()) {
                datosReceta.setTipo(TipoEntrada.RECETA);
                EntradaClinica receta =
                        factory.crear(datosReceta, historia, datos.getProfesionalId());
                entradaDAO.guardar(receta);
                historia.agregarEntrada(receta);
            }
        }

        return historia;
    }

    @Override
    @RolesAllowed({"PROFESIONAL", "PACIENTE", "ADMINISTRADOR"})
    public List<EntradaClinica> listarEntradas(Long pacienteId) {
        HistoriaClinica historia = obtenerHistoriaDePaciente(pacienteId);
        if (historia == null) {
            return List.of();
        }
        return entradaDAO.listarPorHistoria(historia.getId());
    }

    private HistoriaClinica obtenerOCrearHistoria(Long pacienteId) {
        HistoriaClinica historia = historiaDAO.buscarPorPaciente(pacienteId);
        if (historia == null) {
            historia = new HistoriaClinica(pacienteId);
            historiaDAO.guardar(historia);
        }
        return historia;
    }

    private void validarPaciente(Long pacienteId) {
        Usuario paciente = validarUsuario(pacienteId, "paciente");
        if (!ROL_PACIENTE.equals(paciente.getRol())) {
            throw new DatosInvalidosException(
                    "El usuario " + pacienteId + " no tiene rol " + ROL_PACIENTE + ".");
        }
    }

    private void validarProfesional(Long profesionalId) {
        Usuario profesional = validarUsuario(profesionalId, "profesional");
        if (!ROL_PROFESIONAL.equals(profesional.getRol())) {
            throw new DatosInvalidosException(
                    "El usuario " + profesionalId + " no tiene rol " + ROL_PROFESIONAL + ".");
        }
    }

    private Usuario validarUsuario(Long id, String descripcion) {
        if (id == null) {
            throw new DatosInvalidosException("El id del " + descripcion + " es obligatorio.");
        }
        Usuario usuario = servicioDeUsuarios.obtenerUsuario(id);
        if (usuario == null) {
            throw new DatosInvalidosException("No existe un usuario con id " + id + ".");
        }
        return usuario;
    }
}
