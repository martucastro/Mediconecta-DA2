import { describe, expect, it } from 'vitest'
import { decidirAcceso, rutaInicial, type Sesion } from './api'

const ADMIN: Sesion = { id: 1, nombre: 'Admin', email: 'admin@ejemplo.com', rol: 'ADMINISTRADOR' }

describe('rutaInicial', () => {
  it('lleva a cada rol a su pantalla, el administrador incluido', () => {
    expect(rutaInicial('PACIENTE')).toBe('/home')
    expect(rutaInicial('PROFESIONAL')).toBe('/agenda')
    expect(rutaInicial('ADMINISTRADOR')).toBe('/admin')
  })
})

describe('decidirAcceso', () => {
  it('manda al administrador a su panel si pide una pantalla de otro rol', () => {
    expect(decidirAcceso(ADMIN, ['PACIENTE'])).toBe('/admin')
  })

  it('deja pasar al administrador a su panel', () => {
    expect(decidirAcceso(ADMIN, ['ADMINISTRADOR'])).toBeNull()
  })
})
