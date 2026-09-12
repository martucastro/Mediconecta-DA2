package ar.edu.uade.da2.mediconecta.historiaclinica;

import java.util.List;

import jakarta.ejb.Local;

/**
 * Interfaz de negocio del componente ServicioDeHistoriaClinica.
 *
 * Es el único contrato que los consumidores conocen: la implementación, los DAO
 * y el factory quedan ocultos detrás de estas cinco operaciones.
 */
@Local
public interface ServicioDeHistoriaClinicaLocal {

    /**
     * Abre la historia clínica de un paciente. Falla si el usuario no existe,
     * si no tiene rol PACIENTE, o si ya tiene una historia abierta.
     */
    HistoriaClinica crearHistoria(Long pacienteId);

    /** Devuelve la historia del paciente con todas sus entradas, o null si no tiene. */
    HistoriaClinica obtenerHistoriaDePaciente(Long pacienteId);

    /** Agrega una entrada suelta (antecedente, diagnóstico o receta) a la historia. */
    EntradaClinica agregarEntrada(Long pacienteId, NuevaEntradaDTO datos);

    /**
     * Cierra una consulta: registra el diagnóstico y todas sus recetas de forma
     * atómica. Si cualquiera de las entradas es inválida, no se persiste ninguna.
     */
    HistoriaClinica registrarConsulta(Long pacienteId, RegistrarConsultaDTO datos);

    /** Entradas de la historia del paciente, ordenadas por fecha. */
    List<EntradaClinica> listarEntradas(Long pacienteId);
}
