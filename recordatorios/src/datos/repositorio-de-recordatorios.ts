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
 * si el proceso muere). La memoria cambia solo despues de que el archivo se
 * escribio: si guardar falla, el repositorio queda como estaba.
 */
// ponytail: archivo JSON entero en memoria; pasar a SQLite si el volumen crece.
@Injectable()
export class RepositorioDeRecordatorios {
  private readonly archivo = process.env.RECORDATORIOS_ARCHIVO ?? 'data/recordatorios.json';
  private recordatorios: Recordatorio[] = leerAlmacen(this.archivo);

  todos(): readonly Recordatorio[] {
    return this.recordatorios;
  }

  buscarPorTurno(turnoId: number): Recordatorio[] {
    return this.recordatorios.filter((r) => r.turnoId === turnoId);
  }

  agregar(recordatorio: Recordatorio): void {
    this.guardar([...this.recordatorios, recordatorio]);
  }

  marcarEnviados(turnoIds: number[], enviadoEn: string): void {
    this.guardar(this.recordatorios.map((r) => (turnoIds.includes(r.turnoId) ? { ...r, enviadoEn } : r)));
  }

  private guardar(nuevos: Recordatorio[]): void {
    mkdirSync(dirname(this.archivo), { recursive: true });
    const temporal = `${this.archivo}.tmp`;
    writeFileSync(temporal, JSON.stringify(nuevos, null, 2));
    renameSync(temporal, this.archivo);
    this.recordatorios = nuevos;
  }
}

/**
 * Un almacen ilegible frena el arranque en vez de tomarse como vacio: si no,
 * la proxima escritura lo pisaria y se perderian los recordatorios.
 */
function leerAlmacen(archivo: string): Recordatorio[] {
  if (!existsSync(archivo)) {
    return [];
  }
  let datos: unknown;
  try {
    datos = JSON.parse(readFileSync(archivo, 'utf8'));
  } catch (e) {
    throw new Error(`El almacen de recordatorios ${archivo} no es JSON valido: ${(e as Error).message}`);
  }
  if (!Array.isArray(datos) || !datos.every(esRecordatorio)) {
    throw new Error(`El almacen de recordatorios ${archivo} no tiene el formato esperado: una lista de recordatorios`);
  }
  return datos;
}

function esRecordatorio(valor: unknown): valor is Recordatorio {
  const r = valor as Recordatorio;
  return typeof valor === 'object' && valor !== null
    && Number.isSafeInteger(r.turnoId) && Number.isSafeInteger(r.pacienteId)
    && typeof r.fechaHoraTurno === 'string' && typeof r.programadoPara === 'string'
    && (r.enviadoEn === null || typeof r.enviadoEn === 'string');
}
