import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Recordatorio, RepositorioDeRecordatorios } from './repositorio-de-recordatorios';

const recordatorio: Recordatorio = {
  turnoId: 1, pacienteId: 3, fechaHoraTurno: '2026-11-20T10:00',
  programadoPara: '2026-11-19T13:00:00.000Z', enviadoEn: null,
};

describe('RepositorioDeRecordatorios', () => {
  let carpeta: string;
  let archivo: string;

  beforeEach(() => {
    carpeta = mkdtempSync(join(tmpdir(), 'recordatorios-'));
    archivo = join(carpeta, 'recordatorios.json');
    process.env.RECORDATORIOS_ARCHIVO = archivo;
  });

  afterEach(() => {
    delete process.env.RECORDATORIOS_ARCHIVO;
    rmSync(carpeta, { recursive: true, force: true });
  });

  // Un directorio con el nombre del archivo temporal hace fallar la escritura.
  const romperEscritura = () => mkdirSync(`${archivo}.tmp`);

  it('si no puede guardar, agregar falla y no deja el recordatorio en memoria', () => {
    const repositorio = new RepositorioDeRecordatorios();
    romperEscritura();
    expect(() => repositorio.agregar(recordatorio)).toThrow();
    expect(repositorio.todos()).toEqual([]);
  });

  it('si no puede guardar, marcarEnviados falla y no marca nada en memoria', () => {
    const repositorio = new RepositorioDeRecordatorios();
    repositorio.agregar(recordatorio);
    romperEscritura();
    expect(() => repositorio.marcarEnviados([1], '2026-11-19T13:00:00.000Z')).toThrow();
    expect(repositorio.todos()[0].enviadoEn).toBeNull();
  });

  it('al arrancar rechaza un almacen que no es JSON, nombrando el archivo', () => {
    writeFileSync(archivo, '{ esto no es json');
    expect(() => new RepositorioDeRecordatorios()).toThrow(/recordatorios\.json.*JSON/);
  });

  it.each([
    ['un objeto en vez de una lista', '{"turnoId":1}'],
    ['un elemento sin los campos esperados', '[{"turnoId":"1"}]'],
  ])('al arrancar rechaza un almacen con %s', (_caso, contenido) => {
    writeFileSync(archivo, contenido);
    expect(() => new RepositorioDeRecordatorios()).toThrow(/recordatorios\.json.*formato/);
  });

  it('al arrancar lee un almacen valido', () => {
    writeFileSync(archivo, JSON.stringify([recordatorio]));
    expect(new RepositorioDeRecordatorios().todos()).toEqual([recordatorio]);
  });
});
