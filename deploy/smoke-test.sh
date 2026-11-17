#!/usr/bin/env bash
# Verificacion de humo de MediConecta.
#
# Recorre el flujo completo contra un servidor ya desplegado y comprueba que
# la seguridad, el hold, el copago, la sala de video, las notificaciones y los
# reclamos se comporten como se espera, incluida la falla de la pasarela de pago.
#
#   bash deploy/smoke-test.sh [--rapido] [--resiliencia]
#
# --rapido        Saltea la expiracion del hold, que espera 5 minutos a
#                 proposito porque la gestiona el contenedor.
# --resiliencia   Agrega la demo de fallas del legado de la obra social y del
#                 proveedor de video. Las provoca con system properties de
#                 WildFly, asi que necesita JBOSS_CLI con la ruta a
#                 jboss-cli.sh (o .bat). Las quita siempre al terminar, incluso
#                 si el script se interrumpe.
#
#   JBOSS_CLI=/ruta/a/wildfly/bin/jboss-cli.sh bash deploy/smoke-test.sh --rapido --resiliencia
#
# Corre una sola vez por base: usa franjas con fecha fija (enero de 2027).

set -u
BASE="${BASE:-http://127.0.0.1:8080/mediconecta/api}"
# Mismas variables que lee el seed de usuarios en el primer arranque (README, paso 3).
ADMIN="admin@mediconecta.com:${MEDICONECTA_ADMIN_PASSWORD:-cambiar123}"
PROF="profesional@mediconecta.com:${MEDICONECTA_PROFESIONAL_PASSWORD:-cambiar123}"
PACI="paciente@mediconecta.com:${MEDICONECTA_PACIENTE_PASSWORD:-cambiar123}"
# Confirmar cobra el copago (SCRUM-93) y el cuerpo es JSON. La pasarela simulada
# aprueba cualquier token salvo los que empiezan con "rechazar" o "caer".
PAGO=(-H "Content-Type: application/json" -d '{"tokenMedioDePago":"tok_visa_demo"}')

RAPIDO=no; RESILIENCIA=no
for arg in "$@"; do
  case "$arg" in
    --rapido) RAPIDO=si ;;
    --resiliencia) RESILIENCIA=si ;;
    *) echo "Opcion desconocida: $arg (usar --rapido y/o --resiliencia)"; exit 2 ;;
  esac
done

if [ "$RESILIENCIA" = "si" ] && { [ -z "${JBOSS_CLI:-}" ] || [ ! -f "$JBOSS_CLI" ]; }; then
  echo "--resiliencia necesita JBOSS_CLI con la ruta a jboss-cli.sh o jboss-cli.bat"
  exit 2
fi

ok=0; fallos=0

comprobar() { # descripcion, esperado, obtenido
  if [ "$2" = "$3" ]; then
    printf "  OK    %-52s %s\n" "$1" "$3"; ok=$((ok+1))
  else
    printf "  FALLA %-52s esperaba %s, obtuvo %s\n" "$1" "$2" "$3"; fallos=$((fallos+1))
  fi
}

codigo() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
tipo() { curl -s -o /dev/null -w '%{content_type}' "$@" | cut -d';' -f1; }

# Los consumidores de JMS corren despues de la respuesta HTTP, en otros hilos:
# lo que dejan (notificacion, reclamo) puede tardar unos milisegundos en verse.
# sondear reintenta el GET hasta que algun objeto del listado cumpla el patron.
sondear() { # usuario, ruta, patron (grep -E sobre cada objeto JSON), reintentos de 0,5 s (30 por defecto)
  local i
  for i in $(seq 1 "${4:-30}"); do
    if curl -s -u "$1" "$BASE$2" | tr '{' '\n' | grep -Eq "$3"; then echo si; return; fi
    sleep 0.5
  done
  echo no
}

# Confirma un turno ya reservado con el token de medio de pago indicado: devuelve el codigo HTTP.
confirmar_con() { # usuario, turnoId, token
  codigo -u "$1" -X PUT -H "Content-Type: application/json" -d "{\"tokenMedioDePago\":\"$3\"}" "$BASE/turnos/$2/confirmar"
}

echo "MediConecta, verificacion de humo contra $BASE"
echo
echo "Autenticacion y autorizacion"
comprobar "anonimo a historia clinica"        401 "$(codigo "$BASE/historias/paciente/3")"
comprobar "credencial incorrecta"             401 "$(codigo -u "admin@mediconecta.com:mal" "$BASE/usuarios")"
comprobar "admin lista usuarios"              200 "$(codigo -u "$ADMIN" "$BASE/usuarios")"
comprobar "paciente NO lista usuarios"        403 "$(codigo -u "$PACI" "$BASE/usuarios")"
comprobar "profesional NO lista usuarios"     403 "$(codigo -u "$PROF" "$BASE/usuarios")"

echo
echo "Errores en JSON (SCRUM-132)"
comprobar "401 del contenedor"                application/json "$(tipo "$BASE/turnos/mios")"
comprobar "403 del contenedor (web.xml)"      application/json "$(tipo -u "$PACI" "$BASE/usuarios")"
comprobar "403 del componente"                application/json "$(tipo -u "$PACI" "$BASE/usuarios/999999999")"
comprobar "404 de un usuario inexistente"     application/json "$(tipo -u "$ADMIN" "$BASE/usuarios/999999999")"
comprobar "401 del login"                     application/json "$(tipo -H "Content-Type: application/json" \
                                                     -d '{"email":"nadie@mediconecta.com","contrasena":"mal"}' "$BASE/usuarios/login")"
