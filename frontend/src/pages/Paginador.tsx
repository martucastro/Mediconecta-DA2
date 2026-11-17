// Oculto cuando todo entra en una pagina.
interface Props {
  pagina: number
  totalPaginas: number
  onCambiar: (pagina: number) => void
}

export default function Paginador({ pagina, totalPaginas, onCambiar }: Props) {
  if (totalPaginas <= 1) return null
  return (
    <nav className="row" aria-label="Paginado" style={{ gap: 8, justifyContent: 'center' }}>
      <button type="button" className="btn card" aria-label="Página anterior" disabled={pagina === 0} onClick={() => onCambiar(pagina - 1)} style={{ padding: '8px 14px', borderRadius: 8 }}>←</button>
      <span className="muted">Página {pagina + 1} de {totalPaginas}</span>
      <button type="button" className="btn card" aria-label="Página siguiente" disabled={pagina === totalPaginas - 1} onClick={() => onCambiar(pagina + 1)} style={{ padding: '8px 14px', borderRadius: 8 }}>→</button>
    </nav>
  )
}
