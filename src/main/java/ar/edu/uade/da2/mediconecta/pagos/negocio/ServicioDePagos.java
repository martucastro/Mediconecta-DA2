package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.comun.negocio.ConflictoDeNegocioException;
import ar.edu.uade.da2.mediconecta.comun.negocio.DatosInvalidosException;
import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.ServicioDeTurnos;
import ar.edu.uade.da2.mediconecta.usuarios.datos.Usuario;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Patrón Facade: única puerta de entrada al componente de pagos.
 *
 * Es stateless porque cada operación es autocontenida: no hay conversación con el
 * cliente que haya que recordar entre llamadas. El estado del cobro es durable en
 * la fila de Pago, no conversacional en el bean.
 *
 * La integración externa (llamada REST a la pasarela) no vive acá sino detrás del
 * Adapter PasarelaDePagoAdapter: la fachada orquesta y persiste, pero no conoce el
 * formato del proveedor.
 */
@Stateless
@RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_ADMINISTRADOR })
public class ServicioDePagos implements ServicioDePagosLocal {

    private static final Logger LOGGER = Logger.getLogger(ServicioDePagos.class.getName());

    private static final int MAX_MONEDA = 3;

    @Inject
    private PagoDAO pagoDAO;

    @Inject
    private PasarelaDePagoAdapter pasarela;

    // Identifica al usuario autenticado. @RolesAllowed no alcanza para las reglas
    // que dependen de *cual* pago o turno se esta operando.
    @Resource
    private SessionContext contexto;

    // Dependencias hacia otros componentes, siempre a traves de su fachada:
    // ServicioDeUsuarios resuelve al caller; ServicioDeTurnos dice de quien es el
    // turno. Nunca se consultan sus tablas directamente desde aca.
    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @PostConstruct
    public void inicializar() {
        LOGGER.info("ServicioDePagos: instancia creada por el contenedor.");
    }

    @PreDestroy
    public void liberar() {
        LOGGER.info("ServicioDePagos: instancia destruida por el contenedor.");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Pago cobrar(CobroDTO datos) {
        validarCobro(datos);
        // Solo se puede cobrar un turno propio: el paciente tiene que ser quien lo
        // reservo. Sin esto, con el rol PACIENTE bastaba para pagar un turno ajeno.
        verificarQueElTurnoEsDelCaller(datos.getTurnoId());

        // Se registra el intento en su propia transaccion (REQUIRES_NEW) antes de
        // llamar a la pasarela: si esa llamada falla y hace rollback del cobro, el
        // rastro del pago (en estado PENDIENTE) igual sobrevive. Con la transaccion
        // unica del metodo, el rollback tambien se lo llevaria.
        Pago pago = new Pago(datos.getTurnoId(), datos.getMonto(),
                datos.getMoneda().trim().toUpperCase());
        pagoDAO.guardarEnNuevaTransaccion(pago);

        ResultadoPasarela resultado = pasarela.cobrar(
                pago.getMonto(), pago.getMoneda(), datos.getTokenMedioDePago());

        pago.setEstado(resultado.getEstado());
        pago.setIdTransaccionExterna(resultado.getIdTransaccionExterna());
        return pagoDAO.actualizar(pago);
    }

    @Override
    @RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL,
            ServicioDeUsuarios.ROL_ADMINISTRADOR })
    public Pago consultarPago(Long pagoId) {
        if (pagoId == null) {
            throw new DatosInvalidosException("El id del pago es obligatorio.");
        }
        Pago pago = pagoDAO.buscarPorId(pagoId);
        // Un paciente solo puede ver el pago de un turno suyo. Se verifica solo si
        // el pago existe: si no, el resource ya responde 404 sin filtrar nada.
        if (pago != null) {
            verificarQueElTurnoEsDelCaller(pago.getTurnoId());
        }
        return pago;
    }

    @Override
    @RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL,
            ServicioDeUsuarios.ROL_ADMINISTRADOR })
    public List<Pago> listarPorTurno(Long turnoId) {
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio.");
        }
        verificarQueElTurnoEsDelCaller(turnoId);
        return pagoDAO.listarPorTurno(turnoId);
    }

    @Override
    @RolesAllowed(ServicioDeUsuarios.ROL_ADMINISTRADOR)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Pago reembolsar(Long pagoId) {
        Pago pago = consultarPago(pagoId);
        if (pago == null) {
            throw new DatosInvalidosException("No existe un pago con id " + pagoId + ".");
        }
        if (pago.getEstado() != EstadoPago.APROBADO) {
            throw new ConflictoDeNegocioException(
                    "Solo se puede reembolsar un pago APROBADO; el pago " + pagoId
                            + " está en estado " + pago.getEstado() + ".");
        }

        ResultadoPasarela resultado = pasarela.reembolsar(pago.getIdTransaccionExterna());
        pago.setEstado(resultado.getEstado());
        return pagoDAO.actualizar(pago);
    }

    private void validarCobro(CobroDTO datos) {
        if (datos == null) {
            throw new DatosInvalidosException("Los datos del cobro son obligatorios.");
        }
        if (datos.getTurnoId() == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio.");
        }
        if (datos.getMonto() == null || datos.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DatosInvalidosException("El monto debe ser mayor a cero.");
        }
        if (datos.getMoneda() == null || datos.getMoneda().isBlank()) {
            throw new DatosInvalidosException("La moneda es obligatoria (por ejemplo ARS).");
        }
        if (datos.getMoneda().trim().length() != MAX_MONEDA) {
            throw new DatosInvalidosException(
                    "La moneda debe ser un código ISO de 3 letras (por ejemplo ARS, USD).");
        }
        if (datos.getTokenMedioDePago() == null || datos.getTokenMedioDePago().isBlank()) {
            throw new DatosInvalidosException("El token del medio de pago es obligatorio.");
        }
    }

    private Usuario usuarioAutenticado() {
        String email = contexto.getCallerPrincipal().getName();
        Usuario usuario = servicioDeUsuarios.obtenerPorEmail(email);
        if (usuario == null) {
            throw new EJBAccessException("No se pudo resolver el usuario autenticado: " + email);
        }
        return usuario;
    }

    /**
     * Autorizacion que depende del dato, no del rol.
     *
     * Un administrador opera sobre cualquier pago. Para el resto, el turno tiene
     * que ser suyo: el paciente que lo reservo o el profesional que lo atiende.
     *
     * No se puede expresar con @RolesAllowed porque esa anotacion no ve los
     * argumentos del metodo: sabe que el caller es PACIENTE, no que este turno sea
     * suyo. Se resuelve el turno a traves de la fachada de ServicioDeTurnos, no
     * consultando su tabla. Mismo criterio que ServicioDeTurnos e
     * ServicioDeHistoriaClinica.
     */
    private void verificarQueElTurnoEsDelCaller(Long turnoId) {
        if (contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)) {
            return;
        }
        Turno turno = servicioDeTurnos.obtenerTurno(turnoId);
        if (turno == null) {
            throw new DatosInvalidosException("No existe el turno " + turnoId + ".");
        }
        Usuario caller = usuarioAutenticado();
        boolean esElPaciente = turno.getPaciente() != null
                && caller.getId().equals(turno.getPaciente().getId());
        boolean esElProfesional = turno.getProfesional() != null
                && caller.getId().equals(turno.getProfesional().getId());
        if (!esElPaciente && !esElProfesional) {
            throw new EJBAccessException(
                    "El turno " + turnoId + " pertenece a otro paciente.");
        }
    }
}
