// Prueba de carga de la reserva de turnos (SCRUM-119).
//
// Cada usuario virtual (VU) repite el flujo del paciente sobre ServicioDeTurnos:
//   1. GET  /turnos?profesionalId=...   consultar disponibilidad
//   2. POST /turnos                      reservar (deja el turno EN_HOLD)
//   3. PUT  /turnos/{id}/cancelar        soltar el hold (y su temporizador)
//
// Datos sin choques entre VUs: en setup() se crea un paciente por VU y el
// profesional abre una franja por VU. El VU n solo reserva (y cancela) su
// propia franja, asi que un 409 es un error real y no dos VUs peleando por el
// mismo turno. Los emails llevan el id de la corrida y setup() saltea los
// horarios ya ocupados, asi que se puede correr varias veces contra la misma base.
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
    // Una submetrica por instancia (1, 2, ...): el resumen muestra cuantas
    // requests del flujo atendio cada una, y falla si alguna no recibio ninguna.
    ...Object.fromEntries(BASES.map((_, i) => [`http_reqs{instancia:${i + 1}}`, ['count>0']])),
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
function cabeceras(auth, operacion, instancia) {
  const headers = { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
  if (auth) headers.Authorization = auth
  const tags = instancia ? { operacion, instancia } : { operacion }
  return { headers, tags }
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

  // Una franja por VU, que el VU reserva y cancela en cada iteracion: al
  // cancelar el hold la franja vuelve a DISPONIBLE. Asi la agenda tiene VUS
  // franjas sin importar ITERACIONES, y la respuesta de disponibilidad no crece
  // con la duracion de la corrida. Arranca dentro de unos dias, un minuto por
  // franja. Si el minuto ya tiene una franja (409 por SCRUM-125, por ejemplo de
  // una corrida anterior contra la misma base), prueba el minuto siguiente.
  const inicio = new Date(corrida + 3 * 24 * 3600 * 1000)
  inicio.setSeconds(0, 0)
  const abrirFranja = { ...cabeceras(authProfesional, 'setup'), responseCallback: http.expectedStatuses(201, 409) }
  const franjas = []
  let minuto = 0
  for (let vu = 0; vu < VUS; vu++) {
    let alta
    do {
      const fecha = new Date(inicio.getTime() + minuto++ * 60 * 1000)
      alta = http.post(`${base}/turnos/disponibilidad`,
        JSON.stringify({ fechaHora: localDateTime(fecha), modalidad: 'PRESENCIAL', consultorio: 'Carga' }),
        abrirFranja)
    } while (alta.status === 409)
    if (alta.status !== 201) fail(`No se pudo abrir una franja: HTTP ${alta.status} ${alta.body}`)
    franjas.push(alta.json('id'))
  }

  console.log(`Corrida ${corrida}: ${VUS} pacientes, ${VUS} franjas, instancias: ${BASES.join(', ')}`)
  return { profesionalId, pacientes, franjas }
}

export default function (datos) {
  const vu = exec.vu.idInTest - 1 // 0-based
  const base = BASES[vu % BASES.length]
  const instancia = String((vu % BASES.length) + 1)
  const auth = datos.pacientes[vu]
  const turnoId = datos.franjas[vu]
  const comienzo = Date.now()

  group('reserva de un turno', () => {
    const disponibilidad = http.get(`${base}/turnos?profesionalId=${datos.profesionalId}`,
      cabeceras(auth, 'disponibilidad', instancia))
    check(disponibilidad, {
      'disponibilidad 200': (r) => r.status === 200,
      'la franja del VU sigue disponible': (r) => r.status === 200 && r.json().some((t) => t.id === turnoId),
    })

    const reserva = http.post(`${base}/turnos`, JSON.stringify({ turnoId }), cabeceras(auth, 'reservar', instancia))
    const reservado = check(reserva, {
      'reservar 200': (r) => r.status === 200,
      'queda EN_HOLD': (r) => r.status === 200 && r.json('estado') === 'EN_HOLD',
    })
    if (!reservado) return

    const cancelacion = http.put(`${base}/turnos/${turnoId}/cancelar`, null, cabeceras(auth, 'cancelar', instancia))
    check(cancelacion, { 'cancelar 200': (r) => r.status === 200 })
    // Si la cancelacion falla, se reintenta una vez. Con el hold tomado, cada
    // reserva siguiente del VU daria 409 (un hold por paciente) hasta que el hold
    // venza a los 5 minutos, y la corrida mediria esa cascada y no el sistema.
    // El fallo original igual queda contado en checks y en http_req_failed.
    if (cancelacion.status !== 200) {
      const reintento = http.put(`${base}/turnos/${turnoId}/cancelar`, null,
        cabeceras(auth, 'cancelar_reintento', instancia))
      check(reintento, { 'cancelar (reintento) 200': (r) => r.status === 200 })
    }
  })

  flujo.add(Date.now() - comienzo)
}
