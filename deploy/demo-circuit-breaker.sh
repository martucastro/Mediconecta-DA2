#!/usr/bin/env bash
# Demostracion del circuit breaker: con un sistema externo caido, el resto de
# la plataforma sigue funcionando.
#
#   JBOSS_CLI=/ruta/a/wildfly/bin/jboss-cli.sh bash deploy/demo-circuit-breaker.sh
#
# Simula la caida del proveedor de video (mediconecta.video.simular-caida=true,
# se cambia en caliente con jboss-cli), confirma un turno de telemedicina varias
# veces y comprueba:
#   1. tras 3 fallas seguidas el circuito se abre (queda en el log) y el
#      adaptador falla al instante, sin llamar al proveedor;
#   2. mientras tanto, otro paciente reserva y confirma un turno presencial
#      (con su copago), y los demas endpoints responden 200;
#   3. al volver el proveedor, pasado el tiempo de apertura, la confirmacion
#      del mismo turno funciona (el circuito se cierra solo) y el paciente
#      paga una sola vez: cada intento fallido se reembolso (CompensacionDeCobro).
#
# Variables: BASE (URL de la API), JBOSS_CLI (ruta a jboss-cli.sh o .bat, como
# en smoke-test.sh --resiliencia), SERVER_LOG (ruta de server.log, opcional,
# para verificar apertura y cierre) y las contrasenas del seed.

set -u
BASE="${BASE:-http://127.0.0.1:8080/mediconecta/api}"
SERVER_LOG="${SERVER_LOG:-}"
ADMIN="admin@mediconecta.com:${MEDICONECTA_ADMIN_PASSWORD:-cambiar123}"
PROF="profesional@mediconecta.com:${MEDICONECTA_PROFESIONAL_PASSWORD:-cambiar123}"
PACI="paciente@mediconecta.com:${MEDICONECTA_PACIENTE_PASSWORD:-cambiar123}"
PROP="/system-property=mediconecta.video.simular-caida"
PAGO=(-H "Content-Type: application/json" -d '{"tokenMedioDePago":"tok_visa_demo"}')

if [ -z "${JBOSS_CLI:-}" ] || [ ! -f "$JBOSS_CLI" ]; then
  echo "Hace falta JBOSS_CLI con la ruta a jboss-cli.sh o jboss-cli.bat"
  exit 2
fi

ok=0; fallos=0
comprobar() { # descripcion, esperado, obtenido
  if [ "$2" = "$3" ]; then printf "  OK    %-58s %s\n" "$1" "$3"; ok=$((ok+1))
  else printf "  FALLA %-58s esperaba %s, obtuvo %s\n" "$1" "$2" "$3"; fallos=$((fallos+1)); fi
}
codigo() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
cli() { "$JBOSS_CLI" --connect "$@" >/dev/null 2>&1 </dev/null; }
restaurar() { cli "$PROP:remove"; }
trap restaurar EXIT
restaurar   # parte de un estado limpio, por si quedo algo de una corrida anterior

# Fecha unica por corrida para no chocar con franjas de corridas anteriores.
D=$(( ($(date +%s) / 60) % 28 + 1 )); H=$(( ($(date +%s) / 3600) % 8 + 9 ))
FECHA=$(printf '2028-03-%02dT%02d:%02d:00' "$D" "$H" $(( $(date +%s) % 60 )))
nueva() { curl -s -u "$PROF" -H "Content-Type: application/json" \
  -d "{\"fechaHora\":\"$1\",\"modalidad\":\"$2\"$3}" "$BASE/turnos/disponibilidad" | sed -n 's/.*"id":\([0-9]*\).*/\1/p'; }
reservar() { codigo -u "$1" -H "Content-Type: application/json" -d "{\"turnoId\":$2}" "$BASE/turnos"; }
cuenta() { grep -c "Circuit breaker 'proveedor-de-video': $1" "$SERVER_LOG"; }

