package ar.edu.uade.da2.mediconecta.usuarios.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * ProfesionalDTO es lo que sale por un endpoint publico (sin autenticacion):
 * si alguien le agrega un campo, estas pruebas fallan antes de que ese dato
 * (por ejemplo el email) quede expuesto sin que nadie lo note.
 */
class ProfesionalDTOTest {

    @Test
    void soloTieneLosCamposIdYNombre() {
        Set<String> campos = new TreeSet<>();
        for (Field campo : ProfesionalDTO.class.getDeclaredFields()) {
            campos.add(campo.getName());
        }
        assertEquals(Set.of("id", "nombre"), campos);
    }

    @Test
    void soloExponeLosGettersDeIdYNombre() {
        Set<String> getters = new TreeSet<>();
        for (Method metodo : ProfesionalDTO.class.getDeclaredMethods()) {
            if (metodo.getName().startsWith("get")) {
                getters.add(metodo.getName());
            }
        }
        assertEquals(Set.of("getId", "getNombre"), getters);
    }

    @Test
    void copiaIdYNombreDelUsuario() {
        Usuario usuario = new Usuario("Diego Martinez", "diego@mediconecta.com", "PROFESIONAL", "hash");
        usuario.setId(5L);

        ProfesionalDTO dto = new ProfesionalDTO(usuario);

        assertEquals(5L, dto.getId());
        assertEquals("Diego Martinez", dto.getNombre());
    }
}
