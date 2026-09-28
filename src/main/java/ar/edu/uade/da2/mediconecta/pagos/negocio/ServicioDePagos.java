package ar.edu.uade.da2.mediconecta.pagos.negocio;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

import ar.edu.uade.da2.mediconecta.pagos.datos.EstadoPago;
import ar.edu.uade.da2.mediconecta.pagos.datos.Pago;
import ar.edu.uade.da2.mediconecta.pagos.datos.PagoDAO;
import ar.edu.uade.da2.mediconecta.usuarios.negocio.ServicioDeUsuarios;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.RolesAllowed;
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

        // Se registra el intento antes de llamar a la pasarela: si el cobro queda
        // PENDIENTE o la pasarela falla, igual queda rastro del pago iniciado.
        Pago pago = new Pago(datos.getTurnoId(), datos.getMonto(),
                datos.getMoneda().trim().toUpperCase());
        pagoDAO.guardar(pago);

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
        return pagoDAO.buscarPorId(pagoId);
    }

    @Override
    @RolesAllowed({ ServicioDeUsuarios.ROL_PACIENTE, ServicioDeUsuarios.ROL_PROFESIONAL,
            ServicioDeUsuarios.ROL_ADMINISTRADOR })
    public List<Pago> listarPorTurno(Long turnoId) {
        if (turnoId == null) {
            throw new DatosInvalidosException("El id del turno es obligatorio.");
        }
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
}
