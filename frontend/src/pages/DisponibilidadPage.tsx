import { Link } from 'react-router-dom'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

export default function DisponibilidadPage() {
  useTitulo('Disponibilidad')

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

        <article className="card person">
          <img className="avatar" src={ASSETS.avatar} alt="" />
          <div className="stack grow" style={{ gap: 5 }}>
            <h1 className="person-name">Dra. Carla Benítez</h1>
            <p className="muted" style={{ fontSize: 14 }}>Clínica médica · Centro Médico Belgrano · MN 84512</p>
          </div>
          <span className="tag tag-soft" style={{ padding: '8px 14px', borderRadius: 7 }}>Acepta OSDE 210</span>
        </article>

        <section className="stack" style={{ gap: 14 }}>
          <h2 className="h2">Elegí un horario</h2>
          <div className="days" data-single-select>
            <button className="day-btn" aria-pressed="false"><small>LUN</small><span>15</span></button>
            <button className="day-btn" aria-pressed="false"><small>MAR</small><span>16</span></button>
            <button className="day-btn" aria-pressed="false"><small>MIE</small><span>17</span></button>
            <button className="day-btn" aria-pressed="true"><small>JUE</small><span>18</span></button>
            <button className="day-btn" aria-pressed="false"><small>VIE</small><span>19</span></button>
            <button className="day-btn" aria-pressed="false"><small>SAB</small><span>20</span></button>
          </div>
        </section>

        <section className="stack" style={{ gap: 14 }}>
          <p className="muted" style={{ fontWeight: 500 }}>Jueves 18 de septiembre · 8 turnos disponibles</p>
          <div className="slots" data-single-select>
            <button className="slot" aria-pressed="false">09:00</button>
            <button className="slot" disabled>09:30</button>
            <button className="slot" aria-pressed="false">10:00</button>
            <button className="slot" aria-pressed="false">10:30</button>
            <button className="slot" disabled>11:00</button>
            <button className="slot" aria-pressed="false">11:30</button>
            <button className="slot" aria-pressed="false">14:00</button>
            <button className="slot" aria-pressed="true">14:30</button>
            <button className="slot" aria-pressed="false">15:00</button>
            <button className="slot" disabled>15:30</button>
            <button className="slot" aria-pressed="false">16:00</button>
            <button className="slot" aria-pressed="false">16:30</button>
          </div>
        </section>

        <Link id="reserve" className="btn btn-primary btn-lg" to="/hold" style={{ alignSelf: 'flex-start' }}>Reservar 14:30 hs</Link>
      </main>
    </>
  )
}