// Handoff hacia la pantalla de hold (SCRUM-85): no existe un GET de un turno
// individual, asi que el TurnoDTO EN_HOLD que devuelve el POST /api/turnos se
// guarda aca para que esa pantalla lo lea. Mismo patron que guardarSesion/
// obtenerSesion en api.ts: sessionStorage, validado al leer.

export type EstadoTurno = 'DISPONIBLE' | 'EN_HOLD' | 'CONFIRMADO' | 'CANCELADO'
export type ModalidadTurno = 'PRESENCIAL' | 'TELEMEDICINA'

export interface TurnoDTO {
  id: number
  fechaHora: string
  estado: EstadoTurno
  profesionalId: number | null
  profesionalNombre: string | null
  pacienteId: number | null
  pacienteNombre: string | null
  inicioHold: string | null
  modalidad: ModalidadTurno | null
  consultorio: string | null
  coberturaAutorizada: boolean | null
  coberturaPorcentaje: number | null
  copago: number | null
  numeroAutorizacion: string | null
}

const CLAVE_TURNO_EN_HOLD = 'mediconecta_turno_en_hold'

export function guardarTurnoEnHold(turno: TurnoDTO) {
  sessionStorage.setItem(CLAVE_TURNO_EN_HOLD, JSON.stringify(turno))
}

function esTurnoEnHold(valor: unknown): valor is TurnoDTO {
  if (typeof valor !== 'object' || valor === null) return false
  const t = valor as Record<string, unknown>
  return typeof t.id === 'number' && typeof t.fechaHora === 'string' && t.estado === 'EN_HOLD'
}

export function leerTurnoEnHold(): TurnoDTO | null {
  const raw = sessionStorage.getItem(CLAVE_TURNO_EN_HOLD)
  if (!raw) return null
  try {
    const valor: unknown = JSON.parse(raw)
    if (esTurnoEnHold(valor)) return valor
  } catch {
    // JSON invalido: se trata igual que si no hubiera turno guardado
  }
  // Quedo algo que no es un hold valido (por ejemplo editado a mano, o un
  // turno que ya no esta EN_HOLD): se descarta en vez de pasarlo a la pantalla.
  limpiarTurnoEnHold()
  return null
}

export function limpiarTurnoEnHold() {
  sessionStorage.removeItem(CLAVE_TURNO_EN_HOLD)
}
