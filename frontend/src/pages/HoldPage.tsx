import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'
import { aDate, formatearHora, parsearFechaHora } from '../fechas'
import { DURACION_HOLD_MS, vencimientoDelHold } from '../proximoTurno'
import { leerTurnoEnHold, limpiarTurnoEnHold, type TurnoDTO } from '../turnoEnHold'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'
import UnirseAConsulta from './UnirseAConsulta'

const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']

const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 })

// El copago se cobra al confirmar (SCRUM-93) con el token del medio de pago,
// nunca con el numero de tarjeta. Con una pasarela real el token lo devuelve su
// formulario; la simulada (PasarelaExternaResource) aprueba cualquier token
// salvo los que empiezan con "rechazar" (409) o "caer" (pasarela caida, 502).
const MEDIOS_DE_PAGO = [
  { token: 'tok_visa_demo', nombre: 'Tarjeta de prueba (se aprueba)' },
  { token: 'rechazar_demo', nombre: 'Tarjeta de prueba (se rechaza)' },
  { token: 'caer_demo', nombre: 'Simular pasarela caída' },
]

type Estado = 'reservado' | 'enviando' | 'confirmado' | 'vencido'

// Un 403 al confirmar o liberar es que el hold es de otra cuenta (por ejemplo,
// se inicio sesion con otro paciente en la misma pestana): reintentar no sirve,
// asi que se trata como un hold vencido.
const RESERVA_AJENA = 'Esta reserva no es de tu cuenta. Elegí otro horario para reservar.'

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

function segundosRestantes(vence: number): number {
  return Math.max(0, Math.ceil((vence - Date.now()) / 1000))
}

function formatearFecha(fechaHora: string): string {
  const f = parsearFechaHora(fechaHora)
  if (!f) return '—'
  return `${DIAS[aDate(f).getDay()]} ${dosDigitos(f.dia)}/${dosDigitos(f.mes)} · ${formatearHora(f)} hs`
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
  // El backend es quien decide de verdad: si el hold ya vencio, confirmar responde 409.
  const [vence] = useState(() => (turno ? vencimientoDelHold(turno) ?? 0 : 0))
  const [restante, setRestante] = useState(() => segundosRestantes(vence))
  const [estado, setEstado] = useState<Estado>(() => (segundosRestantes(vence) > 0 ? 'reservado' : 'vencido'))
  const [error, setError] = useState('')
  const [tokenMedioDePago, setTokenMedioDePago] = useState(MEDIOS_DE_PAGO[0].token)
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
      // El token viaja siempre: sin copago el backend lo ignora.
      await api<TurnoDTO>(`/turnos/${turno.id}/confirmar`, {
        method: 'PUT',
        body: JSON.stringify({ tokenMedioDePago }),
      })
      limpiarTurnoEnHold()
      setEstado('confirmado')
    } catch (err) {
      if (err instanceof ApiError && err.estado === 403) {
        limpiarTurnoEnHold()
        setEstado('vencido')
        setError(RESERVA_AJENA)
        return
      }
      // Un 409 con el hold todavia corriendo es un copago rechazado: la
      // confirmacion se revirtio, el turno sigue reservado y se puede reintentar
      // con otro medio de pago (igual que tras un 502 o un 400).
      // ponytail: se distingue por la cuenta regresiva del navegador; si el
      // servidor vence el hold unos segundos antes, se ve su mensaje y la cuenta
      // llega a cero sola.
      if (err instanceof ApiError && err.estado === 409 && segundosRestantes(vence) === 0) {
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
      if (err instanceof ApiError && err.estado === 403) {
        limpiarTurnoEnHold()
        setEstado('vencido')
        setError(RESERVA_AJENA)
        enviando.current = false
        return
      }
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
    navigate('/disponibilidad', { replace: true, state: { profesionalId: turno.profesionalId } })
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
        <img className="avatar d" src={ASSETS.avatar} alt="" />
        <img className="m" src={ASSETS.logo} alt="MediConecta" style={{ width: 18, height: 18 }} />
      </header>

      <main className="stage">
        <article className="card hold">
          <div className="counter" role="timer" aria-live="off">
            {confirmado ? (
              <>
                <span className="label-caps">TURNO CONFIRMADO</span>
                <p>Podés verlo en tu inicio.</p>
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
                <dt>Copago<span className="d"> a abonar al confirmar</span></dt>
                <dd className="strong">{turno.copago == null ? '—' : PESOS.format(turno.copago)}</dd>
              </div>
            </dl>
          </section>

          {!confirmado && !vencido && (turno.copago ?? 0) > 0 && (
            <div className="field">
              <label htmlFor="medio-de-pago">MEDIO DE PAGO</label>
              <select className="input" id="medio-de-pago" value={tokenMedioDePago} onChange={(e) => setTokenMedioDePago(e.target.value)} disabled={estado !== 'reservado'}>
                {MEDIOS_DE_PAGO.map((medio) => (
                  <option key={medio.token} value={medio.token}>{medio.nombre}</option>
                ))}
              </select>
            </div>
          )}

          {error && <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>}

          <div className="actions">
            {confirmado ? (
              <>
                {/* La sala se crea dentro de la confirmacion (SCRUM-95): un turno
                    de telemedicina confirmado ya tiene su enlace (SCRUM-104). */}
                {turno.modalidad === 'TELEMEDICINA' && <UnirseAConsulta turnoId={turno.id} />}
                <Link className={turno.modalidad === 'TELEMEDICINA' ? 'btn' : 'btn btn-primary'} to="/home">Volver al inicio</Link>
              </>
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
