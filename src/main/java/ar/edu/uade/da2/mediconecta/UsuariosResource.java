package ar.edu.uade.da2.mediconecta;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/usuarios")
@ApplicationScoped
public class UsuariosResource {

    @Inject
    private ServicioDeUsuarios servicio;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Usuario> listar() {
        return servicio.listarUsuarios();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Usuario obtener(@PathParam("id") Long id) {
        return servicio.obtenerUsuario(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Usuario registrar(Usuario usuario) {
        return servicio.registrarUsuario(usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.getContrasenaHash());
    }
    
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Usuario login(Usuario credenciales) {
        Usuario usuario = servicio.autenticar(credenciales.getEmail(), credenciales.getContrasenaHash());
        if (usuario == null) {
            throw new jakarta.ws.rs.WebApplicationException("Email o contraseña incorrectos", 401);
        }
        return usuario;
    }
}