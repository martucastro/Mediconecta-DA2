package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.time.LocalDateTime;
import java.util.List;

import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.datos.TurnoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateful;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Componente stateful: mantiene el hold de un turno durante la conversacion
 * reservar -> confirmar/cancelar.
 *
 * La expiracion del hold NO vive aca sino en ExpiradorDeHolds. La especificacion
 * de Jakarta Enterprise Beans no permite crear timers sobre un stateful session
 * bean; ver el javadoc de ese componente para el detalle.
 */
@Stateful
@RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL,
        ServicioDeUsuarios.ROL_ADMINISTRADOR })
public class ServicioDeTurnos {

    @Resource
    private SessionContext contexto;

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private ExpiradorDeHolds expirador;

    // Facade hacia ServicioDeUsuarios: ServicioDeTurnos orquesta la validacion
    // del paciente antes de reservar. Si en el futuro se suman ServicioDePagos
    // o ServicioDeNotificaciones, este es el punto donde se orquestarian.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    private Long turnoEnCursoId;

    @PostConstruct
    public void iniciar() {
        System.out.println("[ServicioDeTurnos] Instancia creada: " + this);
    }

    @PreDestroy
    public void finalizar() {
        System.out.println("[ServicioDeTurnos] Instancia destruida: " + this);
    }

    @PermitAll
    public List<Turno> consultarDisponibilidad(Long profesionalId) {
        return turnoDAO.listarDisponiblesPorProfesional(profesionalId);
    }

    /**
     * Abre una franja disponible en la agenda del profesional autenticado.
     * Sin esto no hay forma de que existan turnos para reservar.
     */
    @RolesAllowed(ServicioDeUsuarios.ROL_PROFESIONAL)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno abrirDisponibilidad(LocalDateTime fechaHora) {
        // La validacion vive aca y no en el recurso REST: es una regla del
        // negocio (una franja sin horario no es una franja), y tiene que valer
        // igual si manana se invoca al componente desde otro canal.
        if (fechaHora == null) {
            throw new DatosInvalidosException("Falta la fecha y hora de la franja");
        }
        if (fechaHora.isBefore(LocalDateTime.now())) {
            throw new DatosInvalidosException("No se puede abrir una franja en el pasado");
        }
        Usuario profesional = usuarioAutenticado();
        Turno turno = new Turno(profesional, fechaHora);
        turnoDAO.guardar(turno);
        return turno;
    }

    /**
     * Retiene el turno a nombre del paciente autenticado durante 5 minutos.
     *
     * El paciente se resuelve del caller principal y no de un parametro: si
     * viniera del cuerpo de la peticion, cualquiera podria reservar a nombre de
     * otro.
     */
    @RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno reservarTurno(Long turnoId) {
        Usuario paciente = usuarioAutenticado();

        Turno turno = turnoDAO.buscarParaActualizar(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId);
        }
        if (turno.getEstado() != EstadoTurno.DISPONIBLE) {
            throw new ConflictoDeNegocioException(
                    "El turno ya no esta disponible: esta " + turno.getEstado());
        }

        turno.setPaciente(paciente);
        turno.setEstado(EstadoTurno.EN_HOLD);
        turno.setInicioHold(LocalDateTime.now());
        turnoDAO.actualizar(turno);

        this.turnoEnCursoId = turnoId;
        expirador.programar(turnoId, ExpiradorDeHolds.DURACION_HOLD_MS);

        return turno;
    }

    @RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno confirmarTurno(Long turnoId) {
        Turno turno = turnoDAO.buscarParaActualizar(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId);
        }
        if (turno.getEstado() != EstadoTurno.EN_HOLD) {
            throw new ConflictoDeNegocioException(
                    "El turno no tiene un hold activo: esta " + turno.getEstado());
        }
        verificarQueElHoldEsDelCaller(turno);

        turno.setEstado(EstadoTurno.CONFIRMADO);
        expirador.cancelar(turnoId);
        return turnoDAO.actualizar(turno);
    }

    @RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno cancelarTurno(Long turnoId) {
        Turno turno = turnoDAO.buscarParaActualizar(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId);
        }
        if (turno.getEstado() == EstadoTurno.DISPONIBLE) {
            throw new ConflictoDeNegocioException(
                    "El turno no esta reservado, no hay nada que cancelar");
        }
        verificarQueElHoldEsDelCaller(turno);

        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setPaciente(null);
        turno.setInicioHold(null);
        expirador.cancelar(turnoId);
        return turnoDAO.actualizar(turno);
    }

    // Expone el estado conversacional que mantiene esta instancia stateful:
    // que turno esta reteniendo esta conversacion en este momento.
    @PermitAll
    public Long getTurnoEnCursoId() {
        return turnoEnCursoId;
    }

    private Usuario usuarioAutenticado() {
        String email = contexto.getCallerPrincipal().getName();
        Usuario usuario = servicioDeUsuarios.obtenerPorEmail(email);
        if (usuario == null) {
            throw new EJBAccessException("No se pudo resolver el usuario autenticado: " + email);
        }
        return usuario;
    }

    /**
     * Un paciente solo puede confirmar o cancelar el turno que el mismo retuvo.
     *
     * No se puede expresar con @RolesAllowed porque esa anotacion no ve los
     * argumentos del metodo: sabe que el caller es PACIENTE, no que este turno
     * sea suyo. Mismo criterio que usa ServicioDeHistoriaClinica.
     */
    private void verificarQueElHoldEsDelCaller(Turno turno) {
        if (contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)) {
            return;
        }
        Usuario caller = usuarioAutenticado();
        if (turno.getPaciente() == null || !caller.getId().equals(turno.getPaciente().getId())) {
            throw new EJBAccessException("El turno fue reservado por otro paciente.");
        }
    }
}
