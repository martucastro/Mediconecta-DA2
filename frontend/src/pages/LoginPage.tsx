import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, encabezadoBasic, guardarSesion, rutaInicial, type Sesion } from '../api'
import { ASSETS } from '../rutas'
import { useTitulo } from '../useTitulo'

export default function LoginPage() {
  useTitulo('Iniciar sesión')

  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarSubmit(evento: FormEvent) {
    evento.preventDefault()
    setError('')
    setEnviando(true)

    try {
      const usuario = await api<Sesion>('/usuarios/login', {
        method: 'POST',
        body: JSON.stringify({ email, contrasena: password }),
      })

      // El administrador no tiene pantallas: opera por la API. No se guarda
      // sesion, para no dejarlo logueado en una interfaz que no es para el.
      const destino = rutaInicial(usuario.rol)
      if (!destino) {
        setError('Esta interfaz es para pacientes y profesionales. Los administradores operan por la API.')
        return
      }

      // El backend no crea sesion: guardamos el usuario y el header Basic para
      // mandarlo en cada pedido protegido de ahora en mas.
      guardarSesion(usuario, encabezadoBasic(email, password))
      navigate(destino)
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
            <p className="muted" role="alert" style={{ color: 'var(--accent)' }}>{error}</p>
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
