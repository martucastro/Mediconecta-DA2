import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import { api } from '../api'
import { formatearDiaLargo, formatearHora, parsearFechaHora } from '../fechas'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

// Mismos campos que NotificacionDTO en el backend. fechaEnvio es un
// LocalDateTime del servidor: llega como texto ISO, sin zona horaria.
interface Notificacion {
  id: number
  turnoId: number
  canal: string
  mensaje: string
  fechaEnvio: string
}

// fechaEnvio llega con hasta nanosegundos y sin zona: se lee con fechas.ts
// en vez de new Date(texto), que segun el navegador da Invalid Date.
function formatearFecha(fechaEnvio: string): string {
  const fecha = parsearFechaHora(fechaEnvio)
  return fecha ? `${formatearDiaLargo(fecha)}, ${formatearHora(fecha)} hs` : ''
}

// Un error no es una lista vacia: si no se pudo cargar, no se sabe si hay
// notificaciones, asi que no se muestra "No tenés notificaciones".
type Carga =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'lista'; notificaciones: Notificacion[] }

export default function NotificacionesPage() {
  useTitulo('Notificaciones')

  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })

  useEffect(() => {
    let cancelado = false
    api<Notificacion[]>('/notificaciones/mias')
      .then((notificaciones) => {
        if (!cancelado) setCarga({ estado: 'lista', notificaciones })
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setCarga({ estado: 'error', mensaje: err instanceof Error ? err.message : 'No se pudieron cargar las notificaciones' })
      })
    return () => {
      cancelado = true
    }
  }, [])

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/home">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/home">Mis turnos</Link>
          <Link to="/historia">Mi historia clínica</Link>
          <Link to="/notificaciones" aria-current="page">Notificaciones</Link>
          <img className="avatar" src={ASSETS.avatar} alt="" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <h1 className="h1" style={{ fontSize: 30 }}>Notificaciones</h1>

        {carga.estado === 'cargando' ? (
          <p className="muted">Cargando notificaciones...</p>
        ) : carga.estado === 'error' ? (
          <p role="alert" style={{ color: 'var(--accent)' }}>No se pudieron cargar tus notificaciones: {carga.mensaje}</p>
        ) : carga.notificaciones.length === 0 ? (
          <p className="muted">No tenés notificaciones</p>
        ) : (
          // El backend ya ordena de la mas reciente a la mas antigua: se muestra tal cual llega.
          <ul className="appts" style={{ listStyle: 'none', padding: 0 }}>
            {carga.notificaciones.map((notificacion) => (
              <li key={notificacion.id} className="card appt">
                <div className="grow">
                  <p className="appt-name">{notificacion.mensaje}</p>
                  <p className="appt-sub">{formatearFecha(notificacion.fechaEnvio)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  )
}