# AutenticacionBasica (SCRUM-103): sin X-Requested-With se pide Basic; con el
# encabezado del SPA, no (si no, el navegador muestra su popup).
comprobar "401 de curl pide Basic"            si "$(curl -s -D - -o /dev/null "$BASE/turnos/mios" | grep -qi '^WWW-Authenticate: Basic' && echo si || echo no)"
comprobar "401 del SPA no pide Basic"         si "$(curl -s -D - -o /dev/null -H 'X-Requested-With: XMLHttpRequest' "$BASE/turnos/mios" | grep -qi '^WWW-Authenticate' && echo no || echo si)"

echo
echo "Rutas del frontend (SCRUM-134)"
# Las pantallas se sirven con index.html y 200 (filtro RutasDelFrontend); la
# API y los archivos que no existen siguen respondiendo 404.
APP="${BASE%/api}"
comprobar "recargar una pantalla da 200"      200 "$(codigo "$APP/home")"
comprobar "el panel del administrador da 200" 200 "$(codigo "$APP/admin")"
comprobar "un archivo inexistente sigue en 404" 404 "$(codigo "$APP/assets/no-existe.js")"
# Con credenciales: asi el 404 lo da JAX-RS y no depende de que la ruta quede
# fuera de los security-constraint (las protegidas responden 401 sin credencial).
comprobar "una ruta de la API inexistente, 404" 404 "$(codigo -u "$ADMIN" "$BASE/no-existe")"
comprobar "ese 404 es JSON, no el index.html"  application/json "$(tipo -u "$ADMIN" "$BASE/no-existe")"


echo
echo "Consulta de usuario por id (SCRUM-102)"
# Los ids dependen de la base de cada uno: se piden al listado del administrador.
LISTA=$(curl -s -u "$ADMIN" "$BASE/usuarios")
id_de() { printf '%s' "$LISTA" | grep -o "\"email\":\"$1\",\"id\":[0-9]*" | grep -o '[0-9]*$'; }
ID_ADMIN=$(id_de "${ADMIN%%:*}")
ID_PACI=$(id_de "${PACI%%:*}")
comprobar "se resolvieron los ids de admin y paciente" "si" "$([ -n "$ID_ADMIN" ] && [ -n "$ID_PACI" ] && echo si || echo no)"
comprobar "anonimo NO consulta un usuario"            401 "$(codigo "$BASE/usuarios/$ID_PACI")"
comprobar "credencial incorrecta NO consulta"         401 "$(codigo -u "${PACI%%:*}:mal" "$BASE/usuarios/$ID_PACI")"
comprobar "paciente consulta su propio usuario"       200 "$(codigo -u "$PACI" "$BASE/usuarios/$ID_PACI")"
comprobar "paciente NO consulta a otro usuario"       403 "$(codigo -u "$PACI" "$BASE/usuarios/$ID_ADMIN")"
comprobar "profesional NO consulta a un paciente"     403 "$(codigo -u "$PROF" "$BASE/usuarios/$ID_PACI")"
comprobar "admin consulta a cualquier usuario"        200 "$(codigo -u "$ADMIN" "$BASE/usuarios/$ID_PACI")"
comprobar "admin con un id inexistente"               404 "$(codigo -u "$ADMIN" "$BASE/usuarios/999999999")"
comprobar "paciente con id inexistente tambien 403"   403 "$(codigo -u "$PACI" "$BASE/usuarios/999999999")"
comprobar "profesionales sigue siendo publico"        200 "$(codigo "$BASE/usuarios/profesionales")"
comprobar "el login sigue siendo publico"             200 "$(codigo -H "Content-Type: application/json" \
                                                             -d "{\"email\":\"${PACI%%:*}\",\"contrasena\":\"${PACI#*:}\"}" \
                                                             "$BASE/usuarios/login")"
# SCRUM-132: el email no distingue mayusculas, ni en el login ni en Basic.
PACI_MAYUS="$(printf '%s' "${PACI%%:*}" | tr '[:lower:]' '[:upper:]')"
comprobar "login con el email en mayusculas"          200 "$(codigo -H "Content-Type: application/json" \
                                                             -d "{\"email\":\"$PACI_MAYUS\",\"contrasena\":\"${PACI#*:}\"}" \
                                                             "$BASE/usuarios/login")"
comprobar "Basic con el email en mayusculas"          200 "$(codigo -u "$PACI_MAYUS:${PACI#*:}" "$BASE/turnos/mios")"
comprobar "el registro sigue siendo publico"         201 "$(codigo -H "Content-Type: application/json" \
                                                             -d "{\"nombre\":\"Registro publico\",\"email\":\"smoke-reg-$(date +%s)@mediconecta.com\",\"rol\":\"PACIENTE\",\"contrasena\":\"cambiar123\"}" \
                                                             "$BASE/usuarios")"
# SCRUM-127: 5 fallos seguidos bloquean el email 15 minutos. Se usa un email
# inventado: se bloquea igual que uno real, asi el 429 no delata cuentas.
BLOQ="smoke-bloqueo-$(date +%s)@mediconecta.com"
for i in 1 2 3 4 5; do
  codigo -H "Content-Type: application/json" -d "{\"email\":\"$BLOQ\",\"contrasena\":\"mal\"}" "$BASE/usuarios/login" > /dev/null
done
comprobar "tras 5 fallos el login responde 429"       429 "$(codigo -H "Content-Type: application/json" \
                                                             -d "{\"email\":\"$BLOQ\",\"contrasena\":\"mal\"}" \
                                                             "$BASE/usuarios/login")"

echo
echo "Turnos"
NUEVO=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
        -d '{"fechaHora":"2027-01-15T10:00:00"}' "$BASE/turnos/disponibilidad")
