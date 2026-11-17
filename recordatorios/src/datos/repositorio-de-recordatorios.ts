import { Injectable } from '@nestjs/common';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

export interface Recordatorio {
  turnoId: number;
  pacienteId: number;
  /** Tal como lo publica ServicioDeTurnos: ISO-8601 local, sin zona. */
  fechaHoraTurno: string;
  /** Instante (ISO-8601 UTC) a partir del cual corresponde enviarlo. */
  programadoPara: string;
  /** null mientras esta pendiente. */
  enviadoEn: string | null;
}

/**
 * Almacen propio de ServicioDeRecordatorios: un archivo JSON, sin tocar las
 * tablas de mediconecta. Se carga entero en memoria al arrancar y se reescribe
 * en cada cambio (archivo temporal + rename, para no dejarlo a medio escribir
 * si el proceso muere).
 */
// ponytail: archivo JSON entero en memoria; pasar a SQLite si el volumen crece.
@Injectable()
export class RepositorioDeRecordatorios {
  private readonly archivo = process.env.RECORDATORIOS_ARCHIVO ?? 'data/recordatorios.json';
  private readonly recordatorios: Recordatorio[] = existsSync(this.archivo)
    ? JSON.parse(readFileSync(this.archivo, 'utf8'))
    : [];

  todos(): Recordatorio[] {
    return this.recordatorios;
  }

  buscarPorTurno(turnoId: number): Recordatorio[] {
    return this.recordatorios.filter((r) => r.turnoId === turnoId);
  }

  agregar(recordatorio: Recordatorio): void {
    this.recordatorios.push(recordatorio);
    this.guardar();
  }

  guardar(): void {
    mkdirSync(dirname(this.archivo), { recursive: true });
    const temporal = `${this.archivo}.tmp`;
    writeFileSync(temporal, JSON.stringify(this.recordatorios, null, 2));
    renameSync(temporal, this.archivo);
  }
}
