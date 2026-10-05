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

    /**
     * Crea la sala de video del turno, o devuelve la que ya tiene.
     *
     * Reglas, en este orden:
     * 1. Solo el paciente o el profesional del turno (403 para cualquier otro, y
     *    tambien si el turno no existe). Va primero para que un tercero no pueda
     *    averiguar nada del turno, ni siquiera si existe.
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
    public SesionCreada crearSesion(Long turnoId) {
        Turno turno = turnoDelCaller(turnoId, usuarioAutenticado());

        if (turno.getModalidad() != ModalidadTurno.TELEMEDICINA) {
            throw new ConflictoDeNegocioException(
                    "El turno " + turnoId + " es presencial: no lleva sala de video.");
        }
        if (!estaTomadoPorUnPaciente(turno)) {
            throw new ConflictoDeNegocioException(
                    "El turno " + turnoId + " no esta reservado por un paciente: esta "
                            + turno.getEstado() + ".");
        }

        Long pacienteId = turno.getPaciente().getId();
        SesionVideo existente = sesionDAO.buscarPorTurno(turnoId);
        if (existente != null && pacienteId.equals(existente.getPacienteId())) {
            return new SesionCreada(existente, false);
        }

        SalaDeVideo sala = proveedor.crearSala(turnoId, turno.getFechaHora());
        LOGGER.info(() -> "Sala de video " + sala.salaId() + " creada para el turno " + turnoId);

        if (existente != null) {
            existente.asignarSala(pacienteId, sala.salaId(), sala.enlaceProfesional(),
                    sala.enlacePaciente());
            return new SesionCreada(sesionDAO.actualizar(existente), true);
        }
        SesionVideo nueva = new SesionVideo(turnoId, pacienteId, turno.getProfesional().getId(),
                sala.salaId(), sala.enlaceProfesional(), sala.enlacePaciente());
        sesionDAO.guardar(nueva);
        return new SesionCreada(nueva, true);
    }

    /**
     * El enlace que le corresponde a quien pregunta, o null si el turno no tiene
     * una sala vigente.
     *
     * Nunca devuelve los dos enlaces: el profesional recibe el de anfitrion y el
     * paciente el de invitado.
     *
     * Una sala es vigente solo si el turno sigue tomado (EN_HOLD o CONFIRMADO) y
     * por el mismo paciente para el que se creo. La regla vale para los dos
     * roles: despues de una cancelacion, de un hold vencido o de que otro
     * paciente tome el turno, ni el profesional ni nadie recibe el enlace viejo.
     * Los participantes se resuelven del turno actual, no de la sesion guardada,
     * asi que el paciente anterior pasa a recibir 403.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public EnlaceDeSesion obtenerEnlace(Long turnoId) {
        Usuario caller = usuarioAutenticado();
        Turno turno = turnoDelCaller(turnoId, caller);

        SesionVideo sesion = sesionDAO.buscarPorTurno(turnoId);
        if (sesion == null || !esVigente(sesion, turno)) {
            return null;
        }
        if (caller.getId().equals(turno.getProfesional().getId())) {
            return new EnlaceDeSesion(turnoId, ROL_EN_SALA_PROFESIONAL,
                    sesion.getEnlaceProfesional(), sesion.getEstado());
        }
        return new EnlaceDeSesion(turnoId, ROL_EN_SALA_PACIENTE, sesion.getEnlacePaciente(),
                sesion.getEstado());
    }

    private static boolean estaTomadoPorUnPaciente(Turno turno) {
        return turno.getPaciente() != null && (turno.getEstado() == EstadoTurno.EN_HOLD
                || turno.getEstado() == EstadoTurno.CONFIRMADO);
    }

    private static boolean esVigente(SesionVideo sesion, Turno turno) {
        return estaTomadoPorUnPaciente(turno)
                && turno.getPaciente().getId().equals(sesion.getPacienteId());
    }

    /**
     * El turno, si el caller es su paciente o su profesional.
     *
     * Un turno inexistente responde igual que uno ajeno (403): si respondiera
     * distinto, cualquier usuario podria recorrer ids y averiguar cuales
     * existen. @RolesAllowed no alcanza para esta regla porque sabe que el
     * caller es PACIENTE, no que el turno sea suyo; mismo criterio que
     * ServicioDeTurnos y ServicioDePagos.
     */
    private Turno turnoDelCaller(Long turnoId, Usuario caller) {
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio.");
        }
        Turno turno = servicioDeTurnos.obtenerTurno(turnoId);
        boolean esProfesional = turno != null && turno.getProfesional() != null
                && caller.getId().equals(turno.getProfesional().getId());
        boolean esPaciente = turno != null && turno.getPaciente() != null
                && caller.getId().equals(turno.getPaciente().getId());
        if (!esProfesional && !esPaciente) {
            throw new EJBAccessException(
                    "Solo el paciente y el profesional del turno acceden a su sala de video.");
        }
        return turno;
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
