package ar.edu.uade.da2.mediconecta.turnos.negocio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeTurnos (stateful).
 *
 * Es el único contrato que los consumidores conocen: TurnosResource y las
 * fachadas de pagos, telemedicina y facturación, que leen turnos por aquí y
 * nunca por el TurnoDAO. Los roles y los atributos de transacción se declaran
 * sobre la implementación; aquí se documentan.
 *
 * El rol por defecto de la clase es PACIENTE, PROFESIONAL o ADMINISTRADOR (es
 * decir, cualquier usuario autenticado); cada operación indica si lo
 * restringe. Un caller sin el rol requerido recibe EJBAccessException.
 */
@Local
public interface ServicioDeTurnosLocal {

    /**
     * Franjas DISPONIBLE de un profesional.
     *
     * Roles: sin restricción en el componente (@PermitAll). Transacción:
     * REQUIRED.
     */
    List<Turno> consultarDisponibilidad(Long profesionalId);

    /**
     * Lectura de un turno por id, para otros componentes. Devuelve null si no
     * existe.
     *
     * Roles: cualquier usuario autenticado. Transacción: REQUIRED.
     */
    Turno obtenerTurno(Long turnoId);

    /**
     * Igual que obtenerTurno, pero con lock de escritura sobre la fila: quien
     * lo invoca decide sobre el turno sin que otro pedido lo cambie en el medio.
     * Devuelve null si no existe.
     *
     * Roles: cualquier usuario autenticado. Transacción: MANDATORY; invocado
     * sin una transacción activa falla con EJBTransactionRequiredException,
     * porque un lock fuera de una transacción no protege nada.
     */
    Turno obtenerTurnoParaActualizar(Long turnoId);

    /**
     * Turnos del usuario autenticado: los del paciente, o la agenda del
     * profesional (filtrada por día si fecha no es null; el filtro se ignora
     * para un paciente). El usuario sale siempre del caller, nunca de un
     * parámetro.
     *
     * Roles: PACIENTE y PROFESIONAL. Transacción: REQUIRED. Falla con
     * EJBAccessException si el caller no corresponde a un usuario registrado.
     */
    List<Turno> misTurnos(LocalDate fecha);

    /**
     * Abre una franja disponible en la agenda del profesional autenticado.
     * modalidad null equivale a PRESENCIAL; el consultorio solo aplica a las
     * franjas presenciales.
     *
     * Roles: PROFESIONAL. Transacción: REQUIRED.
     *
     * Falla con DatosInvalidosException si falta la fecha, si está en el
     * pasado o a más de dos años, si una franja de telemedicina trae
     * consultorio o si el consultorio excede su largo, y con
     * ConflictoDeNegocioException si el profesional ya tiene una franja en ese
     * horario.
     */
    Turno abrirDisponibilidad(LocalDateTime fechaHora, ModalidadTurno modalidad, String consultorio);

    /**
     * Retiene el turno a nombre del paciente autenticado durante cinco minutos
     * (estado EN_HOLD) y programa su expiración. Antes de retenerlo dispara el
     * punto de extensión TurnoEnReserva, que cotiza la cobertura del paciente.
     *
     * Roles: PACIENTE. Transacción: REQUIRED; la cotización corre en la misma
     * transacción, así que si falla no queda ningún hold.
     *
     * Falla con DatosInvalidosException si el id es null o el turno no existe;
     * con ConflictoDeNegocioException si el turno no está DISPONIBLE, ya pasó
     * o el paciente ya retiene otro turno con el hold vigente (uno por
     * paciente), y con ObraSocialNoDisponibleException si el legado de la obra
     * social no responde.
     */
    Turno reservarTurno(Long turnoId);

    /**
     * Confirma el hold del paciente autenticado. Dispara el punto de extensión
     * TurnoEnConfirmacion (cobro del copago con tokenMedioDePago y sala de
     * video si es telemedicina) y publica TurnoConfirmado en el tópico JMS.
     * tokenMedioDePago puede ser null si el turno no tiene copago.
     *
     * Roles: PACIENTE. Transacción: REQUIRED; el cobro, la sala y la
     * publicación del mensaje forman parte de la misma transacción, de modo
     * que si cualquiera falla el turno sigue EN_HOLD y el mensaje no sale.
     *
     * Falla con DatosInvalidosException si el id es null o el turno no existe;
     * con EJBAccessException si el hold es de otro paciente; con
     * ConflictoDeNegocioException si no hay un hold activo o la pasarela
     * rechaza el cobro, y con PasarelaNoDisponibleException o
     * ProveedorDeVideoNoDisponibleException si el sistema externo no responde.
     */
    Turno confirmarTurno(Long turnoId, String tokenMedioDePago);

    /**
     * Cancela el turno del paciente autenticado. Un hold vuelve a la agenda
     * como DISPONIBLE; un turno CONFIRMADO queda CANCELADO y después dispara el
     * punto de extensión TurnoEnCancelacion (anulación del reclamo, sala
     * CANCELADA y, al final, reembolso del copago).
     *
     * Roles: PACIENTE. Transacción: REQUIRED; si la compensación falla, la
     * cancelación entera se revierte y el turno sigue CONFIRMADO.
     *
     * Falla con DatosInvalidosException si el id es null o el turno no existe;
     * con EJBAccessException si el turno es de otro paciente, y con
     * ConflictoDeNegocioException si no está reservado.
     */
    Turno cancelarTurno(Long turnoId);
}
