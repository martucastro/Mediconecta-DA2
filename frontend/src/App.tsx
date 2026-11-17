import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import DisponibilidadPage from './pages/DisponibilidadPage'
import HoldPage from './pages/HoldPage'
import AgendaPage from './pages/AgendaPage'
import NotificacionesPage from './pages/NotificacionesPage'
import NotFoundPage from './pages/NotFoundPage'
import RutaProtegida from './pages/RutaProtegida'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />

      <Route element={<RutaProtegida roles={['PACIENTE']} />}>
        <Route path="/home" element={<HomePage />} />
        <Route path="/disponibilidad" element={<DisponibilidadPage />} />
        <Route path="/hold" element={<HoldPage />} />
        <Route path="/notificaciones" element={<NotificacionesPage />} />
      </Route>

      <Route element={<RutaProtegida roles={['PROFESIONAL']} />}>
        <Route path="/agenda" element={<AgendaPage />} />
        {/* /historia queda deshabilitada hasta que SCRUM-87 conecte HistoriaPage con /api/historias. */}
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
