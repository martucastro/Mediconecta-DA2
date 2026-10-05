import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'
import { guardarTurnoEnHold, type TurnoDTO } from '../turnoEnHold'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

interface ProfesionalDTO {
  id: number
  nombre: string
}

const DIAS_ABREV = ['DOM', 'LUN', 'MAR', 'MIE', 'JUE', 'VIE', 'SAB']
const DIAS_LARGO = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0')
}

// Clave de agrupacion por dia en horario local (no toISOString: eso pasa a
// UTC y correria la fecha si el navegador no esta en GMT-3).
function claveDelDia(fecha: Date): string {
  return `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}`
}

function fechaDesdeClave(clave: string): Date {
  return new Date(`${clave}T00:00:00`)
}

function etiquetaCorta(fecha: Date): { abreviatura: string; numero: number } {
  return { abreviatura: DIAS_ABREV[fecha.getDay()], numero: fecha.getDate() }
}

function etiquetaLarga(fecha: Date): string {
  return `${DIAS_LARGO[fecha.getDay()]} ${fecha.getDate()} de ${MESES[fecha.getMonth()]}`
}

function formatearHora(fechaHora: string): string {
  const fecha = new Date(fechaHora)
  return `${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`
}

/**
 * Agrupa los turnos por dia. El Map conserva el orden de inserccion, y como
 * se recorren ya ordenados por fechaHora, las claves quedan en orden
 * cronologico sin tener que ordenarlas aparte.
 */
function agruparPorDia(turnos: TurnoDTO[]): Map<string, TurnoDTO[]> {
  const grupos = new Map<string, TurnoDTO[]>()
  const ordenados = [...turnos].sort((a, b) => a.fechaHora.localeCompare(b.fechaHora))
  for (const turno of ordenados) {
    const clave = claveDelDia(new Date(turno.fechaHora))
    const grupo = grupos.get(clave)
    if (grupo) grupo.push(turno)
    else grupos.set(clave, [turno])
  }
  return grupos
}

