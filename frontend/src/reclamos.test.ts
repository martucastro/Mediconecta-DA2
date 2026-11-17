import { describe, expect, it } from 'vitest'
import { colorEstado, textoEstado } from './reclamos'

describe('textoEstado', () => {
  it('nombra los estados del reclamo en castellano, sin el enum', () => {
    expect(textoEstado('ENVIADO')).toBe('Enviado')
    expect(textoEstado('EN_REVISION_MANUAL')).toBe('En revisión manual')
    expect(textoEstado('PENDIENTE')).toBe('Pendiente de envío')
    expect(textoEstado('ANULADO')).toBe('Anulado')
  })

  it('un estado que el backend agregue despues se lee igual, sin guiones bajos', () => {
    expect(textoEstado('RECHAZADO_POR_LA_OBRA')).toBe('Rechazado por la obra')
  })
})

describe('colorEstado', () => {
  it('distingue el enviado del que necesita revision', () => {
    expect(colorEstado('ENVIADO')).toBe('var(--success)')
    expect(colorEstado('EN_REVISION_MANUAL')).toBe('var(--accent)')
    expect(colorEstado('OTRO')).toBe('var(--muted)')
  })
})
