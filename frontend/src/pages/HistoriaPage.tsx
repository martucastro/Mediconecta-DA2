import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import { api, ApiError, obtenerSesion, rutaInicial } from '../api'
import { formatearFechaCorta, parsearFechaHora } from '../fechas'
import {
  armarConsulta, cuerpo, ETIQUETAS, ordenarRecientes, pacienteDeLaPagina, titulo,
  type HistoriaDTO, type RecetaForm, type TipoEntrada,
} from '../historia'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

const COLORES: Record<TipoEntrada, { fondo: string; texto: string }> = {
  DIAGNOSTICO: { fondo: 'var(--primary-soft)', texto: 'var(--primary)' },
  RECETA: { fondo: 'var(--accent-soft)', texto: 'var(--accent)' },
  ANTECEDENTE: { fondo: 'var(--ground)', texto: 'var(--muted)' },
}

const RECETA_VACIA: RecetaForm = { medicamento: '', dosis: '', dias: '' }

type Carga =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  // GET /historias/paciente/{id} respondio 404: el paciente no tiene historia.
  | { estado: 'sin-historia' }
  | { estado: 'lista'; historia: HistoriaDTO }

function mensajeDeError(err: unknown, porDefecto: string): string {
  // El 403 lo decide el backend: un paciente solo puede leer su propia historia.
  if (err instanceof ApiError && err.estado === 403) {
    return 'No tenés permiso para ver esta historia clínica. Cada paciente solo puede ver la suya.'
  }
  return err instanceof Error ? err.message : porDefecto
}

function fecha(texto: string): string {
  const f = parsearFechaHora(texto)
  return f ? formatearFechaCorta(f) : ''
}

export default function HistoriaPage() {
  useTitulo('Historia clínica')
  const sesion = obtenerSesion()
  const [params] = useSearchParams()
  // La agenda pasa el nombre del paciente al navegar; si se recarga la pagina
  // se pierde y queda el id.
  const nombreDesdeLaAgenda = (useLocation().state as { nombre?: string } | null)?.nombre

  // RutaProtegida ya garantiza la sesion; esto solo conforma a TypeScript.
  if (!sesion) return null
  const esProfesional = sesion.rol === 'PROFESIONAL'
  const pacienteId = pacienteDeLaPagina(sesion.rol, sesion.id, params.get('pacienteId'))
  const nombre = esProfesional ? nombreDesdeLaAgenda : pacienteId === sesion.id ? sesion.nombre : undefined

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to={rutaInicial(sesion.rol) ?? '/'}>
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          {esProfesional ? (
            <Link to="/agenda">Mi agenda</Link>
          ) : (
            <>
              <Link to="/home">Mis turnos</Link>
              <Link to="/historia" aria-current="page">Mi historia clínica</Link>
            </>
          )}
          <img className="avatar" src={ASSETS.avatar} alt="" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        {pacienteId === null ? (
          <article className="card" style={{ padding: '22px 24px' }}>
            <p className="muted">
              Elegí un paciente desde <Link to="/agenda">tu agenda</Link> para ver su historia clínica.
            </p>
          </article>
        ) : (
          <>
            <article className="card person" style={{ padding: '22px 24px' }}>
              <img className="avatar" src={ASSETS.avatar} alt="" style={{ width: 54, height: 54 }} />
              <div className="stack grow" style={{ gap: 5 }}>
                <h1 className="person-name">{nombre ?? `Paciente #${pacienteId}`}</h1>
                <p className="muted" style={{ fontSize: 14 }}>Historia clínica</p>
              </div>
              {esProfesional && (
                <span className="tag" style={{ padding: '8px 14px', borderRadius: 7, background: 'var(--primary-soft)', color: 'var(--primary)' }}>Acceso restringido · rol PROFESIONAL</span>
              )}
            </article>
            {/* key: si cambia el paciente, el estado de la historia anterior se descarta. */}
            <HistoriaDelPaciente key={pacienteId} pacienteId={pacienteId} esProfesional={esProfesional} />
          </>
        )}
      </main>
    </>
  )
}

