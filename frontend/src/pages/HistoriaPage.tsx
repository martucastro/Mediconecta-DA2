import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'
import { api } from '../api'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

type TipoEntrada = 'DIAGNOSTICO' | 'RECETA' | 'ANTECEDENTE'

interface EntradaDTO {
  id: number
  tipo: TipoEntrada
  profesionalId: number
  fecha: string
  // DIAGNOSTICO
  codigoCIE10?: string
  descripcion?: string
  // RECETA
  medicamento?: string
  dosis?: string
  diasTratamiento?: number
  // ANTECEDENTE
  tipoAntecedente?: string
  detalle?: string
}

const COLORES: Record<TipoEntrada, { fondo: string; texto: string }> = {
  DIAGNOSTICO: { fondo: 'var(--primary-soft)', texto: 'var(--primary)' },
  RECETA: { fondo: 'var(--accent-soft)', texto: 'var(--accent)' },
  ANTECEDENTE: { fondo: 'var(--ground)', texto: 'var(--muted)' },
}

function fecha(iso: string): string {
  const f = new Date(iso)
  if (Number.isNaN(f.getTime())) return ''
  return f.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function titulo(entrada: EntradaDTO): string {
  switch (entrada.tipo) {
    case 'DIAGNOSTICO':
      return [entrada.descripcion, entrada.codigoCIE10 && `CIE-10 ${entrada.codigoCIE10}`]
        .filter(Boolean).join(' · ')
    case 'RECETA':
      return [entrada.medicamento, entrada.dosis].filter(Boolean).join(' · ')
    case 'ANTECEDENTE':
      return entrada.tipoAntecedente ?? 'Antecedente'
  }
}

function cuerpo(entrada: EntradaDTO): string {
  switch (entrada.tipo) {
    case 'RECETA':
      return entrada.diasTratamiento ? `Tratamiento por ${entrada.diasTratamiento} días.` : ''
    case 'ANTECEDENTE':
      return entrada.detalle ?? ''
    default:
      return ''
  }
}

export default function HistoriaPage() {
  useTitulo('Historia clínica')
  const [params] = useSearchParams()
  const pacienteId = params.get('pacienteId')

  const [entradas, setEntradas] = useState<EntradaDTO[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!pacienteId) return
    let cancelado = false
    setEntradas(null)
    setError('')
    api<EntradaDTO[]>(`/historias/paciente/${pacienteId}/entradas`)
      .then((lista) => {
        if (cancelado) return
        setEntradas(lista)
      })
      .catch((err: unknown) => {
        if (cancelado) return
        setEntradas([])
        setError(err instanceof Error ? err.message : 'No se pudo cargar la historia clínica')
      })
    return () => {
      cancelado = true
    }
  }, [pacienteId])

  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/agenda">
          <img src={ASSETS.logo} alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/agenda">Mi agenda</Link>
          <Link to="/historia" aria-current="page">Pacientes</Link>
          <a href="#">Facturación</a>
          <img className="avatar" src={ASSETS.avatar} alt="Perfil" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="Perfil" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        {!pacienteId ? (
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
                <h1 className="person-name">Paciente #{pacienteId}</h1>
                <p className="muted" style={{ fontSize: 14 }}>Historia clínica</p>
              </div>
              <span className="tag" style={{ padding: '8px 14px', borderRadius: 7, background: 'var(--primary-soft)', color: 'var(--primary)' }}>Acceso restringido · rol PROFESIONAL</span>
            </article>

            <div className="row between page-head">
              <h2 className="h2" style={{ fontSize: 26 }}>Historia clínica</h2>
            </div>

            {error && (
              <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>
            )}

            {entradas === null ? (
              <p className="muted">Cargando la historia clínica...</p>
            ) : entradas.length === 0 ? (
              <p className="muted">Este paciente todavía no tiene entradas en su historia clínica.</p>
            ) : (
              <div className="stack" style={{ gap: 14 }}>
                {entradas.map((entrada) => (
                  <article key={entrada.id} className="card entry">
                    <div className="row between entry-head">
                      <div className="row" style={{ gap: 12 }}>
                        <span className="type" style={{ background: COLORES[entrada.tipo].fondo, color: COLORES[entrada.tipo].texto }}>
                          {entrada.tipo}
                        </span>
                        <span className="muted" style={{ fontSize: 13 }}>Profesional #{entrada.profesionalId}</span>
                      </div>
                      <time className="muted" style={{ fontSize: 13, fontWeight: 500 }}>{fecha(entrada.fecha)}</time>
                    </div>
                    <h3 className="entry-title">{titulo(entrada)}</h3>
                    {cuerpo(entrada) && <p className="entry-body">{cuerpo(entrada)}</p>}
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </main>
    </>
  )
}
