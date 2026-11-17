package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;

/**
 * SCRUM-132: el identity store tiene que encontrar al usuario aunque el
 * encabezado Basic traiga el email con otras mayusculas que el registro.
 */
class ConfiguracionDeSeguridadTest {

    private final DatabaseIdentityStoreDefinition definicion =
            ConfiguracionDeSeguridad.class.getAnnotation(DatabaseIdentityStoreDefinition.class);

    @Test
    void lasConsultasDelIdentityStoreNoDistinguenMayusculas() {
        assertTrue(definicion.callerQuery().contains("LOWER(email) = LOWER(?)"), definicion.callerQuery());
        assertTrue(definicion.groupsQuery().contains("LOWER(email) = LOWER(?)"), definicion.groupsQuery());
    }
}
