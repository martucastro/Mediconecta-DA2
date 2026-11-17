import { instante } from './fechas'
import { type TurnoDTO } from './turnoEnHold'

// Mismo valor que ExpiradorDeHolds.DURACION_HOLD_MS en el backend: solo sirve
// para no ofrecer retomar un hold que ya vencio. El backend es quien lo vence.
export const DURACION_HOLD_MS = 5 * 60 * 1000

/**
 * El turno CONFIRMADO mas cercano que todavia no empezo, o null.
 *
 * GET /turnos/mios le devuelve al paciente sus turnos EN_HOLD y CONFIRMADO,
 * pasados incluidos, asi que se descartan los que ya pasaron. No se confia en
 * el orden de la respuesta: se elige el minimo.
 */
export function proximoConfirmado(turnos: TurnoDTO[], ahora: number): TurnoDTO | null {
  let mejor: TurnoDTO | null = null
  let mejorInstante = Infinity
  for (const turno of turnos) {
    if (turno.estado !== 'CONFIRMADO') continue
    const cuando = instante(turno.fechaHora)
    if (cuando === null || cuando < ahora) continue
    if (cuando < mejorInstante) {
      mejor = turno
      mejorInstante = cuando
    }
  }
  return mejor
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
