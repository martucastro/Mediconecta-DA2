export interface Sesion {
  id: number;
  nombre: string;
  email: string;
  rol: string;
}

const CLAVE_SESION = 'mediconecta_sesion';
const CLAVE_AUTH = 'mediconecta_auth';

export function guardarSesion(sesion: Sesion, authHeader: string) {
  sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  sessionStorage.setItem(CLAVE_AUTH, authHeader);
}

export function obtenerSesion(): Sesion | null {
  const raw = sessionStorage.getItem(CLAVE_SESION);
  return raw ? JSON.parse(raw) : null;
}

export function cerrarSesion() {
  sessionStorage.removeItem(CLAVE_SESION);
  sessionStorage.removeItem(CLAVE_AUTH);
}

export class ApiError extends Error {}

export async function api(path: string, opciones: RequestInit = {}) {
  const auth = sessionStorage.getItem(CLAVE_AUTH);
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(opciones.headers as Record<string, string> | undefined),
  };
  if (auth) {
    headers['Authorization'] = auth;
  }

  const respuesta = await fetch(`/mediconecta/api${path}`, {
    ...opciones,
    headers,
  });

  if (!respuesta.ok) {
    // El backend responde errores de dos formas distintas segun el endpoint:
    // texto plano (ej. login) o {"error": "mensaje"} (ej. turnos, tras el
    // refactor de excepciones comunes). Probamos JSON primero; si falla,
    // usamos el texto tal cual.
    const texto = await respuesta.text();
    let mensaje = texto;
    try {
      const json = JSON.parse(texto);
      if (json && typeof json.error === 'string') {
        mensaje = json.error;
      }
    } catch {
      // no era JSON, mensaje ya es el texto plano
    }
    throw new ApiError(mensaje || `Error ${respuesta.status}`);
  }

  if (respuesta.status === 204) {
    return null;
  }
  return respuesta.json();
}