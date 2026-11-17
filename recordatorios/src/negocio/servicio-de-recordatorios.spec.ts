import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { RepositorioDeRecordatorios } from '../datos/repositorio-de-recordatorios';
import { calcularEnvio, recordatoriosVencidos, ServicioDeRecordatorios } from './servicio-de-recordatorios';

const HORA = 60 * 60 * 1000;
const ahora = new Date('2026-11-17T12:00:00');

describe('calcularEnvio', () => {
  it('programa el recordatorio 24 h antes del turno', () => {
    const turno = new Date('2026-11-20T10:00:00');
    expect(calcularEnvio(turno, ahora)).toEqual(new Date('2026-11-19T10:00:00'));
  });

  it('lo envia de inmediato si faltan menos de 24 h', () => {
    expect(calcularEnvio(new Date(ahora.getTime() + 2 * HORA), ahora)).toEqual(ahora);
  });

  it('justo 24 h antes no lo adelanta', () => {
    const turno = new Date(ahora.getTime() + 24 * HORA);
    expect(calcularEnvio(turno, ahora)).toEqual(ahora);
  });
});

describe('recordatoriosVencidos', () => {
  const base = { pacienteId: 1, fechaHoraTurno: '2026-11-20T10:00' };
  it('devuelve solo los pendientes cuya hora ya llego', () => {
    const vencido = { ...base, turnoId: 1, programadoPara: '2026-11-17T11:00:00.000Z', enviadoEn: null };
    const futuro = { ...base, turnoId: 2, programadoPara: '2026-11-19T10:00:00.000Z', enviadoEn: null };
    const enviado = { ...base, turnoId: 3, programadoPara: '2026-11-17T11:00:00.000Z', enviadoEn: '2026-11-17T11:00:00.000Z' };
    expect(recordatoriosVencidos([vencido, futuro, enviado], new Date('2026-11-17T12:00:00.000Z'))).toEqual([vencido]);
  });
});

describe('ServicioDeRecordatorios', () => {
  let carpeta: string;
  let servicio: ServicioDeRecordatorios;

  beforeEach(() => {
    carpeta = mkdtempSync(join(tmpdir(), 'recordatorios-'));
    process.env.RECORDATORIOS_ARCHIVO = join(carpeta, 'recordatorios.json');
    servicio = new ServicioDeRecordatorios(new RepositorioDeRecordatorios());
  });

  afterEach(() => {
    delete process.env.RECORDATORIOS_ARCHIVO;
    rmSync(carpeta, { recursive: true, force: true });
  });

  it('un turno de dentro de 3 dias queda pendiente para 24 h antes', () => {
    const r = servicio.registrar({ turnoId: 7, pacienteId: 3, fechaHora: '2026-11-20T10:00' }, ahora);
    expect(r.programadoPara).toBe(new Date('2026-11-19T10:00:00').toISOString());
    expect(r.enviadoEn).toBeNull();
  });

  it('un turno de dentro de 2 h se envia en el momento', () => {
    const r = servicio.registrar({ turnoId: 8, pacienteId: 3, fechaHora: '2026-11-17T14:00' }, ahora);
    expect(r.enviadoEn).toBe(ahora.toISOString());
  });

  it('la reentrega del mismo evento no duplica el recordatorio', () => {
    servicio.registrar({ turnoId: 9, pacienteId: 3, fechaHora: '2026-11-20T10:00' }, ahora);
    servicio.registrar({ turnoId: 9, pacienteId: 3, fechaHora: '2026-11-20T10:00' }, ahora);
    expect(servicio.listar(9)).toHaveLength(1);
  });

  it('sobrevive a un reinicio: lo persistido se vuelve a leer', () => {
    servicio.registrar({ turnoId: 10, pacienteId: 3, fechaHora: '2026-11-20T10:00' }, ahora);
    const otraInstancia = new ServicioDeRecordatorios(new RepositorioDeRecordatorios());
    expect(otraInstancia.listar(10)).toHaveLength(1);
  });

  it('enviarVencidos marca como enviado lo que ya llego a su hora', () => {
    servicio.registrar({ turnoId: 11, pacienteId: 3, fechaHora: '2026-11-20T10:00' }, ahora);
    servicio.enviarVencidos(new Date('2026-11-19T10:00:00'));
    expect(servicio.listar(11)[0].enviadoEn).toBe(new Date('2026-11-19T10:00:00').toISOString());
  });
});