ID=$(printf '%s' "$NUEVO" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
# El id del profesional sale de la franja que abrio, no de suponer el de la semilla.
ID_PROF=$(printf '%s' "$NUEVO" | sed -n 's/.*"profesionalId":\([0-9]*\).*/\1/p')
comprobar "profesional abre disponibilidad"   "si" "$([ -n "$ID" ] && [ -n "$ID_PROF" ] && echo si || echo no)"
comprobar "sin modalidad la franja es PRESENCIAL" "si" "$(printf '%s' "$NUEVO" | grep -q '"modalidad":"PRESENCIAL"' && echo si || echo no)"
TELE=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
       -d '{"fechaHora":"2027-01-15T12:00:00","modalidad":"TELEMEDICINA"}' "$BASE/turnos/disponibilidad")
comprobar "abre una franja de telemedicina"   "si" "$(printf '%s' "$TELE" | grep -q '"modalidad":"TELEMEDICINA"' && echo si || echo no)"
comprobar "telemedicina con consultorio"      400 "$(codigo -u "$PROF" -H "Content-Type: application/json" \
                                                     -d '{"fechaHora":"2027-01-15T13:00:00","modalidad":"TELEMEDICINA","consultorio":"3"}' \
                                                     "$BASE/turnos/disponibilidad")"
comprobar "modalidad desconocida"             400 "$(codigo -u "$PROF" -H "Content-Type: application/json" \
                                                     -d '{"fechaHora":"2027-01-15T14:00:00","modalidad":"DOMICILIO"}' \
                                                     "$BASE/turnos/disponibilidad")"
comprobar "paciente NO abre disponibilidad"   403 "$(codigo -u "$PACI" -H "Content-Type: application/json" \
                                                     -d '{"fechaHora":"2027-01-15T11:00:00"}' "$BASE/turnos/disponibilidad")"
# SCRUM-132: JSON-B ya no convierte tipos en silencio (1.5 era 1, 123 era "123").
comprobar "turnoId con decimales"             400 "$(codigo -u "$PACI" -H "Content-Type: application/json" \
                                                     -d '{"turnoId":1.5}' "$BASE/turnos")"
comprobar "turnoId fuera de rango"            400 "$(codigo -u "$PACI" -H "Content-Type: application/json" \
                                                     -d '{"turnoId":99999999999999999999}' "$BASE/turnos")"
comprobar "token de pago numerico"            400 "$(codigo -u "$PACI" -X PUT -H "Content-Type: application/json" \
                                                     -d '{"tokenMedioDePago":123}' "$BASE/turnos/999999999/confirmar")"

# Un hold vigente por paciente (SCRUM-132): la teleconsulta se reserva y se
# suelta antes de reservar $ID.
ID_TELE=$(printf '%s' "$TELE" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
RESERVA_TELE=$(curl -s -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID_TELE}" "$BASE/turnos")
comprobar "telemedicina se cotiza como teleconsulta" "si" "$(printf '%s' "$RESERVA_TELE" | grep -q '"copago":4500.00' && echo si || echo no)"
comprobar "cancelar libera el turno de telemedicina" 200 "$(codigo -u "$PACI" -X PUT "$BASE/turnos/$ID_TELE/cancelar")"
RESERVA=$(curl -s -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID}" "$BASE/turnos")
comprobar "reservar deja el turno EN_HOLD"    "si" "$(printf '%s' "$RESERVA" | grep -q EN_HOLD && echo si || echo no)"
comprobar "la respuesta NO filtra el hash"    "si" "$(printf '%s' "$RESERVA" | grep -q contrasena && echo no || echo si)"
# El paciente semilla es afiliado al plan medio (70%): una CONSULTA de 20000 le deja 6000.
comprobar "la reserva calcula el copago del 70%"  "si" "$(printf '%s' "$RESERVA" | grep -q '"copago":6000.00' && echo si || echo no)"
comprobar "la reserva guarda la autorizacion"     "si" "$(printf '%s' "$RESERVA" | grep -q '"numeroAutorizacion":"AUT-OS-2002-CONSULTA"' && echo si || echo no)"
comprobar "un segundo hold del paciente: 409"     409 "$(codigo -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID_TELE}" "$BASE/turnos")"
comprobar "otro usuario NO confirma el hold"  403 "$(codigo -u "$PROF" -X PUT "${PAGO[@]}" "$BASE/turnos/$ID/confirmar")"
# Copago rechazado: la transaccion de confirmarTurno se revierte entera.
comprobar "copago con tarjeta rechazada"      409 "$(codigo -u "$PACI" -X PUT -H "Content-Type: application/json" \
                                                     -d '{"tokenMedioDePago":"rechazar_demo"}' "$BASE/turnos/$ID/confirmar")"
