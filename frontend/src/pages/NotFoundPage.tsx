export default function NotFoundPage() {
  return (
    <main className="stage">
      <div className="card" style={{ padding: 40, textAlign: 'center' }}>
        <h1 className="h1">Página no encontrada</h1>
        <p className="muted">La dirección a la que intentaste entrar no existe.</p>
        <a className="btn btn-primary" href="/mediconecta/">Volver al inicio</a>
      </div>
    </main>
  )
}