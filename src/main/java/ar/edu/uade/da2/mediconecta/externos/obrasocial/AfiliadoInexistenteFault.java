package ar.edu.uade.da2.mediconecta.externos.obrasocial;

import jakarta.xml.ws.WebFault;

/**
 * El legado no reconoce el pedido: afiliado desconocido, DNI que no corresponde
 * al número de afiliado o prestación inexistente. Viaja como SOAP Fault.
 *
 * Es distinto de "sin cobertura": un afiliado sin cobertura es una respuesta
 * válida del legado; esto es un pedido que el legado no puede ni evaluar.
 */
@WebFault(name = "afiliadoInexistente")
public class AfiliadoInexistenteFault extends Exception {

    public AfiliadoInexistenteFault(String mensaje) {
        super(mensaje);
    }
}
