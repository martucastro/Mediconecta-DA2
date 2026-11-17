package ar.edu.uade.da2.mediconecta.historiaclinica.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Antecedente;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Diagnostico;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.EntradaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.HistoriaClinica;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.Receta;
import ar.edu.uade.da2.mediconecta.historiaclinica.datos.TipoEntrada;

/**
 * SCRUM-117: el Factory decide la subclase segun el tipo y valida los campos
 * obligatorios de ese tipo en particular.
 */
class EntradaClinicaFactoryTest {

    private static final Long PROFESIONAL_ID = 2L;

    private final EntradaClinicaFactory factory = new EntradaClinicaFactory();
    private final HistoriaClinica historia = new HistoriaClinica(3L);

    @Test
    void unAntecedenteCreaUnAntecedenteConSusCampos() {
        NuevaEntradaDTO datos = entrada(TipoEntrada.ANTECEDENTE);
        datos.setTipoAntecedente("ALERGIA");
        datos.setDetalle("  Penicilina  ");

        EntradaClinica entrada = factory.crear(datos, historia, PROFESIONAL_ID);

        Antecedente antecedente = assertInstanceOf(Antecedente.class, entrada);
        assertEquals("ALERGIA", antecedente.getTipoAntecedente());
        assertEquals("Penicilina", antecedente.getDetalle());
        assertSame(historia, antecedente.getHistoria());
        assertEquals(PROFESIONAL_ID, antecedente.getProfesionalId());
    }

    @Test
    void unDiagnosticoCreaUnDiagnosticoConSusCampos() {
        NuevaEntradaDTO datos = entrada(TipoEntrada.DIAGNOSTICO);
        datos.setCodigoCIE10("J00");
        datos.setDescripcion("Resfrio comun");

        EntradaClinica entrada = factory.crear(datos, historia, PROFESIONAL_ID);

        Diagnostico diagnostico = assertInstanceOf(Diagnostico.class, entrada);
        assertEquals("J00", diagnostico.getCodigoCIE10());
        assertEquals("Resfrio comun", diagnostico.getDescripcion());
        assertEquals(TipoEntrada.DIAGNOSTICO, diagnostico.getTipo());
    }

    @Test
    void unaRecetaCreaUnaRecetaConSusCampos() {
        EntradaClinica entrada = factory.crear(receta("Paracetamol", "500mg", 5), historia, PROFESIONAL_ID);

        Receta receta = assertInstanceOf(Receta.class, entrada);
        assertEquals("Paracetamol", receta.getMedicamento());
        assertEquals("500mg", receta.getDosis());
        assertEquals(5, receta.getDiasTratamiento());
    }

    @Test
    void sinTipoEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(new NuevaEntradaDTO(), historia, PROFESIONAL_ID));
    }

    @Test
    void sinDatosEsDatoInvalido() {
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(null, historia, PROFESIONAL_ID));
    }

    @Test
    void cadaTipoExigeSusPropiosCampos() {
        // Un diagnostico sin medicamento es normal; sin codigo CIE-10, no.
        NuevaEntradaDTO diagnostico = entrada(TipoEntrada.DIAGNOSTICO);
        diagnostico.setDescripcion("Sin codigo");
        NuevaEntradaDTO antecedente = entrada(TipoEntrada.ANTECEDENTE);
        antecedente.setTipoAntecedente("CIRUGIA");

        assertThrows(DatosInvalidosException.class, () -> factory.crear(diagnostico, historia, PROFESIONAL_ID));
        assertThrows(DatosInvalidosException.class, () -> factory.crear(antecedente, historia, PROFESIONAL_ID));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(receta("  ", "500mg", 5), historia, PROFESIONAL_ID));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(receta("Paracetamol", null, 5), historia, PROFESIONAL_ID));
    }

    @Test
    void unaRecetaExigeDiasDeTratamientoEntreUnoYElMaximo() {
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(receta("Paracetamol", "500mg", null), historia, PROFESIONAL_ID));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(receta("Paracetamol", "500mg", 0), historia, PROFESIONAL_ID));
        assertThrows(DatosInvalidosException.class, () -> factory.crear(
                receta("Paracetamol", "500mg", Receta.MAX_DIAS_TRATAMIENTO + 1), historia, PROFESIONAL_ID));
    }

    @Test
    void unTextoMasLargoQueLaColumnaEsDatoInvalidoYNoUn500() {
        NuevaEntradaDTO datos = entrada(TipoEntrada.DIAGNOSTICO);
        datos.setCodigoCIE10("X".repeat(Diagnostico.MAX_CODIGO_CIE10 + 1));
        datos.setDescripcion("Codigo demasiado largo");

        assertThrows(DatosInvalidosException.class, () -> factory.crear(datos, historia, PROFESIONAL_ID));
    }

    private static NuevaEntradaDTO entrada(TipoEntrada tipo) {
        NuevaEntradaDTO datos = new NuevaEntradaDTO();
        datos.setTipo(tipo);
        return datos;
    }

    private static NuevaEntradaDTO receta(String medicamento, String dosis, Integer dias) {
        NuevaEntradaDTO datos = entrada(TipoEntrada.RECETA);
        datos.setMedicamento(medicamento);
        datos.setDosis(dosis);
        datos.setDiasTratamiento(dias);
        return datos;
    }
}
