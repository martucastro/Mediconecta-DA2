import { useEffect } from 'react'

// Titulo de la pestana por pantalla, como tenia el prototipo original.
export function useTitulo(titulo: string) {
  useEffect(() => {
    document.title = `${titulo} · MediConecta`
  }, [titulo])
}
