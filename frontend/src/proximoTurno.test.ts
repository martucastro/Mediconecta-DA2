import { describe, expect, it } from 'vitest'
import { DURACION_HOLD_MS, holdVigente, proximoConfirmado } from './proximoTurno'
import { type EstadoTurno, type TurnoDTO } from './turnoEnHold'

// "Ahora" fijo y en hora local, para que el resultado no dependa del reloj ni
// de la zona horaria de quien corre los tests.
const AHORA = new Date(2026, 10, 11, 12, 0, 0).getTime()

function turno(id: number, estado: EstadoTurno, fechaHora: string, inicioHold?: string): TurnoDTO {
  return { id, estado, fechaHora, inicioHold }
}

describe('proximoConfirmado', () => {
  it('elige el confirmado mas cercano aunque la respuesta venga desordenada', () => {
    const turnos = [
      turno(1, 'CONFIRMADO', '2026-11-20T10:00:00'),
      turno(2, 'CONFIRMADO', '2026-11-13T09:00:00'),
      turno(3, 'CONFIRMADO', '2026-11-15T16:00:00'),
    ]

    expect(proximoConfirmado(turnos, AHORA)?.id).toBe(2)
  })

  it('descarta los confirmados que ya pasaron', () => {
    const turnos = [
      turno(1, 'CONFIRMADO', '2026-11-10T10:00:00'),
      turno(2, 'CONFIRMADO', '2026-11-11T11:59:00'),
      turno(3, 'CONFIRMADO', '2026-11-12T08:00:00'),
    ]

    expect(proximoConfirmado(turnos, AHORA)?.id).toBe(3)
  })

  it('no confunde un hold con un turno confirmado', () => {
    const turnos = [
      turno(1, 'EN_HOLD', '2026-11-12T08:00:00', '2026-11-11T11:58:00'),
      turno(2, 'CONFIRMADO', '2026-11-14T08:00:00'),
    ]

    expect(proximoConfirmado(turnos, AHORA)?.id).toBe(2)
  })

  it('ignora un turno con la fecha ilegible en vez de romper', () => {
    const turnos = [turno(1, 'CONFIRMADO', 'basura'), turno(2, 'CONFIRMADO', '2026-11-14T08:00:00')]

    expect(proximoConfirmado(turnos, AHORA)?.id).toBe(2)
  })

  it('devuelve null sin turnos confirmados por venir', () => {
    expect(proximoConfirmado([], AHORA)).toBeNull()
    expect(proximoConfirmado([turno(1, 'CONFIRMADO', '2026-11-01T10:00:00')], AHORA)).toBeNull()
  })
})

describe('holdVigente', () => {
  it('devuelve el hold que todavia no vencio', () => {
    // Reservado hace 2 minutos: le quedan 3.
    const turnos = [turno(1, 'EN_HOLD', '2026-11-12T08:00:00', '2026-11-11T11:58:00.123456789')]

    expect(holdVigente(turnos, AHORA)?.id).toBe(1)
  })

  it('descarta un hold vencido que el barrido todavia no libero', () => {
    // Reservado hace 6 minutos: vencio, aunque siga figurando EN_HOLD.
    const turnos = [turno(1, 'EN_HOLD', '2026-11-12T08:00:00', '2026-11-11T11:54:00')]

    expect(holdVigente(turnos, AHORA)).toBeNull()
  })

  it('el vencimiento es exactamente a los cinco minutos', () => {
    const inicio = '2026-11-11T11:55:00'
    const vence = new Date(2026, 10, 11, 11, 55, 0).getTime() + DURACION_HOLD_MS

    expect(holdVigente([turno(1, 'EN_HOLD', '2026-11-12T08:00:00', inicio)], vence - 1)?.id).toBe(1)
    expect(holdVigente([turno(1, 'EN_HOLD', '2026-11-12T08:00:00', inicio)], vence)).toBeNull()
  })

  it('sin inicioHold no ofrece retomar', () => {
    expect(holdVigente([turno(1, 'EN_HOLD', '2026-11-12T08:00:00')], AHORA)).toBeNull()
  })

  it('con dos holds elige el ultimo reservado', () => {
    const turnos = [
      turno(1, 'EN_HOLD', '2026-11-12T08:00:00', '2026-11-11T11:57:00'),
      turno(2, 'EN_HOLD', '2026-11-13T08:00:00', '2026-11-11T11:59:00'),
    ]

    expect(holdVigente(turnos, AHORA)?.id).toBe(2)
  })
})
