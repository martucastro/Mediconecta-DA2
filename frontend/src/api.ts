export type Rol = 'PACIENTE' | 'PROFESIONAL' | 'ADMINISTRADOR'

export interface Sesion {
  id: number
  nombre: string
  email: string
  rol: Rol
}

const ROLES: Rol[] = ['PACIENTE', 'PROFESIONAL', 'ADMINISTRADOR']
const CLAVE_SESION = 'mediconecta_sesion'
const CLAVE_AUTH = 'mediconecta_auth'
const RUTA_LOGIN = '/usuarios/login'

// La API cuelga del mismo contexto que sirve el frontend (BASE_URL, hoy
// '/mediconecta/'), asi que no se repite el contexto escrito a mano.
const API = `${import.meta.env.BASE_URL}api`

export function guardarSesion(sesion: Sesion, authHeader: string) {
  sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion))
  sessionStorage.setItem(CLAVE_AUTH, authHeader)
}

function esSesion(valor: unknown): valor is Sesion {
  if (typeof valor !== 'object' || valor === null) return false
  const s = valor as Record<string, unknown>
  return typeof s.id === 'number' && typeof s.nombre === 'string' && ROLES.includes(s.rol as Rol)
}

export function obtenerSesion(): Sesion | null {
  const raw = sessionStorage.getItem(CLAVE_SESION)
  if (!raw) return null
  try {
    const valor: unknown = JSON.parse(raw)
    if (esSesion(valor)) return valor
  } catch {
    // JSON invalido: se trata igual que una sesion inexistente
  }
  // El storage quedo corrupto (por ejemplo, editado a mano): se descarta en
  // vez de dejar la aplicacion sin poder renderizar.
  cerrarSesion()
  return null
}

export function cerrarSesion() {
  sessionStorage.removeItem(CLAVE_SESION)
  sessionStorage.removeItem(CLAVE_AUTH)
}

/** Pantalla de inicio de cada rol, o null si ese rol no tiene pantallas. */
export function rutaInicial(rol: Rol): string | null {
  switch (rol) {
    case 'PACIENTE':
      return '/home'
    case 'PROFESIONAL':
      return '/agenda'
    default:
      return null
  }
}

/**
 * Decide si se puede entrar a una ruta protegida. Devuelve null si puede
 * pasar, o la ruta a la que hay que mandarlo: al login si no hay sesion, o a
 * su pantalla de inicio si tiene sesion pero no el rol que pide la ruta.
 */
export function decidirAcceso(sesion: Sesion | null, roles?: Rol[]): string | null {
  if (!sesion) return '/'
  if (roles && !roles.includes(sesion.rol)) return rutaInicial(sesion.rol) ?? '/'
  return null
}

/** Header Authorization de HTTP Basic. UTF-8 para que una "ñ" no rompa btoa. */
export function encabezadoBasic(email: string, contrasena: string): string {
  const bytes = new TextEncoder().encode(`${email}:${contrasena}`)
  let binario = ''
  bytes.forEach((byte) => {
    binario += String.fromCharCode(byte)
  })
  return 'Basic ' + btoa(binario)
}

export class ApiError extends Error {
  readonly estado: number

  constructor(mensaje: string, estado: number) {
    super(mensaje)
    this.estado = estado
  }
}

export async function api<T = unknown>(path: string, opciones: RequestInit = {}): Promise<T> {
  const auth = sessionStorage.getItem(CLAVE_AUTH)
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    // Le dice al backend que el pedido viene del SPA (AutenticacionBasica,
    // SCRUM-103): sin esto, un 401 manda WWW-Authenticate y el navegador
    // muestra su popup nativo de credenciales antes de que este fetch pueda
    // manejar la respuesta.
    'X-Requested-With': 'XMLHttpRequest',
    ...(opciones.headers as Record<string, string> | undefined),
  }
  if (auth) {
    headers['Authorization'] = auth
  }

  let respuesta: Response
  try {
    respuesta = await fetch(`${API}${path}`, { ...opciones, headers })
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo.', 0)
  }

  if (!respuesta.ok) {
    throw await errorDe(respuesta, path, auth !== null)
  }
  if (respuesta.status === 204) {
    return null as T
  }
  return (await respuesta.json()) as T
}

async function errorDe(respuesta: Response, path: string, habiaSesion: boolean): Promise<ApiError> {
  const estado = respuesta.status

  // Un 5xx puede traer una pagina de error entera (con el stack trace): nunca
  // se muestra lo que diga el servidor, solo un mensaje generico.
  if (estado >= 500) {
    return new ApiError('El servidor no pudo procesar el pedido. Probá de nuevo en unos minutos.', estado)
  }

  // 401 en medio de la sesion: el servidor ya no acepta la credencial guardada.
  // Se cierra la sesion y se vuelve al login, en vez de dejar la pantalla
  // mostrando errores. El login mismo queda afuera: ahi un 401 es "credencial
  // incorrecta" y se muestra en el formulario.
  if (estado === 401 && habiaSesion && path !== RUTA_LOGIN) {
    cerrarSesion()
    window.location.assign(import.meta.env.BASE_URL)
    return new ApiError('Tu sesión venció. Volvé a iniciar sesión.', estado)
  }

  if (estado === 403) {
    return new ApiError('No tenés permiso para hacer esto.', estado)
  }

  const mensaje = mensajeDelBackend(await respuesta.text())
  return new ApiError(mensaje || `No se pudo completar la operación (error ${estado}).`, estado)
}

// El backend responde errores de dos formas segun el endpoint: texto plano
// (por ejemplo el login) o {"error": "mensaje"} (por ejemplo turnos). Cualquier
// otra cosa (HTML, JSON de otra forma) no se muestra cruda.
function mensajeDelBackend(texto: string): string {
  const limpio = texto.trim()
  if (!limpio) return ''
  try {
    const json: unknown = JSON.parse(limpio)
    if (typeof json === 'object' && json !== null && 'error' in json) {
      const error = (json as { error: unknown }).error
      return typeof error === 'string' ? error : ''
    }
    return ''
  } catch {
    // no era JSON: es texto plano
  }
  return limpio.startsWith('<') ? '' : limpio
}
