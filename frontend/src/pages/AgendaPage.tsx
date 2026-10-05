import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import { api, obtenerSesion } from '../api'
import { type ModalidadTurno, type TurnoDTO } from '../turnoEnHold'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

// Mismo valor que ExpiradorDeHolds.DURACION_HOLD_MS en el backend: solo sirve
// para mostrar cuanto le queda a un hold, el backend es quien lo vence.
const DURACION_HOLD_MS = 5 * 60 * 1000

const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

/** AAAA-MM-DD en hora local: el formato que espera ?fecha= y el input date. */
function claveDelDia(fecha: Date): string {
  return `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}`
}

function moverDias(clave: string, dias: number): string {
  const fecha = new Date(`${clave}T00:00:00`)
  fecha.setDate(fecha.getDate() + dias)
  return claveDelDia(fecha)
}

function titulo(clave: string): string {
  const fecha = new Date(`${clave}T00:00:00`)
  return `${DIAS[fecha.getDay()]} ${fecha.getDate()} de ${MESES[fecha.getMonth()]}`
}

function hora(fechaHora: string): string {
  const fecha = new Date(fechaHora)
  return `${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`
}

// inicioHold llega con hasta nanosegundos: se recorta a milisegundos.
function restanteDelHold(inicioHold: string, ahora: number): string {
  const vence = new Date(inicioHold.replace(/(\.\d{3})\d+/, '$1')).getTime() + DURACION_HOLD_MS
  const segundos = Math.max(0, Math.ceil((vence - ahora) / 1000))
  return `${dosDigitos(Math.floor(segundos / 60))}:${dosDigitos(segundos % 60)}`
}

function detalle(turno: TurnoDTO): string {
  const lugar = turno.modalidad === 'TELEMEDICINA'
    ? 'Telemedicina'
    : turno.consultorio ? `Presencial · ${turno.consultorio}` : 'Presencial'
  if (turno.estado === 'DISPONIBLE') return `${lugar} · Horario libre`
  const cobertura = turno.coberturaAutorizada ? `Cobertura ${turno.coberturaPorcentaje ?? 0} %` : 'Particular'
  return `${lugar} · ${cobertura}`
}

function etiquetaEstado(turno: TurnoDTO, ahora: number): { texto: string; color: string } {
  switch (turno.estado) {
    case 'CONFIRMADO':
      return { texto: 'CONFIRMADO', color: 'var(--success)' }
    case 'EN_HOLD':
      return {
        texto: turno.inicioHold ? `EN HOLD · ${restanteDelHold(turno.inicioHold, ahora)}` : 'EN HOLD',
        color: 'var(--accent)',
      }
    case 'CANCELADO':
      return { texto: 'CANCELADO', color: 'var(--muted)' }
    default:
      return { texto: 'LIBRE', color: 'var(--primary)' }
  }
}