export default function DisponibilidadPage() {
  useTitulo('Disponibilidad')
  const navigate = useNavigate()

  const [profesionales, setProfesionales] = useState<ProfesionalDTO[] | null>(null)
  const [profesionalId, setProfesionalId] = useState<number | null>(null)
  const [turnos, setTurnos] = useState<TurnoDTO[] | null>(null)
  const [diaSeleccionado, setDiaSeleccionado] = useState<string | null>(null)
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<TurnoDTO | null>(null)
  const [error, setError] = useState('')
  const [reservando, setReservando] = useState(false)

  useEffect(() => {
    api<ProfesionalDTO[]>('/usuarios/profesionales')
      .then(setProfesionales)
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'No se pudieron cargar los profesionales')
        setProfesionales([])
      })
  }, [])

  // Al cambiar de profesional se vuelve a pedir su disponibilidad. El reset
  // de la seleccion anterior (dia y horario eran de otro profesional) vive en
  // el onChange del select y no aca: un efecto no deberia setState de forma
  // sincronica en su cuerpo, solo al reaccionar a lo que resuelve el fetch.
  useEffect(() => {
    if (profesionalId === null) return
    let cancelado = false
    api<TurnoDTO[]>(`/turnos?profesionalId=${profesionalId}`)
      .then((disponibles) => {
        if (cancelado) return
        setTurnos(disponibles)
        setDiaSeleccionado([...agruparPorDia(disponibles).keys()][0] ?? null)
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setError(err instanceof Error ? err.message : 'No se pudieron cargar los turnos')
        setTurnos([])
      })
    return () => {
      cancelado = true
    }
  }, [profesionalId])

  // El dia, el horario y los turnos mostrados eran del profesional anterior:
  // sin este reset, "Reservar" tomaria un turno de otro profesional.
  function elegirProfesional(id: number | null) {
    setProfesionalId(id)
    setTurnos(null)
    setDiaSeleccionado(null)
    setTurnoSeleccionado(null)
    setError('')
  }

  async function reservar() {
    if (!turnoSeleccionado || profesionalId === null || reservando) return
    setReservando(true)
    setError('')
    try {
      const turno = await api<TurnoDTO>('/turnos', {
        method: 'POST',
        body: JSON.stringify({ turnoId: turnoSeleccionado.id }),
      })
      guardarTurnoEnHold(turno)
      navigate('/hold')
      return
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo reservar el turno')
      if (err instanceof ApiError && err.estado === 409) {
        // Otro paciente se quedo con el turno primero: se refresca la grilla
        // para que no siga mostrando un horario que ya no existe.
        try {
          const disponibles = await api<TurnoDTO[]>(`/turnos?profesionalId=${profesionalId}`)
          setTurnos(disponibles)
          setTurnoSeleccionado(null)
          // Si el dia elegido se quedo sin turnos, se pasa al primero que tenga.
          const diasConTurnos = [...agruparPorDia(disponibles).keys()]
          setDiaSeleccionado((dia) => (dia && diasConTurnos.includes(dia) ? dia : diasConTurnos[0] ?? null))
        } catch {
          // si tampoco se puede refrescar, se deja visible el error de la reserva
        }
      }
    } finally {
      setReservando(false)
    }
  }

  const nombreProfesional = profesionales?.find((p) => p.id === profesionalId)?.nombre
  const grupos = turnos ? agruparPorDia(turnos) : new Map<string, TurnoDTO[]>()
  const dias = [...grupos.keys()]
  const turnosDelDia = diaSeleccionado ? grupos.get(diaSeleccionado) ?? [] : []

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/home">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/home" aria-current="page">Mis turnos</Link>
          <a href="#">Mi historia clínica</a>
          <a href="#">Cobertura</a>
          <img className="avatar" src={ASSETS.avatar} alt="Perfil" />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="Perfil" />
      </header>

      <main className="content avail">
        <Link className="muted" to="/home" style={{ fontSize: 14, fontWeight: 500 }}>← Volver a la búsqueda</Link>

        <section className="field" style={{ maxWidth: 360 }}>
          <label htmlFor="profesional">PROFESIONAL</label>
          <select
            className="input"
            id="profesional"
            value={profesionalId ?? ''}
            onChange={(e) => elegirProfesional(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">Elegí un profesional</option>
            {profesionales?.map((p) => (
              <option key={p.id} value={p.id}>{p.nombre}</option>
            ))}
          </select>
        </section>

        {profesionales === null && <p className="muted">Cargando profesionales…</p>}
        {profesionales?.length === 0 && (
          <p className="muted">No hay profesionales disponibles por el momento.</p>
        )}

        {error && (
          <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>
        )}

        {profesionalId !== null && nombreProfesional && (
          <article className="card person">
            <img className="avatar" src={ASSETS.avatar} alt="" />
            <div className="stack grow" style={{ gap: 5 }}>
              <h1 className="person-name">{nombreProfesional}</h1>
            </div>
          </article>
        )}

        {profesionalId !== null && turnos === null && <p className="muted">Cargando turnos…</p>}
        {profesionalId !== null && turnos?.length === 0 && (
          <p className="muted">Este profesional no tiene turnos disponibles por el momento.</p>
        )}

        {dias.length > 0 && (
          <>
            <section className="stack" style={{ gap: 14 }}>
              <h2 className="h2">Elegí un horario</h2>
              <div className="days" data-single-select>
                {dias.map((clave) => {
                  const { abreviatura, numero } = etiquetaCorta(fechaDesdeClave(clave))
                  return (
                    <button
                      key={clave}
                      type="button"
                      className="day-btn"
                      aria-pressed={clave === diaSeleccionado}
                      onClick={() => {
                        setDiaSeleccionado(clave)
                        setTurnoSeleccionado(null)
                      }}
                    >
                      <small>{abreviatura}</small>
                      <span>{numero}</span>
                    </button>
                  )
                })}
              </div>
            </section>

            <section className="stack" style={{ gap: 14 }}>
              <p className="muted" style={{ fontWeight: 500 }}>
                {diaSeleccionado && etiquetaLarga(fechaDesdeClave(diaSeleccionado))} · {turnosDelDia.length} turnos disponibles
              </p>
              <div className="slots" data-single-select>
                {turnosDelDia.map((turno) => (
                  <button
                    key={turno.id}
                    type="button"
                    className="slot"
                    aria-pressed={turno.id === turnoSeleccionado?.id}
                    onClick={() => setTurnoSeleccionado(turno)}
                  >
                    {formatearHora(turno.fechaHora)}
                  </button>
                ))}
              </div>
            </section>

            <button
              id="reserve"
              type="button"
              className="btn btn-primary btn-lg"
              style={{ alignSelf: 'flex-start' }}
              disabled={!turnoSeleccionado || reservando}
              onClick={reservar}
            >
              {reservando
                ? 'Reservando…'
                : turnoSeleccionado
                  ? `Reservar ${formatearHora(turnoSeleccionado.fechaHora)} hs`
                  : 'Elegí un horario'}
            </button>
          </>
        )}
      </main>
    </>
  )
}