comprobar "el rechazo deja el turno EN_HOLD"  "si" "$(curl -s -u "$PACI" "$BASE/turnos/mios" | tr '{' '\n' | grep "\"id\":$ID[,}]" | grep -q EN_HOLD && echo si || echo no)"
comprobar "el paciente confirma el suyo"      200 "$(codigo -u "$PACI" -X PUT "${PAGO[@]}" "$BASE/turnos/$ID/confirmar")"
comprobar "anonimo NO ve notificaciones"      401 "$(codigo "$BASE/notificaciones/mias")"
comprobar "profesional NO ve notificaciones"  403 "$(codigo -u "$PROF" "$BASE/notificaciones/mias")"
# La notificacion la crea un consumidor JMS: llega un instante despues de confirmar.
NOTIFICADO=no
for i in 1 2 3 4 5; do
  if curl -s -u "$PACI" "$BASE/notificaciones/mias" | grep -q "\"turnoId\":$ID[,}]"; then
    NOTIFICADO=si
    break
  fi
  sleep 1
done
comprobar "el paciente ve la notificacion del turno" "si" "$NOTIFICADO"

echo
echo "Pagos (copago de la confirmacion)"
PAGOS=$(curl -s -u "$PACI" "$BASE/pagos?turnoId=$ID")
comprobar "el copago quedo APROBADO"          "si" "$(printf '%s' "$PAGOS" | grep -q '"estado":"APROBADO"' && echo si || echo no)"
comprobar "el copago cobrado es de 6000"      "si" "$(printf '%s' "$PAGOS" | grep -q '"monto":6000.00' && echo si || echo no)"
comprobar "el intento rechazado quedo registrado" "si" "$(printf '%s' "$PAGOS" | grep -q '"estado":"RECHAZADO"' && echo si || echo no)"
comprobar "pagos sin credenciales"            401 "$(codigo "$BASE/pagos?turnoId=$ID")"
# SCRUM-132: devolver el copago de un turno que sigue confirmado lo dejaba sin pago.
ID_PAGO=$(printf '%s' "$PAGOS" | tr '{' '\n' | grep '"estado":"APROBADO"' | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
comprobar "admin NO reembolsa un turno confirmado" 409 "$(codigo -u "$ADMIN" -X PUT "$BASE/pagos/$ID_PAGO/reembolso")"

echo
echo "Mis turnos (resueltos desde el usuario autenticado)"
FECHA_ID=$(printf '%s' "$NUEVO" | sed -n 's/.*"fechaHora":"\([0-9-]*\)T.*/\1/p')
comprobar "mis turnos sin credenciales"       401 "$(codigo "$BASE/turnos/mios")"
comprobar "mis turnos como ADMINISTRADOR"     403 "$(codigo -u "$ADMIN" "$BASE/turnos/mios")"
comprobar "el paciente ve su turno"           "si" "$(curl -s -u "$PACI" "$BASE/turnos/mios" | grep -q "\"id\":$ID[,}]" && echo si || echo no)"
comprobar "el profesional lo ve en su agenda" "si" "$(curl -s -u "$PROF" "$BASE/turnos/mios?fecha=$FECHA_ID" | grep -q "\"id\":$ID[,}]" && echo si || echo no)"
comprobar "fecha mal formada"                 400 "$(codigo -u "$PROF" "$BASE/turnos/mios?fecha=15-01-2027")"
OTRO_PACI="smoke-$(date +%s)@mediconecta.com:cambiar123"
curl -s -o /dev/null -H "Content-Type: application/json" \
     -d "{\"nombre\":\"Otro paciente\",\"email\":\"${OTRO_PACI%%:*}\",\"rol\":\"PACIENTE\",\"contrasena\":\"cambiar123\"}" \
     "$BASE/usuarios"
comprobar "otro paciente NO ve ese turno"     "si" "$(curl -s -u "$OTRO_PACI" "$BASE/turnos/mios" | grep -q "\"id\":$ID[,}]" && echo no || echo si)"

echo
echo "Pagos (SCRUM-127: el mismo 403 para un id inexistente que para uno ajeno)"
comprobar "paciente con un pago inexistente da 403"  403 "$(codigo -u "$PACI" "$BASE/pagos/999999999")"
comprobar "paciente con un turno inexistente da 403" 403 "$(codigo -u "$PACI" "$BASE/pagos?turnoId=999999999")"
comprobar "admin con un pago inexistente da 404"     404 "$(codigo -u "$ADMIN" "$BASE/pagos/999999999")"
# SCRUM-132: el reembolso de un pago inexistente era 400; ahora 404, como el GET.
comprobar "admin reembolsa un pago inexistente: 404" 404 "$(codigo -u "$ADMIN" -X PUT "$BASE/pagos/999999999/reembolso")"

