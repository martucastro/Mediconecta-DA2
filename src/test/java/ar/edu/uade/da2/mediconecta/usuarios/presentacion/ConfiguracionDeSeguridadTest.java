package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;

/**
 * SCRUM-132: el identity store encuentra al usuario aunque el encabezado Basic
 * traiga el email con otras mayusculas, normalizando solo el parametro. Si
 * tambien se pasara a minusculas la columna, una fila vieja "ADMIN@x.com"
 * (otro usuario) coincidiria junto con "admin@x.com", y groupsQuery sumaria
 * los roles de las dos.
 */
class ConfiguracionDeSeguridadTest {

    private final DatabaseIdentityStoreDefinition definicion =
            ConfiguracionDeSeguridad.class.getAnnotation(DatabaseIdentityStoreDefinition.class);

    @Test
    void normalizanElParametroYComparanLaColumnaExacta() {
        for (String consulta : new String[] { definicion.callerQuery(), definicion.groupsQuery() }) {
            assertTrue(consulta.endsWith("WHERE email = LOWER(TRIM(?))"), consulta);
            assertFalse(consulta.contains("LOWER(email)"), consulta);
        }
    }
}
