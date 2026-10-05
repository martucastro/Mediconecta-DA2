package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.SesionVideo;
import ar.edu.uade.da2.mediconecta.telemedicina.datos.SesionVideoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Patron Facade: unica puerta de entrada al componente de telemedicina.
 *
 * Crea la sala de video de un turno de telemedicina y le entrega a cada
 * participante su propio enlace. Es stateless por el mismo motivo que pagos: la
 * sesion es durable en la fila de SesionVideo, no conversacional en el bean.
 *
 * La llamada al proveedor no vive aca sino detras del Adapter
 * ProveedorDeVideoAdapter. El turno se lee por la fachada de ServicioDeTurnos,
 * nunca por su tabla.
 *
 * Solo PACIENTE y PROFESIONAL: una sala es de las dos personas del turno. El
 * administrador queda afuera a proposito, porque para la sala es un tercero
 * mas y recibiria un enlace que no le corresponde.
 */
@Stateless
@RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL })
public class ServicioDeTelemedicina {

    private static final Logger LOGGER = Logger.getLogger(ServicioDeTelemedicina.class.getName());

    static final String ROL_EN_SALA_PACIENTE = "PACIENTE";
    static final String ROL_EN_SALA_PROFESIONAL = "PROFESIONAL";

    @Inject
    private SesionVideoDAO sesionDAO;

    @Inject
    private ProveedorDeVideoAdapter proveedor;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Resource
    private SessionContext contexto;

    @PostConstruct
    public void inicializar() {
        LOGGER.info("ServicioDeTelemedicina: instancia creada por el contenedor.");
    }

    @PreDestroy
    public void liberar() {
        LOGGER.info("ServicioDeTelemedicina: instancia destruida por el contenedor.");
    }

    /**
     * Crea la sala de video del turno, o devuelve la que ya tiene.
     *
     * Reglas, en este orden:
     * 1. Solo el paciente o el profesional del turno (403 para cualquier otro).
     *    Va primero para que un tercero no pueda averiguar nada del turno por el
     *    mensaje de error.
     * 2. El turno tiene que ser de TELEMEDICINA: uno presencial no lleva sala.
     * 3. El turno tiene que estar tomado por un paciente (EN_HOLD o CONFIRMADO).
     *    EN_HOLD se acepta porque el paso 2/2 crea la sala dentro de
     *    confirmarTurno, antes de que el turno pase a CONFIRMADO.
     * 4. Idempotente: si el turno ya tiene sala para este paciente, se devuelve
     *    esa sin volver a llamar al proveedor. Si la sala era de un paciente
     *    anterior (su hold vencio y otro tomo el turno), se pide una nueva: el
     *    enlace viejo lo conoce alguien que ya no es parte del turno.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public SesionVideo crearSesion(Long turnoId) {
        Turno turno = turnoExistente(turnoId);
        verificarQueEsParticipante(turno, usuarioAutenticado());

        if (turno.getModalidad() != ModalidadTurno.TELEMEDICINA) {
            throw new ConflictoDeNegocioException(
                    "El turno " + turnoId + " es presencial: no lleva sala de video.");
        }
        if (turno.getPaciente() == null || (turno.getEstado() != EstadoTurno.EN_HOLD
                && turno.getEstado() != EstadoTurno.CONFIRMADO)) {
            throw new ConflictoDeNegocioException(
                    "El turno " + turnoId + " no esta reservado por un paciente: esta "
                            + turno.getEstado() + ".");
        }

        Long pacienteId = turno.getPaciente().getId();
        SesionVideo existente = sesionDAO.buscarPorTurno(turnoId);
        if (existente != null && pacienteId.equals(existente.getPacienteId())) {
            return existente;
        }

        SalaDeVideo sala = proveedor.crearSala(turnoId, turno.getFechaHora());
        LOGGER.info(() -> "Sala de video " + sala.salaId() + " creada para el turno " + turnoId);

        if (existente != null) {
            existente.asignarSala(pacienteId, sala.salaId(), sala.enlaceProfesional(),
                    sala.enlacePaciente());
            return sesionDAO.actualizar(existente);
        }
        SesionVideo nueva = new SesionVideo(turnoId, pacienteId, turno.getProfesional().getId(),
                sala.salaId(), sala.enlaceProfesional(), sala.enlacePaciente());
        sesionDAO.guardar(nueva);
        return nueva;
    }

    /**
     * El enlace que le corresponde a quien pregunta, o null si el turno todavia
     * no tiene sala para el.
     *
     * Nunca devuelve los dos enlaces: el profesional recibe el de anfitrion y el
     * paciente el de invitado. Los participantes se resuelven del turno actual,
     * no de la sesion guardada, asi que un paciente cuyo hold vencio deja de ver
     * la sala en cuanto el turno deja de ser suyo.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public EnlaceDeSesion obtenerEnlace(Long turnoId) {
        Turno turno = turnoExistente(turnoId);
        Usuario caller = usuarioAutenticado();
        verificarQueEsParticipante(turno, caller);

        SesionVideo sesion = sesionDAO.buscarPorTurno(turnoId);
        if (sesion == null) {
            return null;
        }
        if (caller.getId().equals(turno.getProfesional().getId())) {
            return new EnlaceDeSesion(turnoId, ROL_EN_SALA_PROFESIONAL,
                    sesion.getEnlaceProfesional(), sesion.getEstado());
        }
        // Es el paciente actual del turno. Si la sala se creo para otro paciente
        // (un hold anterior que vencio), todavia no hay sala para este.
        if (!caller.getId().equals(sesion.getPacienteId())) {
            return null;
        }
        return new EnlaceDeSesion(turnoId, ROL_EN_SALA_PACIENTE, sesion.getEnlacePaciente(),
                sesion.getEstado());
    }

    private Turno turnoExistente(Long turnoId) {
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio.");
        }
        Turno turno = servicioDeTurnos.obtenerTurno(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId + ".");
        }
        return turno;
    }

    /**
     * Solo el paciente y el profesional del turno. @RolesAllowed no alcanza:
     * sabe que el caller es PACIENTE, no que el turno sea suyo. Mismo criterio
     * que ServicioDeTurnos y ServicioDePagos.
     */
    private void verificarQueEsParticipante(Turno turno, Usuario caller) {
        boolean esProfesional = turno.getProfesional() != null
                && caller.getId().equals(turno.getProfesional().getId());
        boolean esPaciente = turno.getPaciente() != null
                && caller.getId().equals(turno.getPaciente().getId());
        if (!esProfesional && !esPaciente) {
            throw new EJBAccessException(
                    "Solo el paciente y el profesional del turno acceden a su sala de video.");
        }
    }

    private Usuario usuarioAutenticado() {
        String email = contexto.getCallerPrincipal().getName();
        Usuario usuario = servicioDeUsuarios.obtenerPorEmail(email);
        if (usuario == null) {
            throw new EJBAccessException("No se pudo resolver el usuario autenticado: " + email);
        }
        return usuario;
    }
}
