import { describe, expect, it } from 'vitest'
import { aDate, claveDelDia, formatearDiaLargo, formatearFechaCorta, formatearHora, horaSugerida, instante, parsearFechaHora, partesDeCaja, soloFuturos } from './fechas'

describe('parsearFechaHora', () => {
  it('lee un LocalDateTime con segundos como hora local', () => {
    const f = parsearFechaHora('2026-11-18T14:30:00')

    expect(f).toEqual({ anio: 2026, mes: 11, dia: 18, hora: 14, minuto: 30, segundo: 0, milisegundo: 0 })
    // Hora local, no UTC: en cualquier zona horaria el turno de las 14:30 se
    // muestra a las 14:30.
    const fecha = aDate(f!)
    expect(fecha.getHours()).toBe(14)
    expect(fecha.getMinutes()).toBe(30)
    expect(fecha.getDate()).toBe(18)
  })

  it('acepta el formato sin segundos', () => {
    expect(parsearFechaHora('2026-11-18T09:05')).toMatchObject({ hora: 9, minuto: 5, segundo: 0 })
  })

  it('recorta los nanosegundos de inicioHold a milisegundos', () => {
    expect(parsearFechaHora('2026-11-18T14:30:07.123456789')).toMatchObject({ segundo: 7, milisegundo: 123 })
    expect(parsearFechaHora('2026-11-18T14:30:07.5')).toMatchObject({ milisegundo: 500 })
  })

  it('devuelve null si falta el valor', () => {
    expect(parsearFechaHora(null)).toBeNull()
    expect(parsearFechaHora(undefined)).toBeNull()
    expect(parsearFechaHora('')).toBeNull()
  })

  it('rechaza textos que no son un LocalDateTime', () => {
    expect(parsearFechaHora('2026-11-18')).toBeNull()
    expect(parsearFechaHora('18/11/2026 14:30')).toBeNull()
    // Con zona horaria ya no es un LocalDateTime: no se adivina que hacer con ella.
    expect(parsearFechaHora('2026-11-18T14:30:00Z')).toBeNull()
    expect(parsearFechaHora('2026-11-18T14:30:00-03:00')).toBeNull()
  })

  it('rechaza fechas imposibles en vez de correrlas al mes siguiente', () => {
    expect(parsearFechaHora('2026-02-30T10:00:00')).toBeNull()
    expect(parsearFechaHora('2026-13-01T10:00:00')).toBeNull()
    expect(parsearFechaHora('2026-11-18T24:00:00')).toBeNull()
    expect(parsearFechaHora('2026-11-18T10:60:00')).toBeNull()
  })

  it('acepta el 29 de febrero solo en anios bisiestos', () => {
    expect(parsearFechaHora('2028-02-29T10:00:00')).not.toBeNull()
    expect(parsearFechaHora('2026-02-29T10:00:00')).toBeNull()
  })
})

describe('instante', () => {
  it('coincide con el Date local armado por componentes', () => {
    expect(instante('2026-11-18T14:30:00')).toBe(new Date(2026, 10, 18, 14, 30, 0).getTime())
  })

  it('es null para un texto invalido', () => {
    expect(instante('mañana')).toBeNull()
  })
})

describe('formatos', () => {
  const jueves = parsearFechaHora('2026-11-19T08:05:00')!

  it('arma la hora con dos digitos', () => {
    expect(formatearHora(jueves)).toBe('08:05')
  })

  it('arma el dia largo en castellano', () => {
    expect(formatearDiaLargo(jueves)).toBe('jueves 19 de noviembre')
  })

  it('arma la caja de fecha de la tarjeta', () => {
    expect(partesDeCaja(jueves)).toEqual({ diaSemana: 'JUE', dia: '19', mes: 'NOV' })
  })
})

describe('soloFuturos', () => {
  const ahora = new Date(2026, 10, 18, 14, 30, 0).getTime()

  it('descarta los turnos que ya empezaron o empiezan justo ahora', () => {
    const turnos = [
      { id: 1, fechaHora: '2026-11-18T09:00:00' },
      { id: 2, fechaHora: '2026-11-18T14:30:00' },
      { id: 3, fechaHora: '2026-11-18T15:00:00' },
      { id: 4, fechaHora: '2026-11-19T09:00:00' },
    ]

    expect(soloFuturos(turnos, ahora).map((t) => t.id)).toEqual([3, 4])
  })

  it('descarta los que tienen una fecha ilegible', () => {
    expect(soloFuturos([{ fechaHora: 'mañana' }], ahora)).toEqual([])
  })
})

describe('claveDelDia', () => {
  it('arma AAAA-MM-DD en hora local', () => {
    expect(claveDelDia(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })
})

describe('horaSugerida', () => {
  it('propone la proxima hora en punto si el dia es hoy', () => {
    expect(horaSugerida('2026-11-18', new Date(2026, 10, 18, 10, 15))).toBe('11:00')
    expect(horaSugerida('2026-11-18', new Date(2026, 10, 18, 8, 0))).toBe('09:00')
  })

  it('propone las 09:00 para cualquier otro dia', () => {
    expect(horaSugerida('2026-11-19', new Date(2026, 10, 18, 15, 40))).toBe('09:00')
  })

  it('no pasa de las 23:00 aunque ya sea de noche', () => {
    expect(horaSugerida('2026-11-18', new Date(2026, 10, 18, 23, 10))).toBe('23:00')
  })
})

describe('formatearFechaCorta', () => {
  it('muestra dia, mes y anio con dos digitos', () => {
    expect(formatearFechaCorta(parsearFechaHora('2026-03-05T09:00:00')!)).toBe('05/03/2026')
  })
})
