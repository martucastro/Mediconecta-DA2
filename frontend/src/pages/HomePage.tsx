import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import Paginador from './Paginador'
import UnirseAConsulta from './UnirseAConsulta'
import { api, obtenerSesion } from '../api'
import { formatearDiaLargo, formatearHora, parsearFechaHora, partesDeCaja } from '../fechas'
import { paginar } from '../paginado'
import { confirmadosPorVenir, holdVigente, vencimientoDelHold } from '../proximoTurno'
import { guardarTurnoEnHold, type TurnoDTO } from '../turnoEnHold'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

type Carga =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  // "ahora" se toma al llegar la respuesta y no en cada render: decide que
  // turnos ya pasaron, y tiene que ser el mismo para toda la pantalla.
  | { estado: 'listo'; turnos: TurnoDTO[]; ahora: number }

function primerNombre(nombre: string | undefined): string {
  return nombre?.trim().split(/\s+/)[0] ?? ''
}

function textoLugar(turno: TurnoDTO): string {
  if (turno.modalidad === 'TELEMEDICINA') return 'Telemedicina'
  return turno.consultorio ? `Presencial · ${turno.consultorio}` : 'Presencial'
}

function textoCobertura(turno: TurnoDTO): string {
  if (turno.coberturaAutorizada) return `Cobertura autorizada · ${turno.coberturaPorcentaje ?? 0} %`
  return 'Particular'
}

function horaDeVencimiento(turno: TurnoDTO): string {
  const vence = vencimientoDelHold(turno)
  if (vence === null) return ''
  const fecha = new Date(vence)
  return `${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`
}

function TarjetaDeTurno({ turno, etiqueta, children }: { turno: TurnoDTO; etiqueta: string; children?: ReactNode }) {
  // confirmadosPorVenir y holdVigente solo devuelven turnos con fecha legible.
  const fecha = parsearFechaHora(turno.fechaHora)!
  const caja = partesDeCaja(fecha)
  return (
    <article className="card next">
      <div className="date-box" role="img" aria-label={formatearDiaLargo(fecha)}>
        <span className="dow">{caja.diaSemana}</span>
        <span className="day">{caja.dia}</span>
        <span className="mon">{caja.mes}</span>
      </div>
      <div className="stack grow" style={{ gap: 7 }}>
        <h2 style={{ fontSize: 20, fontWeight: 600 }}>{turno.profesionalNombre ?? 'Profesional'}</h2>
        <p className="muted">{textoLugar(turno)} · {formatearHora(fecha)} hs</p>
        {/* .tag es nowrap: en mobile una etiqueta larga se saldria de la tarjeta. */}
        <span className="tag tag-soft" style={{ alignSelf: 'flex-start', whiteSpace: 'normal' }}>{etiqueta}</span>
      </div>
      {children && <div className="row actions-row" style={{ gap: 10 }}>{children}</div>}
    </article>
  )
}

export default function HomePage() {
  useTitulo('Mis turnos')
  const navigate = useNavigate()
  const sesion = obtenerSesion()

  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [recarga, setRecarga] = useState(0)
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    let cancelado = false
    api<TurnoDTO[]>('/turnos/mios')
      .then((turnos) => {
        if (!cancelado) setCarga({ estado: 'listo', turnos, ahora: Date.now() })
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setCarga({ estado: 'error', mensaje: err instanceof Error ? err.message : 'No se pudieron cargar tus turnos' })
      })
    return () => {
      cancelado = true
    }
  }, [recarga])

  function reintentar() {
    setCarga({ estado: 'cargando' })
    setRecarga((n) => n + 1)
  }

  // La pantalla de hold lee el turno de sessionStorage (no hay GET de un
  // turno individual): se deja guardado el que se retoma, como al reservar.
  function retomar(turno: TurnoDTO) {
    guardarTurnoEnHold(turno)
    navigate('/hold')
  }

  // SCRUM-134: todos los confirmados por venir, no solo el proximo.
  const confirmados = carga.estado === 'listo' ? confirmadosPorVenir(carga.turnos, carga.ahora) : []
  const paginaActual = paginar(confirmados, pagina)
  const hold = carga.estado === 'listo' ? holdVigente(carga.turnos, carga.ahora) : null
  const fechaProximo = confirmados.length > 0 ? parsearFechaHora(confirmados[0].fechaHora) : null

  let resumen = ''
  if (carga.estado === 'listo') {
    if (hold) resumen = 'Tenés un turno reservado esperando que lo confirmes.'
    else if (fechaProximo) resumen = `Tu próximo turno es el ${formatearDiaLargo(fechaProximo)}.`
    else resumen = 'No tenés turnos próximos.'
  }

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/home">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/home" aria-current="page">Mis turnos</Link>
          <img className="avatar" src={ASSETS.avatar} alt="" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="" />
      </header>

      <main className="content home">
        <div className="stack" style={{ gap: 6 }}>
          <h1 className="h1">{sesion ? `Hola, ${primerNombre(sesion.nombre)}` : 'Hola'}</h1>
          {resumen && <p className="muted" style={{ fontSize: 16 }}>{resumen}</p>}
        </div>

        <div className="stack" style={{ gap: 16 }} aria-live="polite" aria-busy={carga.estado === 'cargando'}>
          {carga.estado === 'cargando' && <p className="muted">Cargando tus turnos...</p>}

          {carga.estado === 'error' && (
            <article className="card stack" style={{ gap: 12, padding: 22 }}>
              <p role="alert" style={{ color: 'var(--accent)' }}>{carga.mensaje}</p>
              <button className="btn" type="button" onClick={reintentar} style={{ alignSelf: 'flex-start' }}>Reintentar</button>
            </article>
          )}

          {hold && (
            <TarjetaDeTurno turno={hold} etiqueta={`Sin confirmar · vence ${horaDeVencimiento(hold)} hs`}>
              <button className="btn btn-primary" type="button" onClick={() => retomar(hold)}>Retomar la reserva</button>
            </TarjetaDeTurno>
          )}

          {confirmados.length > 0 && (
            <section className="stack" style={{ gap: 16 }}>
              <h2 className="h2">Mis turnos</h2>
              {paginaActual.items.map((turno) => (
                <TarjetaDeTurno key={turno.id} turno={turno} etiqueta={textoCobertura(turno)}>
                  {turno.modalidad === 'TELEMEDICINA' && <UnirseAConsulta turnoId={turno.id} />}
                </TarjetaDeTurno>
              ))}
              <Paginador pagina={paginaActual.pagina} totalPaginas={paginaActual.totalPaginas} onCambiar={setPagina} />
            </section>
          )}

          {carga.estado === 'listo' && !hold && confirmados.length === 0 && (
            <article className="card stack" style={{ gap: 12, padding: 28 }}>
              <h2 style={{ fontSize: 20, fontWeight: 600 }}>Todavía no tenés turnos</h2>
              <p className="muted">Elegí un profesional y un horario para reservar tu próximo turno.</p>
              <Link className="btn btn-primary" to="/disponibilidad" style={{ alignSelf: 'flex-start' }}>Reservar un turno</Link>
            </article>
          )}
        </div>

        <section className="stack" style={{ gap: 18 }}>
          <h2 className="h2">Reservar un turno</h2>
          <Link className="btn btn-primary" to="/disponibilidad" style={{ alignSelf: 'flex-start' }}>Elegir profesional y horario</Link>
        </section>
      </main>
    </>
  )
}
