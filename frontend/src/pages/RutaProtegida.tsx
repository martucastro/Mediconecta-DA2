import { Navigate, Outlet } from 'react-router-dom'
import { decidirAcceso, obtenerSesion, type Rol } from '../api'

interface Props {
  /** Roles que pueden entrar. Sin esto, alcanza con tener sesion. */
  roles?: Rol[]
}

export default function RutaProtegida({ roles }: Props) {
  const destino = decidirAcceso(obtenerSesion(), roles)

  if (destino) {
    return <Navigate to={destino} replace />
  }

  return <Outlet />
}
