import { Link } from 'react-router-dom'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

export default function HistoriaPage() {
  useTitulo('Historia clínica')

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
        </nav>
        <img className="avatar m" src={ASSETS.avatar} alt="Perfil" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <article className="card person" style={{ padding: '22px 24px' }}>
          <img className="avatar" src={ASSETS.avatar} alt="" style={{ width: 54, height: 54 }} />
          <div className="stack grow" style={{ gap: 5 }}>
            <h1 className="person-name">Sofía Ramírez</h1>
            <p className="muted" style={{ fontSize: 14 }}>34 años · DNI 38.492.117 · OSDE 210 · Afiliada 4471-88</p>
          </div>
          <span className="tag" style={{ padding: '8px 14px', borderRadius: 7, background: 'var(--primary-soft)', color: 'var(--primary)' }}>Acceso restringido · rol PROFESIONAL</span>
        </article>

        <div className="row between page-head">
          <h2 className="h2" style={{ fontSize: 26 }}>Historia clínica</h2>
          <button className="btn btn-primary">Registrar consulta</button>
        </div>

        <div className="stack" style={{ gap: 14 }}>
          <article className="card entry">
            <div className="row between entry-head">
              <div className="row" style={{ gap: 12 }}>
                <span className="type" style={{ background: 'var(--primary-soft)', color: 'var(--primary)' }}>DIAGNÓSTICO</span>
                <span className="muted" style={{ fontSize: 13 }}>Dra. Carla Benítez</span>
              </div>
              <time className="muted" style={{ fontSize: 13, fontWeight: 500 }}>18/09/2026</time>
            </div>
            <h3 className="entry-title">Hipertensión arterial esencial · CIE-10 I10</h3>
            <p className="entry-body">Presión registrada 148/92 en dos tomas separadas. Se indica control domiciliario diario durante dos semanas y reducción de sodio en la dieta. Control en 30 días.</p>
          </article>

          <article className="card entry">
            <div className="row between entry-head">
              <div className="row" style={{ gap: 12 }}>
                <span className="type" style={{ background: 'var(--accent-soft)', color: 'var(--accent)' }}>RECETA</span>
                <span className="muted" style={{ fontSize: 13 }}>Dra. Carla Benítez</span>
              </div>
              <time className="muted" style={{ fontSize: 13, fontWeight: 500 }}>18/09/2026</time>
            </div>
            <h3 className="entry-title">Enalapril 10 mg · 1 comprimido cada 12 horas</h3>
            <p className="entry-body">Tratamiento por 30 días. Se advierte sobre posible tos seca como efecto adverso frecuente. Renovable una vez sin consulta presencial.</p>
          </article>

          <article className="card entry">
            <div className="row between entry-head">
              <div className="row" style={{ gap: 12 }}>
                <span className="type" style={{ background: 'var(--ground)', color: 'var(--muted)' }}>ANTECEDENTE</span>
                <span className="muted" style={{ fontSize: 13 }}>Dr. Martín Sosa</span>
              </div>
              <time className="muted" style={{ fontSize: 13, fontWeight: 500 }}>02/03/2024</time>
            </div>
            <h3 className="entry-title">Alergia a penicilina</h3>
            <p className="entry-body">Reacción cutánea documentada en 2019. Evitar betalactámicos. Registrado como antecedente permanente en la historia.</p>
          </article>
        </div>
      </main>
    </>
  )
}