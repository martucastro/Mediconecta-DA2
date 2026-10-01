import { Link } from 'react-router-dom'

export default function HoldPage() {
  return (
    <>
      <header className="topbar">
        <Link className="wordmark d" to="/home">
          <img src="/mediconecta/assets/logo.svg" alt="" />
          MediConecta
        </Link>
        <Link className="m" to="/disponibilidad" style={{ fontWeight: 500 }}>← Turno</Link>
        <img className="avatar d" src="/mediconecta/assets/avatar.svg" alt="Perfil" />
        <img className="m" src="/mediconecta/assets/logo.svg" alt="MediConecta" style={{ width: 18, height: 18 }} />
      </header>

      <main className="stage">
        <article className="card hold">
          <div className="counter" role="timer" aria-live="off">
            <span className="label-caps">TU TURNO ESTÁ RESERVADO</span>
            <time id="countdown" data-seconds="288">04:48</time>
            <div className="progress"><div id="progress" style={{ width: '96%' }}></div></div>
            <p>
              Si no confirmás <span className="d">antes de que llegue a cero</span><span className="m">a tiempo</span>, el turno vuelve a quedar disponible<span className="d"> para otro paciente</span>.
            </p>
          </div>

          <section className="summary">
            <h1 className="summary-title"><span className="d">Revisá antes de confirmar</span><span className="m">Dra. Carla Benítez</span></h1>
            <dl className="details">
              <div className="d"><dt>Profesional</dt><dd>Dra. Carla Benítez</dd></div>
              <div><dt>Fecha<span className="d"> y hora</span></dt><dd>Jue<span className="d">ves</span> 18/09 · 14:30<span className="d"> hs</span></dd></div>
              <div><dt>Modalidad</dt><dd>Presencial · <span className="d">Consultorio </span>4B</dd></div>
              <div><dt>Cobertura</dt><dd className="ok">Autorizada<span className="d"> · OSDE 210</span></dd></div>
              <div><dt>Copago<span className="d"> a abonar</span></dt><dd className="strong">$ 4.800</dd></div>
            </dl>
          </section>

          <div className="actions">
            <Link className="btn btn-primary" to="/home">Confirmar y pagar<span className="d">{'\u00A0'}el copago</span></Link>
            <Link className="btn" to="/disponibilidad">Liberar el turno</Link>
          </div>
        </article>
      </main>
    </>
  )
}