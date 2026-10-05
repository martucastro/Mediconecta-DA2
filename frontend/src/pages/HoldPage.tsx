import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'
import { leerTurnoEnHold, limpiarTurnoEnHold, type TurnoDTO } from '../turnoEnHold'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

// Mismo valor que ExpiradorDeHolds.DURACION_HOLD_MS en el backend. El backend
// es quien decide de verdad: si el hold ya vencio, confirmar responde 409.
const DURACION_HOLD_MS = 5 * 60 * 1000

const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']

const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 })

type Estado = 'reservado' | 'enviando' | 'confirmado' | 'vencido'

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

// inicioHold es un LocalDateTime del servidor: hora local sin zona horaria y
// con hasta nanosegundos. Se recorta a milisegundos, que es lo que Date acepta
// en todos los navegadores, y se interpreta como hora local (servidor y
// navegador estan en la misma zona en el entorno del TP).
function vencimientoDelHold(inicioHold: string): number {
  const inicio = new Date(inicioHold.replace(/(\.\d{3})\d+/, '$1'))
  return inicio.getTime() + DURACION_HOLD_MS
}

function segundosRestantes(vence: number): number {
  return Math.max(0, Math.ceil((vence - Date.now()) / 1000))
}

function formatearFecha(fechaHora: string): string {
  const fecha = new Date(fechaHora)
  return `${DIAS[fecha.getDay()]} ${dosDigitos(fecha.getDate())}/${dosDigitos(fecha.getMonth() + 1)} · ${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())} hs`
}

function formatearCuenta(segundos: number): string {
  return `${dosDigitos(Math.floor(segundos / 60))}:${dosDigitos(segundos % 60)}`
}

function textoModalidad(turno: TurnoDTO): string {
  if (turno.modalidad === 'TELEMEDICINA') return 'Telemedicina'
  return turno.consultorio ? `Presencial · ${turno.consultorio}` : 'Presencial'
}

function textoCobertura(turno: TurnoDTO): string {
  if (turno.coberturaAutorizada) return `Autorizada · ${turno.coberturaPorcentaje ?? 0} %`
  return 'Sin cobertura (particular)'
}

