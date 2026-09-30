import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, guardarSesion } from '../api'

export default function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarSubmit(evento: React.FormEvent) {
    evento.preventDefault()
    setError('')
    setEnviando(true)

    try {
      const usuario = await api('/usuarios/login', {
        method: 'POST',
        body: JSON.stringify({ email, contrasena: password }),
      })

      // El backend no crea sesion: nosotros armamos el header Basic y lo
      // guardamos para mandarlo en cada request protegido de ahora en mas.
      const authHeader = 'Basic ' + btoa(`${email}:${password}`)
      guardarSesion(usuario, authHeader)

      if (usuario.rol === 'PACIENTE') {
        navigate('/home')
      } else if (usuario.rol === 'PROFESIONAL') {
        navigate('/agenda')
      } else {
        navigate('/home')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="login">
      <section className="brand">
        <div className="wordmark">
          <img src="/mediconecta/assets/logo-light.svg" alt="" />
          MediConecta
        </div>
        <div>
          <h1 className="brand-pitch">Turnos, telemedicina e historia clínica en un solo lugar.</h1>
          <p className="brand-sub">Reservá con tu obra social validada en el momento. Sin llamados, sin esperas.</p>
        </div>
        <p className="brand-foot">Universidad Argentina de la Empresa<br />Desarrollo de Aplicaciones II</p>
      </section>

      <section className="access">
        <form className="login-form" onSubmit={manejarSubmit} noValidate>
          <div className="stack" style={{ gap: 8 }}>
            <h2 className="h1">Iniciar sesión</h2>
            <p className="muted">Ingresá con tu correo y contraseña.</p>
          </div>
          <div className="field">
            <label htmlFor="email">CORREO</label>
            <input
              className="input"
              id="email"
              type="email"
              autoComplete="email"
              placeholder="paciente@ejemplo.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">CONTRASEÑA</label>
            <input
              className="input"
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && (
            <p className="muted" style={{ color: 'var(--accent)' }}>{error}</p>
          )}
          <button className="btn btn-primary btn-lg btn-block" type="submit" disabled={enviando}>
            {enviando ? 'Ingresando...' : 'Ingresar'}
          </button>
          <a className="link" href="#">¿Olvidaste tu contraseña?</a>
        </form>
      </section>
    </main>
  )
}