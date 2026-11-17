export const TURNOS_POR_PAGINA = 5

export interface Pagina<T> {
  items: T[]
  pagina: number
  totalPaginas: number
}

// ponytail: paginado en el cliente; la lista de un dia es chica. Pasarlo a la
// API (setFirstResult/setMaxResults) si alguna lista llega a cientos de items.
export function paginar<T>(lista: T[], pagina: number, tamanio = TURNOS_POR_PAGINA): Pagina<T> {
  const totalPaginas = Math.max(1, Math.ceil(lista.length / tamanio))
  const actual = Math.min(Math.max(0, pagina), totalPaginas - 1)
  return { items: lista.slice(actual * tamanio, (actual + 1) * tamanio), pagina: actual, totalPaginas }
}
