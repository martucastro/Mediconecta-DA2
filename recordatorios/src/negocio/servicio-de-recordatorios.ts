import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Recordatorio, RepositorioDeRecordatorios } from '../datos/repositorio-de-recordatorios';

/** Evento de dominio que publica ServicioDeTurnos (Java) al confirmar un turno. */
export interface TurnoConfirmado {
  turnoId: number;
  pacienteId: number;
  /** LocalDateTime.toString() de Java: ISO-8601 sin zona, hora local. */
  fechaHora: string;
}

const ANTICIPACION_MS = 24 * 60 * 60 * 1000;
const INTERVALO_DE_REVISION_MS = 60 * 1000;

/** 24 h antes del turno, o ya mismo si el turno es antes de eso. */
export function calcularEnvio(fechaHoraTurno: Date, ahora: Date): Date {
  return new Date(Math.max(fechaHoraTurno.getTime() - ANTICIPACION_MS, ahora.getTime()));
}

export function recordatoriosVencidos(recordatorios: readonly Recordatorio[], ahora: Date): Recordatorio[] {
  return recordatorios.filter((r) => r.enviadoEn === null && new Date(r.programadoPara) <= ahora);
}

/**
 * ServicioDeRecordatorios: tercer suscriptor del topico TurnoConfirmado, junto
 * a NotificacionMDB y TurnoConfirmadoFacturacionMDB, pero escrito en Node.js.
 * Por cada turno confirmado programa un recordatorio para el paciente y lo
 * "envia" (una linea de log, igual que el canal de ServicioDeNotificaciones)
 * cuando llega su hora.
 */
@Injectable()
export class ServicioDeRecordatorios implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(ServicioDeRecordatorios.name);
  private temporizador?: NodeJS.Timeout;

  constructor(private readonly repositorio: RepositorioDeRecordatorios) {}

  onModuleInit(): void {
    this.enviarVencidos();
    this.temporizador = setInterval(() => this.enviarVencidos(), INTERVALO_DE_REVISION_MS);
  }

  onModuleDestroy(): void {
    clearInterval(this.temporizador);
  }

  /** Idempotente por turnoId: una reentrega del mismo evento no duplica nada. */
  registrar(evento: TurnoConfirmado, ahora = new Date()): Recordatorio {
    const existente = this.repositorio.buscarPorTurno(evento.turnoId)[0];
    if (existente) {
      return existente;
    }
    const recordatorio: Recordatorio = {
      turnoId: evento.turnoId,
      pacienteId: evento.pacienteId,
      fechaHoraTurno: evento.fechaHora,
      programadoPara: calcularEnvio(new Date(evento.fechaHora), ahora).toISOString(),
      enviadoEn: null,
    };
    // Si guardar falla, lanza sin haber cambiado nada: el suscriptor libera el
    // mensaje y la reentrega lo vuelve a intentar.
    this.repositorio.agregar(recordatorio);
    this.log.log(`Recordatorio programado para el turno ${recordatorio.turnoId}: se envia el ${recordatorio.programadoPara}`);
    // Ya quedo guardado: enviarVencidos no lanza, asi que el mensaje se acepta.
    this.enviarVencidos(ahora);
    return this.repositorio.buscarPorTurno(evento.turnoId)[0];
  }

  /**
   * No lanza nunca: corre desde un setInterval, donde una excepcion tiraria
   * el proceso. Primero envia y despues marca: si marcar falla, quedan
   * pendientes y se reenvian en la proxima revision (al menos una vez).
   */
  enviarVencidos(ahora = new Date()): void {
    try {
      const vencidos = recordatoriosVencidos(this.repositorio.todos(), ahora);
      if (vencidos.length === 0) {
        return;
      }
      for (const r of vencidos) {
        this.log.log(`Recordatorio enviado al paciente ${r.pacienteId}: tiene un turno el ${r.fechaHoraTurno} (turno ${r.turnoId})`);
      }
      this.repositorio.marcarEnviados(vencidos.map((r) => r.turnoId), ahora.toISOString());
    } catch (e) {
      this.log.error('No se pudo guardar el envio de recordatorios; quedan pendientes para la proxima revision', e as Error);
    }
  }

  listar(turnoId?: number): readonly Recordatorio[] {
    return turnoId === undefined ? this.repositorio.todos() : this.repositorio.buscarPorTurno(turnoId);
  }
}
