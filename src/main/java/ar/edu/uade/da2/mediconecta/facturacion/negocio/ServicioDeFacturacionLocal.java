package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import java.util.List;

import ar.edu.uade.da2.mediconecta.facturacion.datos.Reclamo;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeFacturacion (stateless).
 *
 * Es el único contrato que los consumidores conocen: los dos MDB del
 * componente (TurnoConfirmadoFacturacionMDB y ReclamoMDB), el observador de la
 * cancelación (ReclamoAlCancelar) y ReclamosResource. El canal SOAP hacia la
 * obra social queda oculto detrás de CanalDeReclamos. Los roles y los
 * atributos de transacción se declaran sobre la implementación; aquí se
 * documentan.
 *
 * Todas las operaciones usan el atributo de transacción por defecto
 * (REQUIRED). Las que invocan los MDB y el observador no restringen por rol
 * (@PermitAll de clase): un MDB no tiene un caller autenticado.
 */
@Local
public interface ServicioDeFacturacionLocal {

    /**
     * Registra el reclamo de un turno recién confirmado y lo encola en
     * ReclamosFacturacion. Se saltea si el turno no existe, no tiene cobertura
     * autorizada o ya no está CONFIRMADO, y es idempotente por turnoId.
     *
     * Roles: sin restricción. Transacción: REQUIRED; el reclamo y el mensaje a
     * la cola se confirman o se revierten juntos con la entrega del mensaje
     * TurnoConfirmado. No lanza excepciones de negocio.
     */
    void registrarReclamo(Long turnoId);

    /**
     * Procesa un reclamo PENDIENTE contra el canal de la obra social. intento
     * es el número de entrega del mensaje (JMSXDeliveryCount), que traduce
     * ReclamoMDB. Un reclamo que ya no está PENDIENTE se ignora.
     *
     * Roles: sin restricción. Transacción: REQUIRED; se une a la transacción de
     * la entrega del mensaje.
     *
     * Ante una falla transitoria registra el intento y relanza
     * CanalDeReclamosNoDisponibleException para que el contenedor haga
     * rollback y la cola reentregue, salvo en el último intento, que deja el
     * reclamo EN_REVISION_MANUAL sin relanzar. Un rechazo definitivo de la obra
     * social también lo deja EN_REVISION_MANUAL, sin relanzar.
     */
    void procesarReclamo(Long reclamoId, int intento);

    /**
     * Anula el reclamo de un turno confirmado que el paciente canceló: uno
     * PENDIENTE pasa a ANULADO y uno ya ENVIADO pasa a EN_REVISION_MANUAL. Sin
     * reclamo no hace nada.
     *
     * Roles: sin restricción. Transacción: REQUIRED; se une a la transacción de
     * cancelarTurno, así que si la cancelación se revierte, la anulación
     * también. No lanza excepciones de negocio.
     */
    void anularReclamo(Long turnoId);

    /**
     * Listado completo de reclamos, para el panel del administrador.
     *
     * Roles: ADMINISTRADOR; cualquier otro caller recibe EJBAccessException.
     * Transacción: REQUIRED.
     */
    List<Reclamo> listarReclamos();
}
