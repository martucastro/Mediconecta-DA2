package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.time.LocalDateTime;
import java.util.List;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.turnos.datos.EstadoTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
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
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.MapMessage;
import jakarta.jms.Topic;

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
    // del paciente antes de reservar.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    // Puntos de extension: obras sociales, pagos, telemedicina y el reclamo se
    // enganchan observando estos eventos, no con una dependencia desde aca.
    // Ver PuntosDeExtension para el orden y el porque.
    @Inject
    private Event<TurnoEnReserva> eventoReserva;

    @Inject
    private Event<TurnoEnConfirmacion> eventoConfirmacion;

    // JMSContext inyectado por el contenedor: participa de la misma transaccion
    // JTA que confirmarTurno (ver publicarTurnoConfirmado). No conoce el nombre
    // de ningun consumidor: publica en un topico y listo.
    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/topic/TurnoConfirmado")
    private Topic topicoTurnoConfirmado;

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
     * Lectura de un turno por id, a traves de la fachada.
     *
     * La expone para que otros componentes (por ejemplo ServicioDePagos) puedan
     * resolver quien es el paciente y el profesional de un turno sin tocar el
     * TurnoDAO: la regla del sistema es que los componentes se hablan por sus
     * fachadas, nunca por la base del otro. Hereda el @RolesAllowed de clase.
     */
    public Turno obtenerTurno(Long turnoId) {
        return turnoDAO.buscarPorId(turnoId);
    }

    /**
     * Abre una franja disponible en la agenda del profesional autenticado.
     * Sin esto no hay forma de que existan turnos para reservar.
     *
     * modalidad null equivale a PRESENCIAL. El consultorio solo aplica a las
     * franjas presenciales: un turno de telemedicina con consultorio le diria al
     * paciente que se presente en un lugar al que no tiene que ir.
     */
    @RolesAllowed(ServicioDeUsuarios.ROL_PROFESIONAL)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno abrirDisponibilidad(LocalDateTime fechaHora, ModalidadTurno modalidad,
            String consultorio) {
        // La validacion vive aca y no en el recurso REST: es una regla del
        // negocio (una franja sin horario no es una franja), y tiene que valer
        // igual si manana se invoca al componente desde otro canal.
        if (fechaHora == null) {
            throw new DatosInvalidosException("Falta la fecha y hora de la franja");
        }
        if (fechaHora.isBefore(LocalDateTime.now())) {
            throw new DatosInvalidosException("No se puede abrir una franja en el pasado");
        }
        ModalidadTurno modalidadEfectiva = modalidad != null ? modalidad : ModalidadTurno.PRESENCIAL;
        String consultorioEfectivo = consultorio != null && !consultorio.isBlank()
                ? consultorio.trim() : null;
        if (modalidadEfectiva == ModalidadTurno.TELEMEDICINA && consultorioEfectivo != null) {
            throw new DatosInvalidosException("Un turno de telemedicina no lleva consultorio");
        }
        if (consultorioEfectivo != null && consultorioEfectivo.length() > 60) {
            throw new DatosInvalidosException("El consultorio admite hasta 60 caracteres");
        }
        Usuario profesional = usuarioAutenticado();
        Turno turno = new Turno(profesional, fechaHora, modalidadEfectiva, consultorioEfectivo);
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
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio");
        }
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

        // PUNTO DE EXTENSION (reserva). Observadores previstos:
        //   COBERTURA (SCRUM-91, CoberturaEnLaReserva): consulta la cobertura y
        //   completa los campos de cobertura y copago del turno; si la obra
        //   social no responde, la reserva falla entera.
        // Va despues de asignar el paciente (la cobertura es suya) y antes de
        // retenerlo: si la cobertura rechaza, no queda ningun hold que liberar.
        eventoReserva.fire(new TurnoEnReserva(turno));

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
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio");
        }
        Turno turno = turnoDAO.buscarParaActualizar(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId);
        }
        if (turno.getEstado() != EstadoTurno.EN_HOLD) {
            throw new ConflictoDeNegocioException(
                    "El turno no tiene un hold activo: esta " + turno.getEstado());
        }
        verificarQueElHoldEsDelCaller(turno);

        // PUNTO DE EXTENSION (confirmacion). Observadores, en este orden:
        //   1. COBRO_COPAGO (SCRUM-93): cobra el copago. Pendiente.
        //   2. SALA_DE_VIDEO (SCRUM-95): crea la sala si es TELEMEDICINA. Pendiente.
        //   3. RECLAMO: encola el reclamo a la obra social. Pendiente.
        // Se dispara antes de tocar el estado: si cualquiera falla, la
        // excepcion revierte la transaccion, el turno sigue EN_HOLD con su
        // temporizador vivo y el paciente puede reintentar mientras dure el
        // hold. La sala va a crearse aca y nunca en reservarTurno: un hold que
        // vence no tiene que dejar salas colgadas.
        eventoConfirmacion.fire(new TurnoEnConfirmacion(turno));

        turno.setEstado(EstadoTurno.CONFIRMADO);
        expirador.cancelar(turnoId);
        Turno confirmado = turnoDAO.actualizar(turno);
        publicarTurnoConfirmado(confirmado);
        return confirmado;
    }

    @RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno cancelarTurno(Long turnoId) {
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio");
        }
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
        turno.liberar();
        expirador.cancelar(turnoId);
        return turnoDAO.actualizar(turno);
    }

    // Expone el estado conversacional que mantiene esta instancia stateful:
    // que turno esta reteniendo esta conversacion en este momento.
    @PermitAll
    public Long getTurnoEnCursoId() {
        return turnoEnCursoId;
    }

    /**
     * Publica el evento de dominio TurnoConfirmado en el topico JMS.
     *
     * Va dentro de la misma transaccion JTA que confirmarTurno: el JMSContext
     * inyectado se enlista en esa transaccion, asi que si el metodo termina
     * haciendo rollback (o si publicar falla y eso hace abortar la
     * transaccion), el mensaje nunca sale. No hay forma de confirmar sin
     * publicar ni de publicar sin haber confirmado.
     *
     * Formato provisorio (MapMessage, no JSON) mientras se acuerda el contrato
     * final con quien implemente ServicioDeNotificaciones: turnoId, pacienteId
     * y profesionalId como long, fechaHora como String ISO-8601 (MapMessage no
     * admite LocalDateTime).
     *
     * ServicioDeTurnos no importa nada de ServicioDeNotificaciones: publica en
     * un topico por nombre JNDI, sin saber quien esta escuchando ni si hay
     * alguien escuchando.
     */
    private void publicarTurnoConfirmado(Turno turno) {
        try {
            MapMessage mensaje = jmsContext.createMapMessage();
            mensaje.setLong("turnoId", turno.getId());
            mensaje.setLong("pacienteId", turno.getPaciente().getId());
            mensaje.setLong("profesionalId", turno.getProfesional().getId());
            mensaje.setString("fechaHora", turno.getFechaHora().toString());
            jmsContext.createProducer().send(topicoTurnoConfirmado, mensaje);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e);
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