echo
echo "Afiliaciones a la obra social (ServicioDeObrasSociales)"
# Se registran los mismos datos del seed (plan medio): el copago del 70% que
# verifica el bloque de turnos no cambia.
AFILIACION=(-H "Content-Type: application/json" -d "{\"pacienteId\":$ID_PACI,\"dni\":\"30333444\",\"numeroAfiliado\":\"OS-2002\"}")
comprobar "admin registra una afiliacion"     200 "$(codigo -u "$ADMIN" "${AFILIACION[@]}" "$BASE/afiliaciones")"
comprobar "paciente NO registra afiliaciones" 403 "$(codigo -u "$PACI" "${AFILIACION[@]}" "$BASE/afiliaciones")"
comprobar "anonimo NO ve una afiliacion"      401 "$(codigo "$BASE/afiliaciones/mia")"
comprobar "el paciente ve su afiliacion"      "si" "$(curl -s -u "$PACI" "$BASE/afiliaciones/mia" | grep -q '"numeroAfiliado":"OS-2002"' && echo si || echo no)"
comprobar "un paciente sin obra social da 404" 404 "$(codigo -u "$OTRO_PACI" "$BASE/afiliaciones/mia")"

echo
echo "Telemedicina (proveedor de video simulado y componente)"
SALA=$(curl -s -H "Content-Type: application/json" -d '{"reference":"smoke"}' "$BASE/externo/salas")
comprobar "el proveedor simulado crea una sala" "si" "$(printf '%s' "$SALA" | grep -q '"guestUrl":"http' && printf '%s' "$SALA" | grep -q '"hostUrl":"http' && echo si || echo no)"
comprobar "el proveedor simulado caido"       503 "$(codigo -H "Content-Type: application/json" -d '{"reference":"caer"}' "$BASE/externo/salas")"
# Franja propia: la de $TELE la reserva y la cancela el bloque de turnos, y un
# turno CANCELADO no se puede volver a reservar.
SALA_TELE=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
            -d '{"fechaHora":"2027-01-15T15:00:00","modalidad":"TELEMEDICINA"}' \
            "$BASE/turnos/disponibilidad")
IDT=$(printf '%s' "$SALA_TELE" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
curl -s -o /dev/null -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$IDT}" "$BASE/turnos"
comprobar "sin sesion todavia"                404 "$(codigo -u "$PACI" "$BASE/telemedicina/turno/$IDT")"
SESION=$(curl -s -u "$PACI" -X POST "$BASE/telemedicina/turno/$IDT")
comprobar "el paciente crea la sesion"        "si" "$(printf '%s' "$SESION" | grep -q '"rol":"PACIENTE"' && echo si || echo no)"
ENLACE_PROF=$(curl -s -u "$PROF" "$BASE/telemedicina/turno/$IDT")
comprobar "crear de nuevo devuelve la misma (200)" 200 "$(codigo -u "$PROF" -X POST "$BASE/telemedicina/turno/$IDT")"
comprobar "el profesional ve su enlace"       "si" "$(printf '%s' "$ENLACE_PROF" | grep -q '"rol":"PROFESIONAL"' && echo si || echo no)"
comprobar "cada uno recibe un enlace distinto" "si" "$([ "$(printf '%s' "$SESION" | sed -n 's/.*"enlace":"\([^"]*\)".*/\1/p')" != "$(printf '%s' "$ENLACE_PROF" | sed -n 's/.*"enlace":"\([^"]*\)".*/\1/p')" ] && echo si || echo no)"
comprobar "sin credenciales NO ve la sala"    401 "$(codigo "$BASE/telemedicina/turno/$IDT")"
comprobar "un turno inexistente da 403"       403 "$(codigo -u "$PACI" "$BASE/telemedicina/turno/999999999")"
comprobar "el administrador NO ve la sala"    403 "$(codigo -u "$ADMIN" "$BASE/telemedicina/turno/$IDT")"
AJENO="smoke-tele-$(date +%s)@mediconecta.com:cambiar123"
curl -s -o /dev/null -H "Content-Type: application/json" \
     -d "{\"nombre\":\"Paciente ajeno\",\"email\":\"${AJENO%%:*}\",\"rol\":\"PACIENTE\",\"contrasena\":\"cambiar123\"}" \
     "$BASE/usuarios"
comprobar "un paciente ajeno NO ve la sala"   403 "$(codigo -u "$AJENO" "$BASE/telemedicina/turno/$IDT")"
comprobar "un turno presencial no lleva sala" 409 "$(codigo -u "$PROF" -X POST "$BASE/telemedicina/turno/$ID")"
# SCRUM-95: confirmar crea la sala sola; confirmar un presencial no crea nada.
CONF_TELE=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
            -d '{"fechaHora":"2027-01-15T16:00:00","modalidad":"TELEMEDICINA"}' \
            "$BASE/turnos/disponibilidad")
IDC=$(printf '%s' "$CONF_TELE" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
# Un hold vigente por paciente: se suelta el de $IDT antes de reservar $IDC.
comprobar "soltar el hold de la sala anterior" 200 "$(codigo -u "$PACI" -X PUT "$BASE/turnos/$IDT/cancelar")"
curl -s -o /dev/null -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$IDC}" "$BASE/turnos"
comprobar "reservar NO crea la sala"          404 "$(codigo -u "$PACI" "$BASE/telemedicina/turno/$IDC")"
comprobar "confirmar telemedicina"            200 "$(codigo -u "$PACI" -X PUT "${PAGO[@]}" "$BASE/turnos/$IDC/confirmar")"
comprobar "al confirmar el paciente tiene enlace" "si" "$(curl -s -u "$PACI" "$BASE/telemedicina/turno/$IDC" | grep -q '"rol":"PACIENTE"' && echo si || echo no)"
comprobar "al confirmar el profesional tiene enlace" "si" "$(curl -s -u "$PROF" "$BASE/telemedicina/turno/$IDC" | grep -q '"rol":"PROFESIONAL"' && echo si || echo no)"
comprobar "confirmar un presencial no crea sala" 404 "$(codigo -u "$PACI" "$BASE/telemedicina/turno/$ID")"

