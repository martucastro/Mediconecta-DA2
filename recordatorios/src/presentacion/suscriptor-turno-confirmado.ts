import { Injectable, Logger, OnApplicationBootstrap, OnApplicationShutdown } from '@nestjs/common';
import { Connection, create_container, EventContext } from 'rhea';
import { ServicioDeRecordatorios, TurnoConfirmado } from '../negocio/servicio-de-recordatorios';

/**
 * Direccion de Artemis del topico JMS TurnoConfirmadoTopic: WildFly le agrega
 * el prefijo "jms.topic." (ver deploy/mediconecta-setup.cli).
 */
const TOPICO = 'jms.topic.TurnoConfirmadoTopic';

/**
 * Contrato entre stacks: ServicioDeTurnos publica un MapMessage JMS y Artemis
 * lo entrega por AMQP 1.0 como un mapa (amqp-value), con las mismas claves.
 * Igual que NotificacionMDB, un mensaje que no se puede leer se descarta:
 * reintentarlo no lo va a arreglar.
 */
export function leerTurnoConfirmado(cuerpo: unknown): TurnoConfirmado | null {
  if (typeof cuerpo !== 'object' || cuerpo === null) {
    return null;
  }
  const { turnoId, pacienteId, fechaHora } = cuerpo as Record<string, unknown>;
  if (!Number.isSafeInteger(turnoId) || !Number.isSafeInteger(pacienteId)
      || typeof fechaHora !== 'string' || Number.isNaN(Date.parse(fechaHora))) {
    return null;
  }
  return { turnoId: turnoId as number, pacienteId: pacienteId as number, fechaHora };
}

/**
 * Equivalente Node.js de un @MessageDriven: se suscribe al topico con una
 * suscripcion durable (contenedor "mediconecta-recordatorios", enlace
 * "recordatorios"), asi que los eventos publicados mientras este proceso esta
 * caido quedan en Artemis y llegan al reconectar. rhea reconecta solo.
 */
@Injectable()
export class SuscriptorTurnoConfirmado implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(SuscriptorTurnoConfirmado.name);
  private conexion?: Connection;

  constructor(private readonly servicio: ServicioDeRecordatorios) {}

  onApplicationBootstrap(): void {
    const clave = process.env.ARTEMIS_CLAVE;
    if (!clave) {
      throw new Error('Falta la variable de entorno ARTEMIS_CLAVE (clave del usuario de aplicacion de WildFly)');
    }
    const contenedor = create_container({ id: 'mediconecta-recordatorios' });
    // Credenciales o permisos incorrectos no se arreglan reintentando: rhea no
    // tiene handler para connection_error ni receiver_error, asi que el
    // proceso termina con el error de Artemis (falla rapido).
    contenedor.on('receiver_open', () => this.log.log(`Suscripcion durable activa en ${TOPICO}`));
    contenedor.on('disconnected', (ctx: EventContext) =>
      this.log.warn(`Sin conexion con Artemis, se reintenta: ${(ctx.error as NodeJS.ErrnoException | undefined)?.code ?? ctx.error ?? 'conexion cerrada'}`));
    contenedor.on('message', (ctx: EventContext) => this.alRecibir(ctx));

    this.conexion = contenedor.connect({
      host: process.env.ARTEMIS_HOST ?? '127.0.0.1',
      port: Number(process.env.ARTEMIS_PUERTO ?? 5672),
      username: process.env.ARTEMIS_USUARIO ?? 'recordatorios',
      password: clave,
    });
    this.conexion.open_receiver({
      name: 'recordatorios',
      source: { address: TOPICO, durable: 2, expiry_policy: 'never', capabilities: ['topic'] },
      autoaccept: false,
    });
  }

  onApplicationShutdown(): void {
    this.conexion?.close();
  }

  private alRecibir(ctx: EventContext): void {
    const evento = leerTurnoConfirmado(ctx.message?.body);
    if (!evento) {
      this.log.warn(`TurnoConfirmado: se descarta un mensaje que no se puede leer: ${JSON.stringify(ctx.message?.body)}`);
      ctx.delivery?.accept();
      return;
    }
    this.log.log(`TurnoConfirmado recibido: turno ${evento.turnoId}, paciente ${evento.pacienteId}, ${evento.fechaHora}`);
    try {
      this.servicio.registrar(evento);
      ctx.delivery?.accept();
    } catch (e) {
      // No se pudo guardar: Artemis lo reentrega con la politica del topico.
      this.log.error(`TurnoConfirmado: no se pudo registrar el turno ${evento.turnoId}`, e as Error);
      ctx.delivery?.release({ delivery_failed: true });
    }
  }
}
