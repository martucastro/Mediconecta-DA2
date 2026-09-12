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
        String tipoAntecedente = exigir(datos.getTipoAntecedente(), "tipoAntecedente",
                TipoEntrada.ANTECEDENTE, Antecedente.MAX_TIPO_ANTECEDENTE);
        String detalle = exigir(datos.getDetalle(), "detalle",
                TipoEntrada.ANTECEDENTE, Antecedente.MAX_DETALLE);
        return new Antecedente(historia, profesionalId, tipoAntecedente, detalle);
    }

    private Diagnostico crearDiagnostico(NuevaEntradaDTO datos, HistoriaClinica historia,
                                         Long profesionalId) {
        String codigo = exigir(datos.getCodigoCIE10(), "codigoCIE10",
                TipoEntrada.DIAGNOSTICO, Diagnostico.MAX_CODIGO_CIE10);
        String descripcion = exigir(datos.getDescripcion(), "descripcion",
                TipoEntrada.DIAGNOSTICO, Diagnostico.MAX_DESCRIPCION);
        return new Diagnostico(historia, profesionalId, codigo, descripcion);
    }

    private Receta crearReceta(NuevaEntradaDTO datos, HistoriaClinica historia,
                               Long profesionalId) {
        String medicamento = exigir(datos.getMedicamento(), "medicamento",
                TipoEntrada.RECETA, Receta.MAX_MEDICAMENTO);
        String dosis = exigir(datos.getDosis(), "dosis", TipoEntrada.RECETA, Receta.MAX_DOSIS);

        Integer dias = datos.getDiasTratamiento();
        if (dias == null || dias <= 0) {
            throw new DatosInvalidosException(
                    "Una RECETA requiere diasTratamiento mayor a cero.");
        }
        if (dias > Receta.MAX_DIAS_TRATAMIENTO) {
            throw new DatosInvalidosException("diasTratamiento no puede superar "
                    + Receta.MAX_DIAS_TRATAMIENTO + " dias.");
        }
        return new Receta(historia, profesionalId, medicamento, dosis, dias);
    }

    /**
     * Exige presencia y largo. El largo se valida contra la misma constante que
     * declara la columna: si esta validacion faltara, el texto demasiado largo
     * llegaria al INSERT y el error de base saldria como un 500, cuando en
     * realidad es un dato invalido del cliente y corresponde un 400.
     */
    private String exigir(String valor, String campo, TipoEntrada tipo, int largoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException(
                    "Una entrada de tipo " + tipo + " requiere el campo '" + campo + "'.");
        }
        String normalizado = valor.trim();
        if (normalizado.length() > largoMaximo) {
            throw new DatosInvalidosException("El campo '" + campo + "' admite hasta "
                    + largoMaximo + " caracteres y recibio " + normalizado.length() + ".");
        }
        return normalizado;
    }
}