echo
echo "Mensajeria asincronica (topico TurnoConfirmado: notificacion y reclamo)"
# NotificacionMDB y el MDB de facturacion consumen el mensaje despues del commit.
comprobar "el paciente recibe la notificacion"  "si" "$(sondear "$PACI" /notificaciones/mias "quedo confirmado.*\"turnoId\":$ID[,}]")"
comprobar "la teleconsulta tambien notifica"    "si" "$(sondear "$PACI" /notificaciones/mias "quedo confirmado.*\"turnoId\":$IDC[,}]")"
comprobar "notificaciones sin credenciales"     401 "$(codigo "$BASE/notificaciones/mias")"
comprobar "el profesional NO ve notificaciones" 403 "$(codigo -u "$PROF" "$BASE/notificaciones/mias")"
comprobar "el administrador NO ve notificaciones" 403 "$(codigo -u "$ADMIN" "$BASE/notificaciones/mias")"
comprobar "el reclamo a la obra social se envia" "si" "$(sondear "$ADMIN" /reclamos "\"estado\":\"ENVIADO\".*\"turnoId\":$ID[,}]")"
comprobar "el paciente NO lista reclamos"       403 "$(codigo -u "$PACI" "$BASE/reclamos")"
comprobar "reclamos sin credenciales"           401 "$(codigo "$BASE/reclamos")"

echo
echo "Falla de la pasarela de pago (la confirmacion se revierte entera)"
FALLA=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
        -d '{"fechaHora":"2027-01-17T10:00:00"}' "$BASE/turnos/disponibilidad")
IDF=$(printf '%s' "$FALLA" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
curl -s -o /dev/null -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$IDF}" "$BASE/turnos"
comprobar "pasarela caida: 502"                 502 "$(confirmar_con "$PACI" "$IDF" caer_demo)"
comprobar "la falla deja el turno EN_HOLD"      "si" "$(curl -s -u "$PACI" "$BASE/turnos/mios" | tr '{' '\n' | grep "\"id\":$IDF[,}]" | grep -q EN_HOLD && echo si || echo no)"
PAGOS_F=$(curl -s -u "$PACI" "$BASE/pagos?turnoId=$IDF")
comprobar "queda el intento PENDIENTE"          "si" "$(printf '%s' "$PAGOS_F" | grep -q '"estado":"PENDIENTE"' && echo si || echo no)"
comprobar "y no se cobro nada (ningun APROBADO)" "si" "$(printf '%s' "$PAGOS_F" | grep -q '"estado":"APROBADO"' && echo no || echo si)"
comprobar "copago sin medio de pago: 400"       400 "$(codigo -u "$PACI" -X PUT -H "Content-Type: application/json" -d '{}' "$BASE/turnos/$IDF/confirmar")"
comprobar "al volver la pasarela se confirma"   200 "$(confirmar_con "$PACI" "$IDF" tok_visa_demo)"

echo
echo "Sistema legado de la obra social (SOAP)"
SOAP="${SOAP:-${BASE%/api}/legado/obrasocial}"

soap() { # operacion, dni, afiliado, prestacion
  curl -s -H "Content-Type: text/xml; charset=utf-8" -H "SOAPAction: \"\"" --data-binary @- "$SOAP" <<EOF
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:os="http://legado.obrasocial.example/">
  <soapenv:Body><os:$1><dni>$2</dni><numeroAfiliado>$3</numeroAfiliado><codigoPrestacion>$4</codigoPrestacion></os:$1></soapenv:Body>
</soapenv:Envelope>
EOF
}
campo() { sed -n "s/.*<$1>\([^<]*\)<\/$1>.*/\1/p"; } # extrae el valor de un elemento

soapReclamo() { # dni, afiliado, numeroAutorizacion
  curl -s -H "Content-Type: text/xml; charset=utf-8" -H "SOAPAction: \"\"" --data-binary @- "$SOAP" <<EOF
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:os="http://legado.obrasocial.example/">
  <soapenv:Body><os:presentarReclamo><dni>$1</dni><numeroAfiliado>$2</numeroAfiliado><numeroAutorizacion>$3</numeroAutorizacion></os:presentarReclamo></soapenv:Body>
</soapenv:Envelope>
EOF
}

comprobar "el WSDL se descarga"               200 "$(codigo "$SOAP?wsdl")"
comprobar "el WSDL publica validarCobertura"  "si" "$(curl -s "$SOAP?wsdl" | grep -q validarCobertura && echo si || echo no)"
# dni, afiliado, cobertura, copago de una CONSULTA (arancel 20000), autorizado
while read -r dni afiliado pct copago aut; do
  R=$(soap autorizarPrestacion "$dni" "$afiliado" CONSULTA)
  comprobar "afiliado $afiliado: cobertura/copago/autorizado" "$pct/$copago/$aut" \
    "$(printf '%s' "$R" | campo porcentajeCobertura)/$(printf '%s' "$R" | campo copago)/$(printf '%s' "$R" | campo autorizado)"
