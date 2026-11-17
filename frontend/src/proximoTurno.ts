import { instante } from './fechas'
import { type TurnoDTO } from './turnoEnHold'

// Mismo valor que ExpiradorDeHolds.DURACION_HOLD_MS en el backend: solo sirve
// para no ofrecer retomar un hold que ya vencio. El backend es quien lo vence.
export const DURACION_HOLD_MS = 5 * 60 * 1000

/**
 * Los turnos CONFIRMADO que todavia no empezaron, del mas cercano al mas lejano.
 *
 * GET /turnos/mios le devuelve al paciente sus turnos EN_HOLD y CONFIRMADO,
 * pasados incluidos, asi que se descartan los que ya pasaron. No se confia en
 * el orden de la respuesta: se ordena aca.
 */
export function confirmadosPorVenir(turnos: TurnoDTO[], ahora: number): TurnoDTO[] {
  return turnos
    .filter((t) => t.estado === 'CONFIRMADO')
    .map((turno) => ({ turno, cuando: instante(turno.fechaHora) }))
    .filter((x): x is { turno: TurnoDTO; cuando: number } => x.cuando !== null && x.cuando >= ahora)
    .sort((a, b) => a.cuando - b.cuando)
    .map((x) => x.turno)
}

/** Cuando vence el hold del turno, o null si no tiene un inicioHold legible. */
export function vencimientoDelHold(turno: TurnoDTO): number | null {
  const inicio = instante(turno.inicioHold)
  return inicio === null ? null : inicio + DURACION_HOLD_MS
}

/**
 * El turno EN_HOLD que todavia se puede confirmar, o null. Si hubiera mas de
 * uno, el de vencimiento mas lejano, que es el ultimo que se reservo.
 *
 * El barrido del backend corre cada minuto, asi que un hold vencido puede
 * seguir figurando EN_HOLD un rato: se filtra por el vencimiento, no solo por
 * el estado.
 */
export function holdVigente(turnos: TurnoDTO[], ahora: number): TurnoDTO | null {
  let mejor: TurnoDTO | null = null
  let mejorVence = -Infinity
  for (const turno of turnos) {
    if (turno.estado !== 'EN_HOLD') continue
    const vence = vencimientoDelHold(turno)
    if (vence === null || vence <= ahora) continue
    if (vence > mejorVence) {
      mejor = turno
      mejorVence = vence
    }
  }
  return mejor
}
