package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Antecedente;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Diagnostico;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Receta;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Patrón Factory.
 *
 * Concentra dos responsabilidades que, de otra forma, quedarían mezcladas en la
 * fachada como un switch sobre el tipo: decidir qué subclase de EntradaClinica
 * instanciar, y validar los campos obligatorios *de ese tipo en particular*
 * (una receta sin medicamento es inválida, pero un diagnóstico sin medicamento
 * es perfectamente normal).
 *
 * Agregar un cuarto tipo de entrada se resuelve acá, sin tocar el servicio.
 */
@ApplicationScoped
public class EntradaClinicaFactory {

    public EntradaClinica crear(NuevaEntradaDTO datos, HistoriaClinica historia, Long profesionalId) {
        if (datos == null) {
            throw new DatosInvalidosException("Los datos de la entrada son obligatorios.");
        }
        if (datos.getTipo() == null) {
            throw new DatosInvalidosException(
                    "El tipo de entrada es obligatorio (ANTECEDENTE, DIAGNOSTICO o RECETA).");
        }

        return switch (datos.getTipo()) {
            case ANTECEDENTE -> crearAntecedente(datos, historia, profesionalId);
            case DIAGNOSTICO -> crearDiagnostico(datos, historia, profesionalId);
            case RECETA -> crearReceta(datos, historia, profesionalId);
        };
    }

    private Antecedente crearAntecedente(NuevaEntradaDTO datos, HistoriaClinica historia,
                                         Long profesionalId) {
        exigir(datos.getTipoAntecedente(), "tipoAntecedente", TipoEntrada.ANTECEDENTE);
        exigir(datos.getDetalle(), "detalle", TipoEntrada.ANTECEDENTE);
        return new Antecedente(historia, profesionalId,
                datos.getTipoAntecedente().trim(), datos.getDetalle().trim());
    }

    private Diagnostico crearDiagnostico(NuevaEntradaDTO datos, HistoriaClinica historia,
                                         Long profesionalId) {
        exigir(datos.getCodigoCIE10(), "codigoCIE10", TipoEntrada.DIAGNOSTICO);
        exigir(datos.getDescripcion(), "descripcion", TipoEntrada.DIAGNOSTICO);
        return new Diagnostico(historia, profesionalId,
                datos.getCodigoCIE10().trim(), datos.getDescripcion().trim());
    }

    private Receta crearReceta(NuevaEntradaDTO datos, HistoriaClinica historia,
                               Long profesionalId) {
        exigir(datos.getMedicamento(), "medicamento", TipoEntrada.RECETA);
        exigir(datos.getDosis(), "dosis", TipoEntrada.RECETA);
        if (datos.getDiasTratamiento() == null || datos.getDiasTratamiento() <= 0) {
            throw new DatosInvalidosException(
                    "Una RECETA requiere diasTratamiento mayor a cero.");
        }
        return new Receta(historia, profesionalId,
                datos.getMedicamento().trim(), datos.getDosis().trim(),
                datos.getDiasTratamiento());
    }

    private void exigir(String valor, String campo, TipoEntrada tipo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException(
                    "Una entrada de tipo " + tipo + " requiere el campo '" + campo + "'.");
        }
    }
}