export default function HoldPage() {
  useTitulo('Confirmar turno')
  const navigate = useNavigate()

  // El turno lo guarda la pantalla de disponibilidad al reservar (SCRUM-84):
  // no hay un GET de un turno individual.
  const [turno] = useState<TurnoDTO | null>(() => leerTurnoEnHold())
  const [vence] = useState(() => (turno?.inicioHold ? vencimientoDelHold(turno.inicioHold) : 0))
  const [restante, setRestante] = useState(() => segundosRestantes(vence))
  const [estado, setEstado] = useState<Estado>(() => (segundosRestantes(vence) > 0 ? 'reservado' : 'vencido'))
  const [error, setError] = useState('')
  // Guarda sincronica contra el doble clic: el estado de React recien cambia en
  // el proximo render, y dos clics seguidos llegan antes de que el boton se
  // deshabilite. Sin esto se mandan dos PUT y el 409 del segundo podria pisar
  // el "confirmado" del primero.
  const enviando = useRef(false)

  // La cuenta regresiva solo corre mientras el turno sigue reservado.
  useEffect(() => {
    if (estado !== 'reservado') return
    const intervalo = setInterval(() => {
      const segundos = segundosRestantes(vence)
      setRestante(segundos)
      if (segundos === 0) {
        setEstado('vencido')
        limpiarTurnoEnHold()
      }
    }, 1000)
    return () => clearInterval(intervalo)
  }, [estado, vence])

  if (!turno) return <Navigate to="/disponibilidad" replace />

  async function confirmar() {
    if (!turno || estado !== 'reservado' || enviando.current) return
    enviando.current = true
    setEstado('enviando')
    setError('')
    try {
      await api<TurnoDTO>(`/turnos/${turno.id}/confirmar`, { method: 'PUT' })
      limpiarTurnoEnHold()
      setEstado('confirmado')
    } catch (err) {
      if (err instanceof ApiError && err.estado === 409) {
        // El hold vencio (o el turno ya no esta reservado): se informa y se
        // vuelve a elegir turno.
        limpiarTurnoEnHold()
        setEstado('vencido')
      } else {
        setEstado('reservado')
      }
      setError(err instanceof Error ? err.message : 'No se pudo confirmar el turno')
    } finally {
      enviando.current = false
    }
  }

  async function liberar() {
    if (!turno || estado !== 'reservado' || enviando.current) return
    enviando.current = true
    setEstado('enviando')
    setError('')
    try {
      await api<TurnoDTO>(`/turnos/${turno.id}/cancelar`, { method: 'PUT' })
    } catch (err) {
      // Un 409 es que el hold ya habia vencido: el turno igual quedo libre.
      // Cualquier otro error (por ejemplo, sin conexion) deja el turno
      // retenido, asi que se informa en vez de salir como si se hubiera liberado.
      if (!(err instanceof ApiError && err.estado === 409)) {
        setEstado('reservado')
        setError(err instanceof Error ? err.message : 'No se pudo liberar el turno')
        enviando.current = false
        return
      }
    }
    limpiarTurnoEnHold()
    navigate('/disponibilidad', { replace: true })
  }

  const porcentaje = Math.round((restante * 1000 * 100) / DURACION_HOLD_MS)
  const confirmado = estado === 'confirmado'
  const vencido = estado === 'vencido'

  return (
    <>
      <header className="topbar">
        <Link className="wordmark d" to="/home">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <Link className="m" to="/disponibilidad" style={{ fontWeight: 500 }}>← Turno</Link>
        <img className="avatar d" src={ASSETS.avatar} alt="Perfil" />
        <img className="m" src={ASSETS.logo} alt="MediConecta" style={{ width: 18, height: 18 }} />
      </header>

      <main className="stage">
        <article className="card hold">
          <div className="counter" role="timer" aria-live="off">
            {confirmado ? (
              <>
                <span className="label-caps">TURNO CONFIRMADO</span>
                <p>Te vamos a avisar por notificación. Podés verlo en tu inicio.</p>
              </>
            ) : vencido ? (
              <>
                <span className="label-caps">LA RESERVA VENCIÓ</span>
                <time>00:00</time>
                <p>El turno volvió a quedar disponible. Elegí otro horario para reservar.</p>
              </>
            ) : (
              <>
                <span className="label-caps">TU TURNO ESTÁ RESERVADO</span>
                <time aria-label={`Quedan ${formatearCuenta(restante)} para confirmar`}>{formatearCuenta(restante)}</time>
                <div className="progress"><div style={{ width: `${porcentaje}%` }}></div></div>
                <p>
                  Si no confirmás <span className="d">antes de que llegue a cero</span><span className="m">a tiempo</span>, el turno vuelve a quedar disponible<span className="d"> para otro paciente</span>.
                </p>
              </>
            )}
          </div>

          <section className="summary">
            <h1 className="summary-title"><span className="d">{confirmado ? 'Tu turno' : 'Revisá antes de confirmar'}</span><span className="m">{turno.profesionalNombre}</span></h1>
            <dl className="details">
              <div className="d"><dt>Profesional</dt><dd>{turno.profesionalNombre}</dd></div>
              <div><dt>Fecha<span className="d"> y hora</span></dt><dd>{formatearFecha(turno.fechaHora)}</dd></div>
              <div><dt>Modalidad</dt><dd>{textoModalidad(turno)}</dd></div>
              <div><dt>Cobertura</dt><dd className={turno.coberturaAutorizada ? 'ok' : undefined}>{textoCobertura(turno)}</dd></div>
              <div>
                <dt>Copago<span className="d"> a abonar en la consulta</span></dt>
                <dd className="strong">{turno.copago == null ? '—' : PESOS.format(turno.copago)}</dd>
              </div>
            </dl>
          </section>

          {error && <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>}

          <div className="actions">
            {confirmado ? (
              <Link className="btn btn-primary" to="/home">Volver al inicio</Link>
            ) : vencido ? (
              <Link className="btn btn-primary" to="/disponibilidad" replace>Elegir otro turno</Link>
            ) : (
              <>
                <button className="btn btn-primary" type="button" onClick={confirmar} disabled={estado !== 'reservado'}>
                  {estado === 'enviando' ? 'Confirmando...' : 'Confirmar turno'}
                </button>
                <button className="btn" type="button" onClick={liberar} disabled={estado !== 'reservado'}>Liberar el turno</button>
              </>
            )}
          </div>
        </article>
      </main>
    </>
  )
}
