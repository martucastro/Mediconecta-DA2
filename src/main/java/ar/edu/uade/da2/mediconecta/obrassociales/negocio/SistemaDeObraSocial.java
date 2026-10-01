package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

/**
 * El sistema de la obra social, visto desde MediConecta.
 *
 * Es el port del Adapter: la fachada ServicioDeObrasSociales habla contra esta
 * interfaz y recibe una Cobertura, sin saber que del otro lado hay un legado
 * SOAP. Esa implementacion (negocio.soap.SistemaDeObraSocialSoap) es el unico
 * lugar que conoce el WSDL; si manana la obra social migrara a REST, cambiaria
 * esa clase y nada mas. Mismo criterio que PasarelaDePagoAdapter en pagos.
 *
 * Las dos operaciones fallan igual, con excepciones de negocio:
 * - ObraSocialNoDisponibleException: el legado no respondio a tiempo, fallo por
 *   su cuenta o contesto algo inutilizable. No dice nada sobre el pedido; se
 *   puede reintentar.
 * - DatosInvalidosException: el legado respondio, pero no reconoce el pedido
 *   (afiliado o prestacion inexistentes). Reintentar no sirve.
 *
 * Que el afiliado no tenga cobertura no es una falla: es una Cobertura con
 * autorizada = false.
 */
public interface SistemaDeObraSocial {

    /** Consulta la cobertura sin pedir autorizacion: nunca trae numero de autorizacion. */
    Cobertura consultar(String dni, String numeroAfiliado, Prestacion prestacion);

    /** Pide la autorizacion: si queda autorizada, trae el numero emitido por el legado. */
    Cobertura autorizar(String dni, String numeroAfiliado, Prestacion prestacion);
}