echo "Circuit breaker: el proveedor de video se cae, la plataforma sigue"
echo
# Un hold vigente por paciente: el turno presencial lo reserva otro paciente,
# que se registra ahora y se atiende como particular.
OTRO="demo-cb-$(date +%s)@mediconecta.com:cambiar123"
curl -s -o /dev/null -H "Content-Type: application/json" \
     -d "{\"nombre\":\"Paciente demo\",\"email\":\"${OTRO%%:*}\",\"rol\":\"PACIENTE\",\"contrasena\":\"${OTRO#*:}\"}" \
     "$BASE/usuarios"
TELE=$(nueva "$FECHA" TELEMEDICINA "")
PRES=$(nueva "${FECHA%T*}T23:30:00" PRESENCIAL ',"consultorio":"101"')
comprobar "reservar el turno de telemedicina" 200 "$(reservar "$PACI" "$TELE")"
[ -n "$SERVER_LOG" ] && ANTES=$(cuenta ABIERTO)

echo "1) Se cae el proveedor de video (simulado)"
cli "$PROP:add(value=true)"
for i in 1 2 3 4 5 6; do
  r=$(curl -s -o /dev/null -D - -u "$PACI" -X PUT "${PAGO[@]}" "$BASE/turnos/$TELE/confirmar" -w '%{http_code}' | tr -d '\r')
  c=${r##*$'\n'}; ra=$(printf '%s' "$r" | grep -i '^retry-after' | head -1)
  comprobar "confirmar telemedicina, intento $i (503 + Retry-After)" "503 con Retry-After" \
            "$c $([ -n "$ra" ] && echo con Retry-After || echo sin Retry-After)"
done
if [ -n "$SERVER_LOG" ]; then
  comprobar "el log registra la apertura del circuito (una vez)" 1 "$(( $(cuenta ABIERTO) - ANTES ))"
fi

echo
echo "2) Con el circuito abierto, el resto de la plataforma funciona"
comprobar "otro paciente reserva un turno PRESENCIAL"  200 "$(reservar "$OTRO" "$PRES")"
comprobar "y lo confirma (cobra el copago)"           200 "$(codigo -u "$OTRO" -X PUT "${PAGO[@]}" "$BASE/turnos/$PRES/confirmar")"
comprobar "mis turnos (paciente)"                     200 "$(codigo -u "$PACI" "$BASE/turnos/mios")"
comprobar "listar usuarios (admin)"                   200 "$(codigo -u "$ADMIN" "$BASE/usuarios")"
comprobar "disponibilidad de turnos"                  200 "$(codigo -u "$PACI" "$BASE/turnos?profesionalId=2")"
comprobar "el turno de telemedicina sigue EN_HOLD"    "si" \
          "$(curl -s -u "$PACI" "$BASE/turnos/mios" | tr '{' '\n' | grep "\"id\":$TELE[,}]" | grep -q EN_HOLD && echo si || echo no)"

echo
echo "3) Vuelve el proveedor; el circuito se cierra solo tras el tiempo de apertura"
restaurar
sleep "${ESPERA_S:-11}"
comprobar "confirmar el mismo turno de telemedicina" 200 "$(codigo -u "$PACI" -X PUT "${PAGO[@]}" "$BASE/turnos/$TELE/confirmar")"
comprobar "el paciente paga una sola vez"            1 "$(curl -s -u "$PACI" "$BASE/pagos?turnoId=$TELE" | grep -o '"estado":"APROBADO"' | wc -l | tr -d ' ')"
[ -n "$SERVER_LOG" ] && comprobar "el log registra el cierre del circuito" "si" \
          "$(tail -n 60 "$SERVER_LOG" | grep -q "proveedor-de-video': CERRADO" && echo si || echo no)"

echo
echo "Resultado: $ok correctas, $fallos fallidas"
[ "$fallos" -eq 0 ]