done <<'CASOS'
30111222 OS-1001 100 0.00 true
30333444 OS-2002 70 6000.00 true
30444555 OS-3003 40 12000.00 true
30555666 OS-4004 0 20000.00 false
CASOS
comprobar "afiliado inexistente da SOAP Fault" "si" \
  "$(soap validarCobertura 1 OS-9999 CONSULTA | grep -q 'Fault' && echo si || echo no)"
# Un pedido invalido es error del cliente: el Adapter lo distingue de una falla
# del legado (faultcode Server) por este codigo.
comprobar "ese Fault tiene codigo Client" "si" \
  "$(soap validarCobertura 1 OS-9999 CONSULTA | grep -Eq '<faultcode[^>]*>[^<]*Client</faultcode>' && echo si || echo no)"
# SCRUM-129: el fault esta declarado en el contrato y viaja con su detalle.
comprobar "el WSDL declara el fault PedidoInvalido" "si" \
  "$(curl -s "$SOAP?wsdl" | grep -q '<wsdl:fault message="tns:PedidoInvalidoException"' && echo si || echo no)"
comprobar "el Fault trae el detalle PedidoInvalido" "si" \
  "$(soap validarCobertura 1 OS-9999 CONSULTA | grep -q 'PedidoInvalido' && echo si || echo no)"
# presentarReclamo (SCRUM-97 T4): presenta una autorizacion ya emitida, no
# evalua cobertura de nuevo.
R=$(soapReclamo 30333444 OS-2002 AUT-OS-2002-CONSULTA)
comprobar "presentarReclamo reconoce arancel x porcentaje del plan" "14000.00" \
  "$(printf '%s' "$R" | campo montoReconocido)"
comprobar "autorizacion desconocida en presentarReclamo da SOAP Fault Client" "si" \
  "$(soapReclamo 30333444 OS-2002 AUT-FALSO | grep -Eq '<faultcode[^>]*>[^<]*Client</faultcode>' && echo si || echo no)"

if [ "$RAPIDO" != "si" ]; then
  echo
  echo "Expiracion del hold (el contenedor libera el turno a los 5 minutos)"
  OTRO=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
         -d '{"fechaHora":"2027-01-16T10:00:00"}' "$BASE/turnos/disponibilidad")
  ID2=$(printf '%s' "$OTRO" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
  curl -s -o /dev/null -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID2}" "$BASE/turnos"
  echo "  turno $ID2 retenido, esperando hasta 7 minutos..."

  # consultarDisponibilidad solo devuelve turnos DISPONIBLE: si el turno vuelve
  # a aparecer en ese listado, el contenedor libero el hold.
  liberado=no
  inicio=$(date +%s)
  for i in $(seq 1 28); do
    sleep 15
    if curl -s -u "$PACI" "$BASE/turnos?profesionalId=$ID_PROF" | grep -q "\"id\":$ID2[,}]"; then
      liberado=si
      echo "  liberado a los $(( $(date +%s) - inicio )) segundos"
      break
    fi
  done
  comprobar "el hold vencido libera el turno"  "si" "$liberado"
fi

