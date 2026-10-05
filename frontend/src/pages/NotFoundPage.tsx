import { Link } from 'react-router-dom'
import { useTitulo } from '../useTitulo'

export default function NotFoundPage() {
  useTitulo('Página no encontrada')

  return (
    <main className="stage">
      <div
        className="card stack"
        style={{ padding: 40, textAlign: 'center', alignItems: 'center', gap: 16 }}
      >
        <h1 className="h1">Página no encontrada</h1>
        <p className="muted">La dirección a la que intentaste entrar no existe.</p>
        <Link className="btn btn-primary" to="/">Volver al inicio</Link>
      </div>
    </main>
  )
}
