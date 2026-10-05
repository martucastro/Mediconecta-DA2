import { Link } from 'react-router-dom'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

export default function HomePage() {
  useTitulo('Mis turnos')

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

      <main className="content home">
        <div className="stack" style={{ gap: 6 }}>
          <h1 className="h1">Hola, Sofía</h1>
          <p className="muted" style={{ fontSize: 16 }}>Tenés un turno confirmado esta semana.</p>
        </div>

        <article className="card next">
          <div className="date-box">
            <span className="dow">JUE</span>
            <span className="day">18</span>
            <span className="mon">SEP</span>
          </div>
          <div className="stack grow" style={{ gap: 7 }}>
            <h2 style={{ fontSize: 20, fontWeight: 600 }}>Dra. Carla Benítez</h2>
            <p className="muted">Clínica médica · Consultorio 4B · 14:30 hs</p>
            <span className="tag tag-soft" style={{ alignSelf: 'flex-start' }}>Cobertura autorizada · OSDE 210</span>
          </div>
          <div className="row actions-row" style={{ gap: 10 }}>
            <Link className="btn" to="/disponibilidad">Reprogramar</Link>
            <Link className="btn btn-primary" to="/hold">Ver detalle</Link>
          </div>
        </article>

        <section className="stack" style={{ gap: 18 }}>
          <h2 className="h2">Reservar un turno</h2>
          <input className="input" type="search" aria-label="Buscar" placeholder="Buscar por especialidad, profesional o clínica" style={{ padding: '16px 20px' }} />
          <div className="chips" data-single-select>
            <button className="chip" aria-pressed="true">Clínica médica</button>
            <button className="chip" aria-pressed="false">Cardiología</button>
            <button className="chip" aria-pressed="false">Dermatología</button>
            <button className="chip" aria-pressed="false">Pediatría</button>
            <button className="chip" aria-pressed="false">Telemedicina</button>
          </div>
        </section>
      </main>
    </>
  )
}