export default function AgendaPage() {
  useTitulo('Mi agenda')
  const sesion = obtenerSesion()

  const [dia, setDia] = useState(() => claveDelDia(new Date()))
  const [turnos, setTurnos] = useState<TurnoDTO[] | null>(null)
  const [errorLista, setErrorLista] = useState('')
  const [recarga, setRecarga] = useState(0)
  const [ahora, setAhora] = useState(() => Date.now())

  const [horaNueva, setHoraNueva] = useState('09:00')
  const [modalidad, setModalidad] = useState<ModalidadTurno>('PRESENCIAL')
  const [consultorio, setConsultorio] = useState('')
  const [errorAlta, setErrorAlta] = useState('')
  const [abriendo, setAbriendo] = useState(false)
  // Guarda sincronica contra el doble envio del formulario (ver HoldPage).
  const enviando = useRef(false)

  useEffect(() => {
    let cancelado = false
    api<TurnoDTO[]>(`/turnos/mios?fecha=${dia}`)
      .then((delDia) => {
        if (cancelado) return
        setTurnos(delDia)
        setErrorLista('')
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setTurnos([])
        setErrorLista(err instanceof Error ? err.message : 'No se pudo cargar la agenda')
      })
    return () => {
      cancelado = true
    }
  }, [dia, recarga])

  // Solo hace falta un reloj si hay algun hold que mostrar descontando.
  const hayHolds = turnos?.some((t) => t.estado === 'EN_HOLD') ?? false
  useEffect(() => {
    if (!hayHolds) return
    const intervalo = setInterval(() => setAhora(Date.now()), 1000)
    return () => clearInterval(intervalo)
  }, [hayHolds])

  function cambiarDia(nuevo: string) {
    setTurnos(null)
    setDia(nuevo)
  }

  async function abrirHorario(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (enviando.current) return
    enviando.current = true
    setAbriendo(true)
    setErrorAlta('')
    try {
      await api<TurnoDTO>('/turnos/disponibilidad', {
        method: 'POST',
        body: JSON.stringify({
          fechaHora: `${dia}T${horaNueva}:00`,
          modalidad,
          consultorio: modalidad === 'PRESENCIAL' && consultorio.trim() ? consultorio.trim() : null,
        }),
      })
      setRecarga((n) => n + 1)
    } catch (err) {
      setErrorAlta(err instanceof Error ? err.message : 'No se pudo abrir el horario')
    } finally {
      enviando.current = false
      setAbriendo(false)
    }
  }

  const lista = turnos ?? []
  const confirmados = lista.filter((t) => t.estado === 'CONFIRMADO').length
  const enHold = lista.filter((t) => t.estado === 'EN_HOLD').length
  const telemedicina = lista.filter((t) => t.modalidad === 'TELEMEDICINA' && t.estado !== 'CANCELADO').length
  const tomados = lista.filter((t) => t.estado === 'CONFIRMADO' || t.estado === 'EN_HOLD').length

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/agenda">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/agenda" aria-current="page">Mi agenda</Link>
          <Link to="/historia">Pacientes</Link>
          <a href="#">Facturación</a>
          <img className="avatar" src={ASSETS.avatar} alt="Perfil" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="Perfil" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <div className="row between page-head">
          <div className="stack" style={{ gap: 5 }}>
            <h1 className="h1" style={{ fontSize: 30 }}>{titulo(dia)}</h1>
            <p className="muted">{sesion?.nombre}</p>
          </div>
          <div className="row" style={{ gap: 8 }}>
            <button type="button" className="btn card" aria-label="Día anterior" onClick={() => cambiarDia(moverDias(dia, -1))} style={{ padding: '11px 16px', borderRadius: 8 }}>←</button>
            <button type="button" className="btn card" onClick={() => cambiarDia(claveDelDia(new Date()))} style={{ padding: '11px 16px', borderRadius: 8 }}>Hoy</button>
            <button type="button" className="btn card" aria-label="Día siguiente" onClick={() => cambiarDia(moverDias(dia, 1))} style={{ padding: '11px 16px', borderRadius: 8 }}>→</button>
          </div>
        </div>

        <div className="stats">
          <div className="card stat"><strong>{tomados}</strong><span>Turnos del día</span></div>
          <div className="card stat"><strong style={{ color: 'var(--success)' }}>{confirmados}</strong><span>Confirmados</span></div>
          <div className="card stat"><strong style={{ color: 'var(--accent)' }}>{enHold}</strong><span>En hold</span></div>
          <div className="card stat"><strong style={{ color: 'var(--primary)' }}>{telemedicina}</strong><span>Telemedicina</span></div>
        </div>

        {errorLista && <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{errorLista}</p>}

        {turnos === null ? (
          <p className="muted">Cargando la agenda...</p>
        ) : lista.length === 0 ? (
          <p className="muted">No hay turnos para este día.</p>
        ) : (
          <ul className="appts" style={{ listStyle: 'none', padding: 0 }}>
            {lista.map((turno) => {
              const estado = etiquetaEstado(turno, ahora)
              return (
                <li key={turno.id} className={`card appt${turno.estado === 'EN_HOLD' ? ' on-hold' : ''}`}>
                  <span className="appt-time">{hora(turno.fechaHora)}</span>
                  <div className="grow">
                    {turno.pacienteId !== null ? (
                      <Link className="appt-name" to={`/historia?pacienteId=${turno.pacienteId}`}>{turno.pacienteNombre}</Link>
                    ) : (
                      <p className="appt-name">Sin paciente</p>
                    )}
                    <p className="appt-sub">{detalle(turno)}</p>
                  </div>
                  <span className="status" style={{ background: estado.color }}>{estado.texto}</span>
                </li>
              )
            })}
          </ul>
        )}

        <form className="card stack" onSubmit={abrirHorario} style={{ gap: 16, padding: 22 }} noValidate>
          <h2 className="h2" style={{ fontSize: 18 }}>Abrir un horario el {titulo(dia).toLowerCase()}</h2>
          <div className="row" style={{ gap: 14, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <div className="field">
              <label htmlFor="hora-nueva">HORA</label>
              <input className="input" id="hora-nueva" type="time" required value={horaNueva} onChange={(e) => setHoraNueva(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="modalidad-nueva">MODALIDAD</label>
              <select className="input" id="modalidad-nueva" value={modalidad} onChange={(e) => setModalidad(e.target.value as ModalidadTurno)}>
                <option value="PRESENCIAL">Presencial</option>
                <option value="TELEMEDICINA">Telemedicina</option>
              </select>
            </div>
            {modalidad === 'PRESENCIAL' && (
              <div className="field">
                <label htmlFor="consultorio-nuevo">CONSULTORIO (OPCIONAL)</label>
                <input className="input" id="consultorio-nuevo" maxLength={60} value={consultorio} onChange={(e) => setConsultorio(e.target.value)} />
              </div>
            )}
            <button className="btn btn-primary" type="submit" disabled={abriendo || !horaNueva}>
              {abriendo ? 'Abriendo...' : 'Abrir horario'}
            </button>
          </div>
          {errorAlta && <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{errorAlta}</p>}
        </form>
      </main>
    </>
  )
}
