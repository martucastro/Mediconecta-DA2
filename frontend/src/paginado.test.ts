import { describe, expect, it } from 'vitest'
import { TURNOS_POR_PAGINA, paginar } from './paginado'

const siete = [1, 2, 3, 4, 5, 6, 7]

describe('paginar', () => {
  it('muestra de a 5', () => {
    expect(TURNOS_POR_PAGINA).toBe(5)
    expect(paginar(siete, 0)).toEqual({ items: [1, 2, 3, 4, 5], pagina: 0, totalPaginas: 2 })
  })

  it('la ultima pagina trae lo que sobra', () => {
    expect(paginar(siete, 1)).toEqual({ items: [6, 7], pagina: 1, totalPaginas: 2 })
  })

  it('una pagina fuera de rango se lleva a la ultima', () => {
    // Pasa cuando la lista se achica (por ejemplo, alguien reservo un turno).
    expect(paginar(siete, 9)).toEqual({ items: [6, 7], pagina: 1, totalPaginas: 2 })
    expect(paginar(siete, -1)).toEqual({ items: [1, 2, 3, 4, 5], pagina: 0, totalPaginas: 2 })
  })

  it('una lista vacia tiene una sola pagina vacia', () => {
    expect(paginar([], 0)).toEqual({ items: [], pagina: 0, totalPaginas: 1 })
  })
})
