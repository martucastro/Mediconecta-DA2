package ar.edu.uade.da2.mediconecta;

import jakarta.ejb.Stateless;

@Stateless
public class SaludoService {
    public String mensaje() {
        return "¡Hola desde MediConecta!";
    }
}