function HistoriaDelPaciente({ pacienteId, esProfesional }: { pacienteId: number; esProfesional: boolean }) {
  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [abriendo, setAbriendo] = useState(false)
  const [errorAlAbrir, setErrorAlAbrir] = useState('')

  useEffect(() => {
    let cancelado = false
    api<HistoriaDTO>(`/historias/paciente/${pacienteId}`)
      .then((historia) => {
        if (!cancelado) setCarga({ estado: 'lista', historia })
      })
      .catch((err: unknown) => {
        if (cancelado) return
        if (err instanceof ApiError && err.estado === 404) {
          setCarga({ estado: 'sin-historia' })
        } else {
          setCarga({ estado: 'error', mensaje: mensajeDeError(err, 'No se pudo cargar la historia clínica') })
        }
      })
    return () => {
      cancelado = true
    }
  }, [pacienteId])

  async function abrirHistoria() {
    setAbriendo(true)
    setErrorAlAbrir('')
    try {
      const historia = await api<HistoriaDTO>(`/historias/paciente/${pacienteId}`, { method: 'POST' })
      setCarga({ estado: 'lista', historia })
    } catch (err) {
      setErrorAlAbrir(mensajeDeError(err, 'No se pudo abrir la historia clínica'))
    } finally {
      setAbriendo(false)
    }
  }

  if (carga.estado === 'cargando') {
    return <p className="muted">Cargando la historia clínica...</p>
  }

  if (carga.estado === 'error') {
    return <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{carga.mensaje}</p>
  }

  if (carga.estado === 'sin-historia') {
    return (
      <article className="card stack" style={{ gap: 12, padding: 22 }}>
        <p className="muted">
          {esProfesional
            ? 'Este paciente todavía no tiene una historia clínica.'
            : 'Todavía no tenés una historia clínica. Se abre en tu primera consulta.'}
        </p>
        {esProfesional && (
          <button className="btn btn-primary" type="button" onClick={abrirHistoria} disabled={abriendo} style={{ alignSelf: 'flex-start' }}>
            {abriendo ? 'Abriendo...' : 'Abrir historia clínica'}
          </button>
        )}
        {errorAlAbrir && <p role="alert" style={{ color: 'var(--accent)' }}>{errorAlAbrir}</p>}
      </article>
    )
  }

  const entradas = ordenarRecientes(carga.historia.entradas)
  return (
    <>
      {esProfesional && (
        <FormularioDeConsulta
          pacienteId={pacienteId}
          onRegistrada={(historia) => setCarga({ estado: 'lista', historia })}
        />
      )}

      <h2 className="h2" style={{ fontSize: 26 }}>Historia clínica</h2>

      {entradas.length === 0 ? (
        <p className="muted">Todavía no hay entradas en la historia clínica.</p>
      ) : (
        <div className="stack" style={{ gap: 14 }}>
          {entradas.map((entrada) => (
            <article key={entrada.id} className="card entry">
              <div className="row between entry-head">
                <div className="row" style={{ gap: 12 }}>
                  <span className="type" style={{ background: COLORES[entrada.tipo].fondo, color: COLORES[entrada.tipo].texto }}>
                    {ETIQUETAS[entrada.tipo]}
                  </span>
                  <span className="muted" style={{ fontSize: 13 }}>Profesional #{entrada.profesionalId}</span>
                </div>
                <time className="muted" dateTime={entrada.fecha} style={{ fontSize: 13, fontWeight: 500 }}>{fecha(entrada.fecha)}</time>
              </div>
              <h3 className="entry-title">{titulo(entrada)}</h3>
              {cuerpo(entrada) && <p className="entry-body">{cuerpo(entrada)}</p>}
            </article>
          ))}
        </div>
      )}
    </>
  )
}