if [ "$RESILIENCIA" = "si" ]; then
  echo
  echo "Resiliencia: legado de la obra social y proveedor de video caidos"
  if [ -z "${JBOSS_CLI:-}" ] || [ ! -f "$JBOSS_CLI" ]; then
    echo "  --resiliencia necesita JBOSS_CLI con la ruta a jboss-cli.sh o jboss-cli.bat"
    exit 2
  fi
  PROP_LEGADO=mediconecta.obrasocial.url
  PROP_VIDEO=mediconecta.video.simular-caida
  # Un :add que no se aplico explica una comprobacion fallida, y un :remove que no
  # se aplico deja el servidor roto: cada falla de jboss-cli se informa.
  cli() {
    local salida
    salida=$("$JBOSS_CLI" --connect "$@" 2>&1) && return 0
    echo "  ATENCION: jboss-cli fallo en $*: $(printf '%s' "$salida" | grep -m1 -E 'WFLY|[Ff]ailed' || printf '%s' "$salida" | tail -n 1)"
    return 1
  }
  activas() { # las propiedades de falla que siguen en el servidor, o "?" si no se pudo consultar
    local salida
    salida=$("$JBOSS_CLI" --connect ':read-children-names(child-type=system-property)' 2>&1) || { echo "?"; return; }
    printf '%s' "$salida" | grep -oE "\"($PROP_LEGADO|$PROP_VIDEO)\"" | tr -d '"'
  }
  # Las fallas son system properties del servidor: si el script se corta, quedarian
  # activas y ninguna reserva funcionaria. Se quitan siempre al salir, y se verifica.
  restaurar() {
    local p quedan
    for p in $(activas); do [ "$p" = "?" ] || cli "/system-property=$p:remove"; done
    quedan=$(activas)
    [ -z "$quedan" ] || echo "  ATENCION: siguen activas o no se pudo verificar ($quedan): quitarlas con jboss-cli /system-property=<nombre>:remove"
  }
  trap restaurar EXIT
  restaurar   # parte de un estado limpio, por si quedo algo de una corrida anterior

  abrir() { # fechaHora, modalidad -> id de la franja
    curl -s -u "$PROF" -H "Content-Type: application/json" \
         -d "{\"fechaHora\":\"$1\",\"modalidad\":\"$2\"}" "$BASE/turnos/disponibilidad" | sed -n 's/.*"id":\([0-9]*\).*/\1/p'
  }
  reservar() { codigo -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$1}" "$BASE/turnos"; }

  # 1. Legado caido: la reserva consulta la cobertura por SOAP y no puede seguir.
  IDL=$(abrir 2027-01-18T10:00:00 PRESENCIAL)
  cli "/system-property=$PROP_LEGADO:add(value=\"http://127.0.0.1:1/x\")"
  comprobar "legado caido: la reserva da 503"          503 "$(reservar "$IDL")"
  comprobar "la franja sigue DISPONIBLE"              "si" "$(curl -s -u "$PACI" "$BASE/turnos?profesionalId=$ID_PROF" | grep -q "\"id\":$IDL[,}]" && echo si || echo no)"
  cli "/system-property=$PROP_LEGADO:remove"
  comprobar "legado de vuelta: la reserva funciona"    200 "$(reservar "$IDL")"
  # Un hold vigente por paciente: se suelta antes de reservar el siguiente.
  comprobar "se suelta ese hold"                      200 "$(codigo -u "$PACI" -X PUT "$BASE/turnos/$IDL/cancelar")"

  # 2. Reintentos de la cola: se reserva con el legado arriba, se rompe y se confirma.
  #    La confirmacion no depende del legado; el reclamo si, y se reintenta solo.
  IDR=$(abrir 2027-01-18T11:00:00 PRESENCIAL)
  reservar "$IDR" >/dev/null
  cli "/system-property=$PROP_LEGADO:add(value=\"http://127.0.0.1:1/x\")"
  T_CONFIRMAR=$(date +%s)
  comprobar "con el legado caido se confirma igual"    200 "$(confirmar_con "$PACI" "$IDR" tok_visa_demo)"
  comprobar "el reclamo reintenta (intentos >= 1)"    "si" "$(sondear "$ADMIN" /reclamos "\"intentos\":[1-9].*\"turnoId\":$IDR[,}]" 40)"
  # El quinto intento llega unos 30 s despues de confirmar (2, 4, 8 y 16 s) y deja
  # el reclamo EN_REVISION_MANUAL. Cada jboss-cli arranca una JVM: el :remove sale
  # ya, en paralelo con la comprobacion siguiente, y se informa cuanto tardo.
  cli "/system-property=$PROP_LEGADO:remove" &
  QUITAR=$!
  RECLAMO_R=$(curl -s -u "$ADMIN" "$BASE/reclamos" | tr '{' '\n' | grep "\"turnoId\":$IDR[,}]")
  comprobar "y todavia no se envio"                   "si" "$([ -n "$RECLAMO_R" ] && ! printf '%s' "$RECLAMO_R" | grep -q ENVIADO && echo si || echo no)"
  wait "$QUITAR"
  echo "  legado restaurado a los $(( $(date +%s) - T_CONFIRMAR )) s de confirmar (si pasa de ~30 s, el quinto intento puede ganar)"
  echo "  esperando el siguiente reintento (hasta 40 s)..."
  comprobar "al volver el legado el reclamo se envia" "si" "$(sondear "$ADMIN" /reclamos "\"estado\":\"ENVIADO\".*\"turnoId\":$IDR[,}]" 80)"

  # 3. Proveedor de video caido: la sala se crea dentro de la confirmacion.
  IDV=$(abrir 2027-01-18T12:00:00 TELEMEDICINA)
  reservar "$IDV" >/dev/null
  cli "/system-property=$PROP_VIDEO:add(value=true)"
  comprobar "video caido: confirmar da 503"            503 "$(confirmar_con "$PACI" "$IDV" tok_visa_demo)"
  comprobar "el turno sigue EN_HOLD"                  "si" "$(curl -s -u "$PACI" "$BASE/turnos/mios" | tr '{' '\n' | grep "\"id\":$IDV[,}]" | grep -q EN_HOLD && echo si || echo no)"
  # El copago ya se habia cobrado cuando fallo la sala: CompensacionDeCobro lo
  # devuelve despues del rollback, antes de que llegue el 503.
  comprobar "el cobro huerfano se reembolsa"          "si" "$(curl -s -u "$PACI" "$BASE/pagos?turnoId=$IDV" | grep -q '"estado":"REEMBOLSADO"' && echo si || echo no)"
  cli "/system-property=$PROP_VIDEO:remove"
  comprobar "video de vuelta: se confirma y hay sala" 200 "$(confirmar_con "$PACI" "$IDV" tok_visa_demo)"
  comprobar "el paciente paga una sola vez"           1 "$(curl -s -u "$PACI" "$BASE/pagos?turnoId=$IDV" | grep -o '"estado":"APROBADO"' | wc -l | tr -d ' ')"
  quedan=$(activas)
  comprobar "no queda ninguna falla en el servidor"   ninguna "${quedan:-ninguna}"
fi

echo
echo "-----------------------------------------------"
echo "  $ok correctas, $fallos fallidas"
[ "$fallos" -eq 0 ] || exit 1
