// Ruta base de la app: la define `base` en vite.config.ts (hoy '/mediconecta/')
// y Vite la expone en import.meta.env.BASE_URL, ya con la barra final. Asi las
// imagenes no dependen de un contexto escrito a mano en cada pantalla: si el
// contexto cambia, se cambia solo en vite.config.ts.
const BASE = import.meta.env.BASE_URL

export const ASSETS = {
  logo: `${BASE}assets/logo.svg`,
  logoClaro: `${BASE}assets/logo-light.svg`,
  avatar: `${BASE}assets/avatar.svg`,
}
