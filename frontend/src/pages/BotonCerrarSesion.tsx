import { useNavigate } from 'react-router-dom'
import { cerrarSesion } from '../api'

export default function BotonCerrarSesion() {
  const navigate = useNavigate()

  function manejarClick() {
    cerrarSesion()
    navigate('/')
  }

  return (
    <button className="btn" onClick={manejarClick}>
      Cerrar sesión
    </button>
  )
}