package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeObrasSociales (stateless).
 *
 * Es el único contrato que los consumidores conocen: el observador de la
 * reserva (CoberturaEnLaReserva), el canal de reclamos de facturación y la capa
 * de presentación de afiliaciones. Cómo se habla SOAP con el legado queda
 * oculto detrás del Adapter SistemaDeObraSocial. Los roles y los atributos de
 * transacción se declaran sobre la implementación; aquí se documentan.
 *
 * El componente no restringe por rol salvo donde se indica (@PermitAll de
 * clase): quién puede reservar lo decide la fachada que lo invoca.
 */
@Local
public interface ServicioDeObrasSocialesLocal {

    /**
     * Lo que pide la reserva de un turno: la cobertura del paciente con la
     * autorización ya pedida y registrada, o la cobertura de un particular
     * (nada cubierto, copago igual al arancel) si no tiene obra social.
     *
     * Roles: sin restricción. Transacción: REQUIRED; participa de la
     * transacción de la reserva, así que la autorización guardada se deshace
     * si la reserva falla después.
     *
     * Falla con DatosInvalidosException si falta la prestación o el paciente
     * no existe o no tiene rol PACIENTE, y con ObraSocialNoDisponibleException
     * si el legado no responde o autoriza sin número de autorización.
     */
    Cobertura cotizarReserva(Long pacienteId, Prestacion prestacion);

    /**
     * Presenta ante la obra social una prestación ya autorizada, para que
     * facturación pueda cobrarle su parte.
     *
     * Roles: sin restricción. Transacción: NOT_SUPPORTED; la llamada remota no
     * arrastra la transacción de quien la invoca.
     *
     * Falla con DatosInvalidosException si el paciente no es válido, no tiene
     * afiliación o la obra social rechaza el reclamo, y con
     * ObraSocialNoDisponibleException si el legado no responde.
     */
    ResultadoPresentacion presentarReclamo(Long pacienteId, String numeroAutorizacion);

    /**
     * Registra o corrige con qué DNI y número de afiliado conoce la obra social
     * al paciente. Si ya tenía afiliación, la actualiza.
     *
     * Roles: ADMINISTRADOR. Transacción: REQUIRED.
     *
     * Falla con DatosInvalidosException si falta el paciente, el DNI o el
     * número de afiliado, o si el usuario no existe o no tiene rol PACIENTE.
     */
    AfiliacionDePaciente registrarAfiliacion(Long pacienteId, String dni, String numeroAfiliado);

    /**
     * La afiliación del paciente autenticado (DNI y número de afiliado), o null
     * si no tiene obra social registrada. El paciente sale del caller, nunca de
     * un parámetro.
     *
     * Roles: PACIENTE. Transacción: SUPPORTS; es una lectura y no necesita una
     * transacción propia. Falla con EJBAccessException si el caller no
     * corresponde a un usuario registrado.
     */
    AfiliacionDePaciente obtenerMiAfiliacion();
}
