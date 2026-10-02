package ar.edu.uade.da2.mediconecta.facturacion.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ObraSocialNoDisponibleException;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ResultadoPresentacion;
import ar.edu.uade.da2.mediconecta.obrassociales.negocio.ServicioDeObrasSociales;

/**
 * CanalDeReclamos contra la fachada ServicioDeObrasSociales, sin contenedor:
 * la fachada es un doble, asi que lo que se verifica es la traduccion de sus
 * excepciones de negocio a las del puerto de facturacion, y la traduccion del
 * resultado exitoso a ResultadoReclamo.
 */
@ExtendWith(MockitoExtension.class)
class CanalDeReclamosSoapTest {

    private static final Long TURNO_ID = 1L;
    private static final Long PACIENTE_ID = 3L;
    private static final String AUTORIZACION = "AUT-OS-2002-CONSULTA";
    private static final BigDecimal PORCENTAJE = new BigDecimal("70.00");

    @Mock
    private ServicioDeObrasSociales servicioDeObrasSociales;

    @InjectMocks
    private CanalDeReclamosSoap canal;

    @Test
    void presentarReclamoDevuelveElResultadoDeLaFachadaTraducido() {
        when(servicioDeObrasSociales.presentarReclamo(PACIENTE_ID, AUTORIZACION))
                .thenReturn(new ResultadoPresentacion("PRES-AUT-OS-2002-CONSULTA", new BigDecimal("14000.00")));

        ResultadoReclamo resultado = canal.presentarReclamo(TURNO_ID, PACIENTE_ID, AUTORIZACION, PORCENTAJE);

        assertEquals(new BigDecimal("14000.00"), resultado.getMonto());
        assertEquals("PRES-AUT-OS-2002-CONSULTA", resultado.getNumeroPresentacion());
    }

    @Test
    void unDatoInvalidoDeLaFachadaEsUnReclamoRechazado() {
        DatosInvalidosException rechazo = new DatosInvalidosException("La autorización no corresponde al afiliado");
        when(servicioDeObrasSociales.presentarReclamo(PACIENTE_ID, AUTORIZACION)).thenThrow(rechazo);

        ReclamoRechazadoException e = assertThrows(ReclamoRechazadoException.class,
                () -> canal.presentarReclamo(TURNO_ID, PACIENTE_ID, AUTORIZACION, PORCENTAJE));

        assertEquals(rechazo.getMessage(), e.getMessage());
    }

    @Test
    void laObraSocialNoDisponibleEsUnCanalNoDisponibleConSuCausa() {
        ObraSocialNoDisponibleException caida = new ObraSocialNoDisponibleException("sin respuesta");
        when(servicioDeObrasSociales.presentarReclamo(PACIENTE_ID, AUTORIZACION)).thenThrow(caida);

        CanalDeReclamosNoDisponibleException e = assertThrows(CanalDeReclamosNoDisponibleException.class,
                () -> canal.presentarReclamo(TURNO_ID, PACIENTE_ID, AUTORIZACION, PORCENTAJE));

        assertSame(caida, e.getCause());
    }
}