function FormularioDeConsulta({ pacienteId, onRegistrada }: { pacienteId: number; onRegistrada: (historia: HistoriaDTO) => void }) {
  const [codigo, setCodigo] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [recetas, setRecetas] = useState<RecetaForm[]>([])
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  const [registrada, setRegistrada] = useState(false)

  function cambiarReceta(indice: number, campo: keyof RecetaForm, valor: string) {
    setRecetas((actuales) => actuales.map((r, i) => (i === indice ? { ...r, [campo]: valor } : r)))
  }

  async function registrar(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setError('')
    setRegistrada(false)
    try {
      const historia = await api<HistoriaDTO>(`/historias/paciente/${pacienteId}/consultas`, {
        method: 'POST',
        body: JSON.stringify(armarConsulta(codigo, descripcion, recetas)),
      })
      setCodigo('')
      setDescripcion('')
      setRecetas([])
      setRegistrada(true)
      onRegistrada(historia)
    } catch (err) {
      // Un 400 trae el mensaje de EntradaClinicaFactory, por ejemplo una
      // receta sin medicamento: se muestra dentro del formulario.
      setError(mensajeDeError(err, 'No se pudo registrar la consulta'))
    } finally {
      setEnviando(false)
    }
  }

  return (
    // noValidate: los campos obligatorios los valida el backend, con su mensaje.
    // onChange burbujea desde cada campo: al editar, el error anterior deja de aplicar.
    <form className="card stack" onSubmit={registrar} onChange={() => setError('')} style={{ gap: 16, padding: 22 }} noValidate>
      <h2 className="h2" style={{ fontSize: 18 }}>Registrar consulta</h2>
      <div className="row" style={{ gap: 14, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <div className="field" style={{ width: 160 }}>
          <label htmlFor="codigo-cie10">CÓDIGO CIE-10</label>
          <input className="input" id="codigo-cie10" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
        </div>
        <div className="field grow" style={{ minWidth: 220 }}>
          <label htmlFor="descripcion">DIAGNÓSTICO</label>
          <input className="input" id="descripcion" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </div>
      </div>

      {recetas.map((receta, i) => (
        <fieldset key={i} className="row" style={{ gap: 14, flexWrap: 'wrap', alignItems: 'flex-end', border: 0, padding: 0, margin: 0 }}>
          <legend className="label-caps muted" style={{ marginBottom: 8 }}>RECETA {i + 1}</legend>
          <div className="field grow" style={{ minWidth: 180 }}>
            <label htmlFor={`receta-${i}-medicamento`}>MEDICAMENTO</label>
            <input className="input" id={`receta-${i}-medicamento`} value={receta.medicamento} onChange={(e) => cambiarReceta(i, 'medicamento', e.target.value)} />
          </div>
          <div className="field" style={{ width: 180 }}>
            <label htmlFor={`receta-${i}-dosis`}>DOSIS</label>
            <input className="input" id={`receta-${i}-dosis`} value={receta.dosis} onChange={(e) => cambiarReceta(i, 'dosis', e.target.value)} />
          </div>
          <div className="field" style={{ width: 110 }}>
            <label htmlFor={`receta-${i}-dias`}>DÍAS</label>
            <input className="input" id={`receta-${i}-dias`} type="number" min={1} value={receta.dias} onChange={(e) => cambiarReceta(i, 'dias', e.target.value)} />
          </div>
          <button className="btn" type="button" onClick={() => setRecetas((actuales) => actuales.filter((_, j) => j !== i))}>Quitar</button>
        </fieldset>
      ))}

      <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
        <button className="btn" type="button" onClick={() => setRecetas((actuales) => [...actuales, RECETA_VACIA])}>Agregar receta</button>
        <button className="btn btn-primary" type="submit" disabled={enviando}>
          {enviando ? 'Registrando...' : 'Registrar consulta'}
        </button>
      </div>
      {error && <p role="alert" style={{ color: 'var(--accent)' }}>{error}</p>}
      {registrada && <p className="muted" role="status">Consulta registrada.</p>}
    </form>
  )
}
