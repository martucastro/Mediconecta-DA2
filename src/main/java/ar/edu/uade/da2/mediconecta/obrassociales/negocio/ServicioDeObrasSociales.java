package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.time.LocalDateTime;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDAO;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AfiliacionDePaciente;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AutorizacionDAO;
import ar.edu.uade.da2.mediconecta.obrassociales.datos.AutorizacionDePrestacion;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Patrón Adapter, lado de adentro: el resto del sistema pregunta por la
 * cobertura de un paciente en términos de dominio (pacienteId, Prestacion) y
 * recibe una Cobertura. Cómo se identifica ese paciente ante la obra social y
 * cómo se habla con ella queda encerrado en este componente.
 *
 * Stateless porque cada consulta es autocontenida.
 *
 * @PermitAll de clase por la misma razón que ServicioDeUsuarios: WildFly
 * deniega por defecto los métodos sin anotación en cuanto el bean tiene alguna.
 * Quién puede reservar o autorizar lo decide la fachada que invoca a este
 * componente (ServicioDeTurnos), que es la que conoce el caso de uso.
 */
@PermitAll
@Stateless
public class ServicioDeObrasSociales {

    private static final Logger LOGGER = Logger.getLogger(ServicioDeObrasSociales.class.getName());

    private static final String ROL_PACIENTE = "PACIENTE";

    @Inject
    private SistemaDeObraSocial obraSocial;

    @Inject
    private AfiliacionDAO afiliacionDAO;

    @Inject
    private AutorizacionDAO autorizacionDAO;

    // Dependencia hacia otro componente, siempre a través de su fachada.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    /**
     * Consulta la cobertura sin comprometer nada: no pide autorización ni
     * persiste. NOT_SUPPORTED porque es una llamada remota que puede tardar
     * hasta el timeout, y no hay nada que proteger con una transacción.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Cobertura validarCobertura(Long pacienteId, Prestacion prestacion) {
        AfiliacionDePaciente afiliacion = afiliacionDe(pacienteId, prestacion);
        return obraSocial.consultar(afiliacion.getDni(), afiliacion.getNumeroAfiliado(), prestacion);
    }

    /**
     * Pide la autorización y, si la obra social la otorga, la guarda con su
     * número y fecha para facturación.
     *
     * Participa de la transacción de quien lo invoque: si la reserva del turno
     * falla después, la autorización guardada se deshace con ella. La llamada al
     * legado no es transaccional, pero los timeouts acotan cuánto puede retener
     * la transacción.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Cobertura autorizarPrestacion(Long pacienteId, Prestacion prestacion) {
        AfiliacionDePaciente afiliacion = afiliacionDe(pacienteId, prestacion);
        Cobertura cobertura = obraSocial.autorizar(
                afiliacion.getDni(), afiliacion.getNumeroAfiliado(), prestacion);

        if (cobertura.autorizada()) {
            // Una autorización sin número no sirve para facturar: se trata como
            // una respuesta inutilizable del legado, no como un éxito.
            if (cobertura.numeroAutorizacion() == null || cobertura.numeroAutorizacion().isBlank()) {
                throw new ObraSocialNoDisponibleException(
                        "La obra social autorizó la prestación pero no informó número de autorización.");
            }
            autorizacionDAO.guardar(new AutorizacionDePrestacion(pacienteId, prestacion.name(),
                    cobertura.numeroAutorizacion(), LocalDateTime.now(),
                    cobertura.porcentaje(), cobertura.copago()));
            LOGGER.info("Autorización " + cobertura.numeroAutorizacion() + " registrada para el paciente "
                    + pacienteId + " (" + prestacion + ").");
        }
        return cobertura;
    }

    /**
     * Registra o corrige con qué DNI y número de afiliado conoce la obra social
     * al paciente.
     */
    @RolesAllowed("ADMINISTRADOR")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public AfiliacionDePaciente registrarAfiliacion(Long pacienteId, String dni, String numeroAfiliado) {
        validarPaciente(pacienteId);
        if (dni == null || dni.isBlank() || numeroAfiliado == null || numeroAfiliado.isBlank()) {
            throw new DatosInvalidosException("El DNI y el número de afiliado son obligatorios.");
        }

        AfiliacionDePaciente afiliacion = afiliacionDAO.buscarPorPaciente(pacienteId);
        if (afiliacion == null) {
            afiliacion = new AfiliacionDePaciente(pacienteId, dni.trim(), numeroAfiliado.trim());
            afiliacionDAO.guardar(afiliacion);
        } else {
            afiliacion.setDni(dni.trim());
            afiliacion.setNumeroAfiliado(numeroAfiliado.trim());
        }
        return afiliacion;
    }

    private AfiliacionDePaciente afiliacionDe(Long pacienteId, Prestacion prestacion) {
        if (prestacion == null) {
            throw new DatosInvalidosException("La prestación es obligatoria.");
        }
        validarPaciente(pacienteId);
        AfiliacionDePaciente afiliacion = afiliacionDAO.buscarPorPaciente(pacienteId);
        if (afiliacion == null) {
            throw new DatosInvalidosException(
                    "El paciente " + pacienteId + " no tiene una obra social registrada.");
        }
        return afiliacion;
    }

    private void validarPaciente(Long pacienteId) {
        if (pacienteId == null) {
            throw new DatosInvalidosException("El id del paciente es obligatorio.");
        }
        Usuario paciente = servicioDeUsuarios.obtenerUsuario(pacienteId);
        if (paciente == null) {
            throw new DatosInvalidosException("No existe un usuario con id " + pacienteId + ".");
        }
        if (!ROL_PACIENTE.equals(paciente.getRol())) {
            throw new DatosInvalidosException(
                    "El usuario " + pacienteId + " no tiene rol " + ROL_PACIENTE + ".");
        }
    }
}
