// Prueba de carga de la reserva de turnos (SCRUM-119).
//
// Cada usuario virtual (VU) repite el flujo del paciente sobre ServicioDeTurnos:
//   1. GET  /turnos?profesionalId=...   consultar disponibilidad
//   2. POST /turnos                      reservar (deja el turno EN_HOLD)
//   3. PUT  /turnos/{id}/cancelar        soltar el hold (y su temporizador)
//
// Datos sin choques entre VUs: en setup() se crea un paciente por VU y el
// profesional abre una franja por cada iteracion de cada VU. El VU n solo
// reserva sus propias franjas, asi que un 409 es un error real y no dos VUs
// peleando por el mismo turno. Los emails y los horarios llevan el id de la
// corrida, asi que se puede correr varias veces contra la misma base.
//
// Ver load-tests/README.md para como correrlo y que anotar.

import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail, group } from 'k6'
import { Trend } from 'k6/metrics'
import encoding from 'k6/encoding'

const VUS = Number(__ENV.VUS || 10)
const ITERACIONES = Number(__ENV.ITERACIONES || 20)
// Una o varias instancias separadas por coma. Con varias, cada VU usa siempre
// la misma (VU n -> instancia n % cantidad): reparto del lado del cliente,
// sin balanceador.
const BASES = (__ENV.BASE_URLS || 'http://localhost:8080/mediconecta/api').split(',').map((b) => b.trim())
const PROFESIONAL = {
  email: __ENV.PROF_EMAIL || 'profesional@mediconecta.com',
  contrasena: __ENV.PROF_PASSWORD || 'cambiar123',
}
const CONTRASENA_PACIENTES = 'Carga-k6-2026'

export const options = {
  scenarios: {
    reserva: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERACIONES,
      maxDuration: __ENV.DURACION_MAXIMA || '10m',
    },
  },
  // Abrir VUS x ITERACIONES franjas lleva su tiempo con muchos datos.
  setupTimeout: '10m',
  thresholds: {
    // Umbrales de referencia: si se rompen, k6 termina con codigo distinto de 0.
    http_req_failed: ['rate<0.01'],
    'http_req_duration{operacion:disponibilidad}': ['p(95)<800'],
    'http_req_duration{operacion:reservar}': ['p(95)<1000'],
    'http_req_duration{operacion:cancelar}': ['p(95)<800'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

// Duracion del flujo completo de un paciente (las tres llamadas).
const flujo = new Trend('flujo_reserva_completo', true)

function basic(email, contrasena) {
  return `Basic ${encoding.b64encode(`${email}:${contrasena}`)}`
}

// X-Requested-With: como el frontend, para que un 401 no mande WWW-Authenticate
// (SCRUM-103). Sin auth (registro, login) no se manda Authorization.
function cabeceras(auth, operacion) {
  const headers = { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
  if (auth) headers.Authorization = auth
  return { headers, tags: { operacion } }
}

const dos = (n) => String(n).padStart(2, '0')
// LocalDateTime como lo espera el backend (sin zona horaria).
function localDateTime(fecha) {
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}:00`
}

export function setup() {
  const base = BASES[0]
  const corrida = Date.now()
  const authProfesional = basic(PROFESIONAL.email, PROFESIONAL.contrasena)

  // El login devuelve la sesion con el id: /usuarios/profesionales no trae el email.
  const login = http.post(`${base}/usuarios/login`,
    JSON.stringify({ email: PROFESIONAL.email, contrasena: PROFESIONAL.contrasena }),
    cabeceras(authProfesional, 'setup'))
  if (login.status !== 200) fail(`No se pudo loguear al profesional ${PROFESIONAL.email}: HTTP ${login.status}`)
  const profesionalId = login.json('id')

  const pacientes = []
  for (let vu = 1; vu <= VUS; vu++) {
    const email = `carga-${corrida}-${vu}@mediconecta.test`
    const alta = http.post(`${base}/usuarios`, JSON.stringify({
      nombre: `Paciente de carga ${vu}`, email, rol: 'PACIENTE', contrasena: CONTRASENA_PACIENTES,
    }), cabeceras(null, 'setup'))
    if (alta.status !== 201) fail(`No se pudo registrar ${email}: HTTP ${alta.status} ${alta.body}`)
    pacientes.push(basic(email, CONTRASENA_PACIENTES))
  }

  // Una franja por iteracion de cada VU, cada una en un horario distinto. Arranca
  // dentro de unos dias y usa los minutos de la corrida, asi dos corridas el
  // mismo dia no abren franjas en los mismos horarios.
  const inicio = new Date(corrida + 3 * 24 * 3600 * 1000)
  inicio.setSeconds(0, 0)
  const franjas = []
  for (let vu = 0; vu < VUS; vu++) {
    const delVu = []
    for (let i = 0; i < ITERACIONES; i++) {
      const fecha = new Date(inicio.getTime() + (vu * ITERACIONES + i) * 60 * 1000)
      const alta = http.post(`${base}/turnos/disponibilidad`,
        JSON.stringify({ fechaHora: localDateTime(fecha), modalidad: 'PRESENCIAL', consultorio: 'Carga' }),
        cabeceras(authProfesional, 'setup'))
      if (alta.status !== 201) fail(`No se pudo abrir una franja: HTTP ${alta.status} ${alta.body}`)
      delVu.push(alta.json('id'))
    }
    franjas.push(delVu)
  }

  console.log(`Corrida ${corrida}: ${VUS} pacientes, ${VUS * ITERACIONES} franjas, instancias: ${BASES.join(', ')}`)
  return { profesionalId, pacientes, franjas }
}

export default function (datos) {
  const vu = exec.vu.idInTest - 1 // 0-based
  const iteracion = exec.vu.iterationInScenario
  const base = BASES[vu % BASES.length]
  const auth = datos.pacientes[vu]
  const turnoId = datos.franjas[vu][iteracion]
  const comienzo = Date.now()

  group('reserva de un turno', () => {
    const disponibilidad = http.get(`${base}/turnos?profesionalId=${datos.profesionalId}`,
      cabeceras(auth, 'disponibilidad'))
    check(disponibilidad, {
      'disponibilidad 200': (r) => r.status === 200,
      'la franja del VU sigue disponible': (r) => r.status === 200 && r.json().some((t) => t.id === turnoId),
    })

    const reserva = http.post(`${base}/turnos`, JSON.stringify({ turnoId }), cabeceras(auth, 'reservar'))
    const reservado = check(reserva, {
      'reservar 200': (r) => r.status === 200,
      'queda EN_HOLD': (r) => r.status === 200 && r.json('estado') === 'EN_HOLD',
    })
    if (!reservado) return

    const cancelacion = http.put(`${base}/turnos/${turnoId}/cancelar`, null, cabeceras(auth, 'cancelar'))
    check(cancelacion, { 'cancelar 200': (r) => r.status === 200 })
  })

  flujo.add(Date.now() - comienzo)
}
