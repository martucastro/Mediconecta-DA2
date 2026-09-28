package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDAO;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Le asigna una afiliación al paciente de prueba, para que la cobertura se
 * pueda consultar en una instalación nueva sin pasos manuales.
 *
 * Usa el afiliado del plan medio del legado simulado (70 %), el caso más
 * representativo: autoriza y deja copago.
 *
 * Escribe por el DAO y no por registrarAfiliacion porque corre en el arranque,
 * sin un caller autenticado como administrador; mismo criterio que
 * SeedDeUsuariosIniciales, del que depende para que el paciente ya exista.
 */
@Singleton
@Startup
@DependsOn("SeedDeUsuariosIniciales")
public class SeedDeAfiliaciones {

    private static final Logger LOGGER = Logger.getLogger(SeedDeAfiliaciones.class.getName());

    private static final String EMAIL_PACIENTE = "paciente@mediconecta.com";
    private static final String DNI = "30333444";
    private static final String NUMERO_AFILIADO = "OS-2002";

    @Inject
    private AfiliacionDAO afiliacionDAO;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sembrar() {
        Usuario paciente = servicioDeUsuarios.obtenerPorEmail(EMAIL_PACIENTE);
        if (paciente == null || afiliacionDAO.buscarPorPaciente(paciente.getId()) != null) {
            return;
        }
        afiliacionDAO.guardar(new AfiliacionDePaciente(paciente.getId(), DNI, NUMERO_AFILIADO));
        LOGGER.info("Seed de afiliaciones: " + EMAIL_PACIENTE + " afiliado como " + NUMERO_AFILIADO + ".");
    }
}
