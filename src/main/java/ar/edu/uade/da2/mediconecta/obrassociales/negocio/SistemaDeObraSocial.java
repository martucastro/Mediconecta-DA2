package ar.edu.uade.da2.mediconecta.obrassociales.datos;

/**
 * El sistema de la obra social, visto desde MediConecta.
 *
 * Es la interfaz que el negocio conoce: no menciona SOAP, ni WSDL, ni ninguna
 * clase generada. Que hoy del otro lado haya un legado SOAP es un detalle de la
 * implementación (SistemaDeObraSocialSoap); si mañana fuera REST, sólo cambiaría
 * esa clase.
 *
 * Las dos operaciones pueden fallar de dos maneras distintas, y el negocio las
 * trata distinto:
 * - LegadoNoDisponibleException: el legado no respondió a tiempo o no se pudo
 *   conectar. No dice nada sobre el paciente; se puede reintentar.
 * - PedidoRechazadoPorLegadoException: el legado respondió, pero no reconoce el
 *   pedido (afiliado o prestación inexistentes). Reintentar no sirve.
 */
public interface SistemaDeObraSocial {

    /** Consulta la cobertura sin pedir autorización: nunca trae número. */
    RespuestaDelLegado consultarCobertura(String dni, String numeroAfiliado, String codigoPrestacion);

    /** Pide la autorización: si queda autorizada, trae el número emitido por el legado. */
    RespuestaDelLegado solicitarAutorizacion(String dni, String numeroAfiliado, String codigoPrestacion);
}
