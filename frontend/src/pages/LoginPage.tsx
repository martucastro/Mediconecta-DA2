import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

export default function LoginPage() {
  useTitulo('Iniciar sesión')

  return (
    <main className="login">
      <section className="brand">
        <div className="wordmark">
          <img src={ASSETS.logoClaro} alt="" />
          MediConecta
        </div>
        <div>
          <h1 className="brand-pitch">Turnos, telemedicina e historia clínica en un solo lugar.</h1>
          <p className="brand-sub">Reservá con tu obra social validada en el momento. Sin llamados, sin esperas.</p>
        </div>
        <p className="brand-foot">Universidad Argentina de la Empresa<br />Desarrollo de Aplicaciones II</p>
      </section>

      <section className="access">
        <form className="login-form">
          <div className="stack" style={{ gap: 8 }}>
            <h2 className="h1">Iniciar sesión</h2>
            <p className="muted">Ingresá con tu correo y contraseña.</p>
          </div>
          <div className="field">
            <label htmlFor="email">CORREO</label>
            <input className="input" id="email" type="email" autoComplete="email" placeholder="paciente@ejemplo.com" required />
          </div>
          <div className="field">
            <label htmlFor="password">CONTRASEÑA</label>
            <input className="input" id="password" type="password" autoComplete="current-password" placeholder="••••••••••" required />
          </div>
          <button className="btn btn-primary btn-lg btn-block" type="submit">Ingresar</button>
          <a className="link" href="#">¿Olvidaste tu contraseña?</a>
        </form>
      </section>
    </main>
  )
}