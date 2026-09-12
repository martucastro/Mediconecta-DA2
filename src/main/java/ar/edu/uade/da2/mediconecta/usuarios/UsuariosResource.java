package ar.edu.uade.da2.mediconecta.usuarios;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/usuarios")
@ApplicationScoped
public class UsuariosResource {

    @Inject
    private ServicioDeUsuarios servicio;

    @RolesAllowed("ADMINISTRADOR")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<UsuarioDTO> listar() {
        return servicio.listarUsuarios()
                .stream()
                .map(UsuarioDTO::new)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response obtener(@PathParam("id") Long id) {
        Usuario usuario = servicio.obtenerUsuario(id);
        if (usuario == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Usuario no encontrado")
                    .build();
        }
        return Response.ok(new UsuarioDTO(usuario)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrar(RegistroUsuarioDTO datos) {
        try {
            Usuario nuevoUsuario = servicio.registrarUsuario(
                    datos.getNombre(), datos.getEmail(), datos.getRol(), datos.getContrasena());
            return Response.status(Response.Status.CREATED)
                    .entity(new UsuarioDTO(nuevoUsuario))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginDTO credenciales) {
        Usuario usuario = servicio.autenticar(credenciales.getEmail(), credenciales.getContrasena());
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Email o contraseña incorrectos")
                    .build();
        }
        return Response.ok(new UsuarioDTO(usuario)).build();
    }
}