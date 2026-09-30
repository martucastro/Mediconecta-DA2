import { Link } from 'react-router-dom'
import BotonCerrarSesion from './BotonCerrarSesion'

export default function AgendaPage() {
  return (
    <>
      <header className="topbar">
        <Link className="wordmark" to="/agenda">
          <img src="/mediconecta/assets/logo.svg" alt="" />
          MediConecta
        </Link>
        <nav className="nav">
          <Link to="/agenda" aria-current="page">Mi agenda</Link>
          <Link to="/historia">Pacientes</Link>
          <a href="#">Facturación</a>
          <img className="avatar" src="/mediconecta/assets/avatar.svg" alt="Perfil" />
          <BotonCerrarSesion />
        </nav>
        <img className="avatar m" src="/mediconecta/assets/avatar.svg" alt="Perfil" />
      </header>

      <main className="content stack" style={{ gap: 26 }}>
        <div className="row between page-head">
          <div className="stack" style={{ gap: 5 }}>
            <h1 className="h1" style={{ fontSize: 30 }}>Jueves 18 de septiembre</h1>
            <p className="muted">Dra. Carla Benítez · Centro Médico Belgrano</p>
          </div>
          <div className="row" style={{ gap: 8 }}>
            <button className="btn card" aria-label="Día anterior" style={{ padding: '11px 16px', borderRadius: 8 }}>←</button>
            <button className="btn card" style={{ padding: '11px 16px', borderRadius: 8 }}>Hoy</button>
            <button className="btn card" aria-label="Día siguiente" style={{ padding: '11px 16px', borderRadius: 8 }}>→</button>
          </div>
        </div>

        <div className="stats">
          <div className="card stat"><strong>9</strong><span>Turnos hoy</span></div>
          <div className="card stat"><strong style={{ color: 'var(--success)' }}>7</strong><span>Confirmados</span></div>
          <div className="card stat"><strong style={{ color: 'var(--accent)' }}>1</strong><span>En hold</span></div>
          <div className="card stat"><strong style={{ color: 'var(--primary)' }}>2</strong><span>Telemedicina</span></div>
        </div>

        <ul className="appts" style={{ listStyle: 'none', padding: 0 }}>
          <li className="card appt">
            <span className="appt-time">09:00</span>
            <div className="grow"><p className="appt-name">Martín Aguirre</p><p className="appt-sub">Control de presión · OSDE 210</p></div>
            <span className="status" style={{ background: 'var(--success)' }}>CONFIRMADO</span>
          </li>
          <li className="card appt">
            <span className="appt-time">10:00</span>
            <div className="grow"><p className="appt-name">Lucía Ferrán</p><p className="appt-sub">Primera consulta · Particular</p></div>
            <span className="status" style={{ background: 'var(--success)' }}>CONFIRMADO</span>
          </li>
          <li className="card appt">
            <span className="appt-time">11:30</span>
            <div className="grow"><p className="appt-name">Diego Paz</p><p className="appt-sub">Seguimiento post quirúrgico · Swiss Medical</p></div>
            <span className="status" style={{ background: 'var(--primary)' }}>TELEMEDICINA</span>
          </li>
          <li className="card appt on-hold">
            <span className="appt-time">14:30</span>
            <div className="grow"><Link className="appt-name" to="/historia">Sofía Ramírez</Link><p className="appt-sub">Control de hipertensión · OSDE 210</p></div>
            <span className="status" style={{ background: 'var(--accent)' }}>EN HOLD · 04:48</span>
          </li>
          <li className="card appt">
            <span className="appt-time">16:00</span>
            <div className="grow"><p className="appt-name">Elena Duarte</p><p className="appt-sub">Renovación de receta · PAMI</p></div>
            <span className="status" style={{ background: 'var(--success)' }}>CONFIRMADO</span>
          </li>
        </ul>
      </main>
    </>
  )
}