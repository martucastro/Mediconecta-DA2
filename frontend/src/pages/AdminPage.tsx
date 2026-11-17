import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import Paginador from './Paginador'
import { api } from '../api'
import { formatearFechaHora, parsearFechaHora } from '../fechas'
import { paginar } from '../paginado'
import { colorEstado, ESTADOS_RECLAMO, textoEstado, type ReclamoDTO } from '../reclamos'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

type Carga =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; reclamos: ReclamoDTO[] }

const TODOS = 'TODOS'

const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

function fechaHora(texto: string): string {
  const f = parsearFechaHora(texto)
  return f ? formatearFechaHora(f) : '—'
}

function detalle(reclamo: ReclamoDTO): string {
  const partes = [
    `${reclamo.intentos} ${reclamo.intentos === 1 ? 'intento' : 'intentos'}`,
    `actualizado el ${fechaHora(reclamo.actualizadoEn)}`,
  ]
  if (reclamo.numeroPresentacion) partes.push(`presentación ${reclamo.numeroPresentacion}`)
  return partes.join(' · ')
}

/**
 * Panel del administrador (SCRUM-106): los reclamos de facturacion a las obras
 * sociales. Solo lectura; el envio y los reintentos los hace el backend.
 */
export default function AdminPage() {
  useTitulo('Reclamos')

  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [recarga, setRecarga] = useState(0)
  const [filtro, setFiltro] = useState(TODOS)
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    let cancelado = false
    api<ReclamoDTO[]>('/reclamos')
      .then((reclamos) => {
        if (!cancelado) setCarga({ estado: 'listo', reclamos })
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setCarga({ estado: 'error', mensaje: err instanceof Error ? err.message : 'No se pudieron cargar los reclamos' })
      })
    return () => {
      cancelado = true
    }
  }, [recarga])

  function actualizar() {
    setCarga({ estado: 'cargando' })
    setRecarga((n) => n + 1)
  }

  function filtrar(estado: string) {
    setFiltro(estado)
    setPagina(0)
  }

  // El mas reciente primero: el id crece con cada reclamo nuevo.
  const reclamos = carga.estado === 'listo' ? [...carga.reclamos].sort((a, b) => b.id - a.id) : []
  const visibles = filtro === TODOS ? reclamos : reclamos.filter((r) => r.estado === filtro)
  const paginaActual = paginar(visibles, pagina)

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/admin">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/admin" aria-current="page">Reclamos</Link>
          <img className="avatar" src={ASSETS.avatar} alt="" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <div className="row between page-head" style={{ alignItems: 'flex-end' }}>
          <div className="stack" style={{ gap: 5 }}>
            <h1 className="h1" style={{ fontSize: 30 }}>Reclamos a obras sociales</h1>
            <p className="muted">Se generan al confirmar un turno con cobertura.</p>
          </div>
          <div className="row" style={{ gap: 10, alignItems: 'flex-end' }}>
            <div className="field">
              <label htmlFor="filtro-estado">ESTADO</label>
              <select className="input" id="filtro-estado" value={filtro} onChange={(e) => filtrar(e.target.value)}>
                <option value={TODOS}>Todos</option>
                {Object.entries(ESTADOS_RECLAMO).map(([valor, { texto }]) => (
                  <option key={valor} value={valor}>{texto}</option>
                ))}
              </select>
            </div>
            <button className="btn" type="button" onClick={actualizar} disabled={carga.estado === 'cargando'}>Actualizar</button>
          </div>
        </div>

        <div className="stack" style={{ gap: 16 }} aria-live="polite" aria-busy={carga.estado === 'cargando'}>
          {carga.estado === 'cargando' && <p className="muted">Cargando los reclamos...</p>}

          {carga.estado === 'error' && (
            <article className="card stack" style={{ gap: 12, padding: 22 }}>
              <p role="alert" style={{ color: 'var(--accent)' }}>{carga.mensaje}</p>
              <button className="btn" type="button" onClick={actualizar} style={{ alignSelf: 'flex-start' }}>Reintentar</button>
            </article>
          )}

          {carga.estado === 'listo' && visibles.length === 0 && (
            <p className="muted">
              {reclamos.length === 0 ? 'Todavía no hay reclamos.' : 'No hay reclamos en este estado.'}
            </p>
          )}

          {visibles.length > 0 && (
            <ul className="appts" style={{ listStyle: 'none', padding: 0 }}>
              {paginaActual.items.map((reclamo) => (
                <li key={reclamo.id} className="card appt">
                  <div className="grow">
                    <p className="appt-name">
                      Turno {reclamo.turnoId} · {reclamo.monto == null ? 'Monto sin informar' : PESOS.format(reclamo.monto)}
                    </p>
                    <p className="appt-sub">{detalle(reclamo)}</p>
                    {reclamo.ultimoError && reclamo.estado !== 'ENVIADO' && (
                      <p className="appt-sub" style={{ color: 'var(--accent)' }}>Último error: {reclamo.ultimoError}</p>
                    )}
                  </div>
                  <span className="status" style={{ background: colorEstado(reclamo.estado) }}>{textoEstado(reclamo.estado)}</span>
                </li>
              ))}
            </ul>
          )}
          <Paginador pagina={paginaActual.pagina} totalPaginas={paginaActual.totalPaginas} onCambiar={setPagina} />
        </div>
      </main>
    </>
  )
}
