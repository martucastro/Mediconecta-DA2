// Las fechas del backend son LocalDateTime de Java serializados por JSON-B:
// "2026-11-18T14:30:00", con segundos y fraccion opcionales (inicioHold trae
// hasta nanosegundos) y SIN zona horaria. No se parsean con new Date(texto):
// segun el formato, el navegador lo toma como UTC o como hora local, y una
// fraccion de mas de 3 digitos da Invalid Date en algunos navegadores. Se
// leen los componentes a mano y se arma el Date con el constructor por
// componentes, que siempre es hora local (servidor y navegador estan en la
// misma zona en el entorno del TP).

export interface FechaHoraLocal {
  anio: number
  /** 1 a 12, como en Java, no 0 a 11 como en Date. */
  mes: number
  dia: number
  hora: number
  minuto: number
  segundo: number
  milisegundo: number
}

const FORMATO = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/

const DIAS_CORTOS = ['DOM', 'LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB']
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']
const MESES_CORTOS = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC']
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

function diasDelMes(anio: number, mes: number): number {
  // Dia 0 del mes siguiente = ultimo dia de este mes.
  return new Date(anio, mes, 0).getDate()
}

/**
 * Lee un LocalDateTime del backend. Devuelve null si falta o no tiene el
 * formato esperado, incluida una fecha imposible como el 30 de febrero, en vez
 * de devolver un Date invalido que despues se muestra como "NaN".
 */
export function parsearFechaHora(texto: string | null | undefined): FechaHoraLocal | null {
  if (!texto) return null
  const partes = FORMATO.exec(texto)
  if (!partes) return null

  const [, anio, mes, dia, hora, minuto, segundo = '0', fraccion = ''] = partes
  const f: FechaHoraLocal = {
    anio: Number(anio),
    mes: Number(mes),
    dia: Number(dia),
    hora: Number(hora),
    minuto: Number(minuto),
    segundo: Number(segundo),
    // Nanosegundos -> milisegundos: alcanza con los tres primeros digitos.
    milisegundo: Number(fraccion.slice(0, 3).padEnd(3, '0')),
  }

  const valida = f.mes >= 1 && f.mes <= 12
    && f.dia >= 1 && f.dia <= diasDelMes(f.anio, f.mes)
    && f.hora <= 23 && f.minuto <= 59 && f.segundo <= 59
  return valida ? f : null
}

/** El instante en hora local del navegador. */
export function aDate(f: FechaHoraLocal): Date {
  return new Date(f.anio, f.mes - 1, f.dia, f.hora, f.minuto, f.segundo, f.milisegundo)
}

/** Milisegundos desde epoch, o null si el texto no es una fecha valida. */
export function instante(texto: string | null | undefined): number | null {
  const f = parsearFechaHora(texto)
  return f ? aDate(f).getTime() : null
}

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

/** "14:30" */
export function formatearHora(f: FechaHoraLocal): string {
  return `${dosDigitos(f.hora)}:${dosDigitos(f.minuto)}`
}

/** "jueves 18 de septiembre" */
export function formatearDiaLargo(f: FechaHoraLocal): string {
  return `${DIAS[aDate(f).getDay()]} ${f.dia} de ${MESES[f.mes - 1]}`
}

/** Lo que muestra la caja de fecha de la tarjeta: JUE / 18 / SEP. */
export function partesDeCaja(f: FechaHoraLocal): { diaSemana: string; dia: string; mes: string } {
  return { diaSemana: DIAS_CORTOS[aDate(f).getDay()], dia: String(f.dia), mes: MESES_CORTOS[f.mes - 1] }
}
