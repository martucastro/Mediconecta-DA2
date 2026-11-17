import type { Rol } from './api'
import { instante } from './fechas'

// Mismos campos que EntradaClinicaDTO e HistoriaClinicaDTO en el backend. Una
// entrada trae solo los campos de su tipo: JSON-B omite los nulos.
export type TipoEntrada = 'DIAGNOSTICO' | 'RECETA' | 'ANTECEDENTE'

export interface EntradaDTO {
  id: number
  tipo: TipoEntrada
  profesionalId: number
  fecha: string
  // DIAGNOSTICO
  codigoCIE10?: string
  descripcion?: string
  // RECETA
  medicamento?: string
  dosis?: string
  diasTratamiento?: number
  // ANTECEDENTE
  tipoAntecedente?: string
  detalle?: string
}

export interface HistoriaDTO {
  id: number
  pacienteId: number
  fechaCreacion: string
  entradas: EntradaDTO[]
}

/** Lo que se escribe en cada fila de receta del formulario (todo texto). */
export interface RecetaForm {
  medicamento: string
  dosis: string
  dias: string
}

export const ETIQUETAS: Record<TipoEntrada, string> = {
  DIAGNOSTICO: 'Diagnóstico',
  RECETA: 'Receta',
  ANTECEDENTE: 'Antecedente',
}

/**
 * De que paciente es la historia que se muestra. El paciente ve la suya; si
 * la URL pide otra, se pide igual y el backend responde 403 (la regla vive en
 * ServicioDeHistoriaClinica, no se duplica aca). El profesional necesita que
 * la URL diga cual: llega desde su agenda.
 */
export function pacienteDeLaPagina(rol: Rol, idDeLaSesion: number, pedido: string | null): number | null {
  if (pedido === null) return rol === 'PACIENTE' ? idDeLaSesion : null
  return /^[1-9]\d*$/.test(pedido) ? Number(pedido) : null
}

/**
 * La mas reciente primero. GET /historias/paciente/{id} no garantiza orden. A
 * igual fecha (el diagnostico y las recetas de una consulta) desempata el id.
 */
export function ordenarRecientes(entradas: EntradaDTO[]): EntradaDTO[] {
  return [...entradas].sort((a, b) => (instante(b.fecha) ?? 0) - (instante(a.fecha) ?? 0) || b.id - a.id)
}

export function titulo(entrada: EntradaDTO): string {
  switch (entrada.tipo) {
    case 'DIAGNOSTICO':
      return [entrada.descripcion, entrada.codigoCIE10 && `CIE-10 ${entrada.codigoCIE10}`]
        .filter(Boolean).join(' · ')
    case 'RECETA':
      return [entrada.medicamento, entrada.dosis].filter(Boolean).join(' · ')
    case 'ANTECEDENTE':
      return entrada.tipoAntecedente ?? 'Antecedente'
  }
}

export function cuerpo(entrada: EntradaDTO): string {
  switch (entrada.tipo) {
    case 'RECETA':
      return entrada.diasTratamiento ? `Tratamiento por ${entrada.diasTratamiento} días.` : ''
    case 'ANTECEDENTE':
      return entrada.detalle ?? ''
    default:
      return ''
  }
}

function texto(valor: string): string | null {
  return valor.trim() || null
}

/**
 * Cuerpo de POST /historias/paciente/{id}/consultas. No se valida aca: un
 * campo vacio viaja como null y el 400 del backend (EntradaClinicaFactory)
 * dice cual falta, con el mismo mensaje que vera cualquier otro cliente.
 */
export function armarConsulta(codigoCIE10: string, descripcion: string, recetas: RecetaForm[]) {
  return {
    diagnostico: { codigoCIE10: texto(codigoCIE10), descripcion: texto(descripcion) },
    recetas: recetas.map((r) => ({
      medicamento: texto(r.medicamento),
      dosis: texto(r.dosis),
      diasTratamiento: r.dias.trim() ? Number(r.dias) : null,
    })),
  }
}
