package ar.edu.uade.da2.mediconecta.telemedicina.negocio;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeTelemedicina (stateless).
 *
 * Es el único contrato que los consumidores conocen: TelemedicinaResource y el
 * observador de la confirmación (SalaAlConfirmar). El proveedor de video queda
 * oculto detrás del Adapter ProveedorDeVideoAdapter. Los roles y los atributos
 * de transacción se declaran sobre la implementación; aquí se documentan.
 *
 * Roles de todo el componente: PACIENTE y PROFESIONAL. El administrador queda
 * afuera a propósito y recibe EJBAccessException: una sala es de las dos
 * personas del turno.
 */
@Local
public interface ServicioDeTelemedicinaLocal {

    /**
     * Crea la sala de video de un turno de telemedicina, o devuelve la que ya
     * tiene para el paciente actual (idempotente). Devuelve solo el enlace de
     * quien la pide.
     *
     * Roles: PACIENTE o PROFESIONAL del turno. Transacción: REQUIRED; toma el
     * lock de escritura del turno, así que dos pedidos simultáneos quedan
     * serializados. Dentro de confirmarTurno se une a esa transacción.
     *
     * Falla con DatosInvalidosException si el id es null; con
     * EJBAccessException si el caller no es paciente ni profesional del turno
     * (también si el turno no existe); con ConflictoDeNegocioException si el
     * turno es presencial o no está tomado por un paciente, y con
     * ProveedorDeVideoNoDisponibleException si el proveedor no responde.
     */
    SesionCreada crearSesion(Long turnoId);

    /**
     * El enlace que le corresponde a quien pregunta (anfitrión para el
     * profesional, invitado para el paciente), o null si el turno no tiene una
     * sala vigente.
     *
     * Roles: PACIENTE o PROFESIONAL del turno. Transacción: SUPPORTS; es una
     * lectura y no necesita una transacción propia.
     *
     * Falla con DatosInvalidosException si el id es null y con
     * EJBAccessException si el caller no es paciente ni profesional del turno.
     */
    EnlaceDeSesion obtenerEnlace(Long turnoId);
}
