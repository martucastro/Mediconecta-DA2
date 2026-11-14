import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import { api } from '../api'
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

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

// fechaEnvio llega con hasta nanosegundos: se recorta a milisegundos, que es lo
// que Date acepta en todos los navegadores (ver HoldPage).
function formatearFecha(fechaEnvio: string): string {
  const fecha = new Date(fechaEnvio.replace(/(\.\d{3})\d+/, '$1'))
  return `${dosDigitos(fecha.getDate())}/${dosDigitos(fecha.getMonth() + 1)}/${fecha.getFullYear()} `
    + `${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`
}

export default function NotificacionesPage() {
  useTitulo('Notificaciones')

  const [notificaciones, setNotificaciones] = useState<Notificacion[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelado = false
    api<Notificacion[]>('/notificaciones/mias')
      .then((lista) => {
        if (cancelado) return
        setNotificaciones(lista)
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setNotificaciones([])
        setError(err instanceof Error ? err.message : 'No se pudieron cargar las notificaciones')
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
          <Link to="/notificaciones" aria-current="page">Notificaciones</Link>
          <img className="avatar" src={ASSETS.avatar} alt="Perfil" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="Perfil" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <h1 className="h1" style={{ fontSize: 30 }}>Notificaciones</h1>

        {error && <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>}

        {notificaciones === null ? (
          <p className="muted">Cargando notificaciones...</p>
        ) : notificaciones.length === 0 ? (
          !error && <p className="muted">No tenés notificaciones</p>
        ) : (
          // El backend ya ordena de la mas reciente a la mas antigua: se muestra tal cual llega.
          <ul className="appts" style={{ listStyle: 'none', padding: 0 }}>
            {notificaciones.map((notificacion) => (
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
