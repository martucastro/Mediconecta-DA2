import { Navigate, Outlet } from 'react-router-dom'
import { obtenerSesion } from '../api'

export default function RutaProtegida() {
  const sesion = obtenerSesion()

  if (!sesion) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}