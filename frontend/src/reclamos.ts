/**
 * Un reclamo de facturacion a la obra social, como lo devuelve GET /reclamos
 * (facturacion.presentacion.ReclamoDTO). JSON-B omite los null: un reclamo
 * que todavia no se envio no trae monto ni numero de presentacion.
 */
export interface ReclamoDTO {
  id: number
  turnoId: number
  pacienteId: number
  numeroAutorizacion?: string
  coberturaPorcentaje?: number
  monto?: number
  estado: string
  intentos: number
  ultimoError?: string
  numeroPresentacion?: string
  creadoEn: string
  actualizadoEn: string
}

/** Los valores de EstadoReclamo, en el orden en que los ofrece el filtro. */
export const ESTADOS_RECLAMO: Record<string, { texto: string; color: string }> = {
  ENVIADO: { texto: 'Enviado', color: 'var(--success)' },
  PENDIENTE: { texto: 'Pendiente de envío', color: 'var(--primary)' },
  EN_REVISION_MANUAL: { texto: 'En revisión manual', color: 'var(--accent)' },
  ANULADO: { texto: 'Anulado', color: 'var(--muted)' },
}

/** El estado en palabras. Uno que el backend agregue despues igual se lee sin guiones bajos. */
export function textoEstado(estado: string): string {
  const conocido = ESTADOS_RECLAMO[estado]?.texto
  if (conocido) return conocido
  const palabras = estado.toLowerCase().replaceAll('_', ' ')
  return palabras.charAt(0).toUpperCase() + palabras.slice(1)
}

export function colorEstado(estado: string): string {
  return ESTADOS_RECLAMO[estado]?.color ?? 'var(--muted)'
}
