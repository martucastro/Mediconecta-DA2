import { useEffect, useState, type CSSProperties } from 'react'
import { api, ApiError } from '../api'

// Respuesta de GET /api/telemedicina/turno/{id} (EnlaceSesionDTO): cada
// participante recibe solo su enlace, el de anfitrion el profesional y el de
// invitado el paciente, los dos a la misma sala.
interface EnlaceDeSala {
  turnoId: number
  rol: 'PACIENTE' | 'PROFESIONAL'
  enlace: string
  estado: string
}

type Sala =
  | { estado: 'cargando' }
  | { estado: 'lista'; enlace: string }
  | { estado: 'sin-sala'; mensaje: string }

/**
 * El mensaje que reemplaza al boton cuando no hay una sala a la que entrar.
 * 404: el turno no tiene sala (todavia no se confirmo o no es de telemedicina).
 * 403/409: el turno ya no es de quien pregunta o ya no esta tomado, por
 * ejemplo porque se cancelo o porque otro paciente lo reservo.
 */
function mensajeSinSala(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.estado === 404) return 'La sala de video todavía no está disponible.'
    if (err.estado === 403 || err.estado === 409) return 'Este turno ya no tiene una videoconsulta disponible.'
    return err.message
  }
  return 'No se pudo obtener la sala de video.'
}

/**
 * Boton "Unirse a la consulta" de un turno de telemedicina confirmado
 * (SCRUM-104). La sala la crea el backend al confirmar el turno (SCRUM-95); aca
 * solo se pide el enlace de quien esta logueado. Lo usan la agenda del
 * profesional y el turno del paciente.
 */
// style se aplica solo al boton: el padding de un boton chico no tiene que
// correr los mensajes.
export default function UnirseAConsulta({ turnoId, style }: { turnoId: number; style?: CSSProperties }) {
  const [sala, setSala] = useState<Sala>({ estado: 'cargando' })

  useEffect(() => {
    let cancelado = false
    api<EnlaceDeSala>(`/telemedicina/turno/${turnoId}`)
      .then((respuesta) => {
        if (!cancelado) setSala({ estado: 'lista', enlace: respuesta.enlace })
      })
      .catch((err: unknown) => {
        if (!cancelado) setSala({ estado: 'sin-sala', mensaje: mensajeSinSala(err) })
      })
    return () => {
      cancelado = true
    }
  }, [turnoId])

  if (sala.estado === 'cargando') {
    return <p className="muted" style={{ fontSize: 14 }}>Buscando la sala de video...</p>
  }
  if (sala.estado === 'sin-sala') {
    return <p className="muted" role="status" style={{ fontSize: 14 }}>{sala.mensaje}</p>
  }
  // Se abre en otra pestaña para no perder la sesion de MediConecta.
  // noopener: la pagina del proveedor no puede manipular esta pestaña.
  return (
    <a className="btn btn-primary" href={sala.enlace} target="_blank" rel="noopener noreferrer" style={style}>
      Unirse a la consulta
    </a>
  )
}
