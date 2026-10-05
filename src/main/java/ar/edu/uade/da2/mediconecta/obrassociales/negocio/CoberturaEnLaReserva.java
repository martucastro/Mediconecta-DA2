package ar.edu.uade.da2.mediconecta.obrassociales.negocio;

import java.math.BigDecimal;

import ar.edu.uade.da2.mediconecta.turnos.datos.ModalidadTurno;
import ar.edu.uade.da2.mediconecta.turnos.datos.Turno;
import ar.edu.uade.da2.mediconecta.turnos.negocio.PuntosDeExtension;
import ar.edu.uade.da2.mediconecta.turnos.negocio.TurnoEnReserva;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Paso 4 del caso de uso: antes de retener el turno, consulta la cobertura del
 * paciente y deja en el turno lo que corresponde pagar.
 *
 * Observa TurnoEnReserva, así que ServicioDeTurnos no conoce a este componente.
 * Corre en la transacción de la reserva y no atrapa nada: si la obra social no
 * responde, la excepción (con rollback) revierte la reserva completa y el turno
 * sigue disponible. Ver docs/documento-tecnico.md, sección 9.2.
 */
@ApplicationScoped
public class CoberturaEnLaReserva {

    @Inject
    private ServicioDeObrasSociales obrasSociales;

    @Transactional(TxType.MANDATORY)
    public void alReservar(@Observes @Priority(PuntosDeExtension.COBERTURA) TurnoEnReserva evento) {
        Turno turno = evento.getTurno();
        Cobertura cobertura = obrasSociales.cotizarReserva(
                turno.getPaciente().getId(), prestacionDe(turno));

        turno.setCoberturaAutorizada(cobertura.autorizada());
        turno.setCoberturaPorcentaje(BigDecimal.valueOf(cobertura.porcentaje()));
        turno.setCopago(cobertura.copago());
        turno.setNumeroAutorizacion(cobertura.numeroAutorizacion());
    }

    private static Prestacion prestacionDe(Turno turno) {
        return turno.getModalidad() == ModalidadTurno.TELEMEDICINA
                ? Prestacion.TELECONSULTA : Prestacion.CONSULTA;
    }
}
