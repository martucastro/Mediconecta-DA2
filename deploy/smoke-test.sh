#!/usr/bin/env bash
# Verificacion de humo de MediConecta.
#
# Recorre el flujo completo contra un servidor ya desplegado y comprueba que
# la seguridad y el hold del turno se comporten como se espera.
#
#   bash deploy/smoke-test.sh
#
# El ultimo caso espera 5 minutos a proposito: es la expiracion del hold, que
# la gestiona el contenedor. Para saltearlo: bash deploy/smoke-test.sh --rapido

set -u
BASE="${BASE:-http://127.0.0.1:8080/mediconecta/api}"
ADMIN="admin@mediconecta.com:cambiar123"
PROF="profesional@mediconecta.com:cambiar123"
PACI="paciente@mediconecta.com:cambiar123"

ok=0; fallos=0

comprobar() { # descripcion, esperado, obtenido
  if [ "$2" = "$3" ]; then
    printf "  OK    %-52s %s\n" "$1" "$3"; ok=$((ok+1))
  else
    printf "  FALLA %-52s esperaba %s, obtuvo %s\n" "$1" "$2" "$3"; fallos=$((fallos+1))
  fi
}

codigo() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "MediConecta, verificacion de humo contra $BASE"
echo
echo "Autenticacion y autorizacion"
comprobar "anonimo a historia clinica"        401 "$(codigo "$BASE/historias/paciente/3")"
comprobar "credencial incorrecta"             401 "$(codigo -u "admin@mediconecta.com:mal" "$BASE/usuarios")"
comprobar "admin lista usuarios"              200 "$(codigo -u "$ADMIN" "$BASE/usuarios")"
comprobar "paciente NO lista usuarios"        403 "$(codigo -u "$PACI" "$BASE/usuarios")"
comprobar "profesional NO lista usuarios"     403 "$(codigo -u "$PROF" "$BASE/usuarios")"

echo
echo "Turnos"
NUEVO=$(curl -s -u "$PROF" -H "Content-Type: application/json" \
        -d '{"fechaHora":"2027-01-15T10:00:00"}' "$BASE/turnos/disponibilidad")
ID=$(printf '%s' "$NUEVO" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
comprobar "profesional abre disponibilidad"   "si" "$([ -n "$ID" ] && echo si || echo no)"
comprobar "paciente NO abre disponibilidad"   403 "$(codigo -u "$PACI" -H "Content-Type: application/json" \
                                                     -d '{"fechaHora":"2027-01-15T11:00:00"}' "$BASE/turnos/disponibilidad")"

RESERVA=$(curl -s -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID}" "$BASE/turnos")
comprobar "reservar deja el turno EN_HOLD"    "si" "$(printf '%s' "$RESERVA" | grep -q EN_HOLD && echo si || echo no)"
comprobar "la respuesta NO filtra el hash"    "si" "$(printf '%s' "$RESERVA" | grep -q contrasena && echo no || echo si)"
comprobar "otro usuario NO confirma el hold"  403 "$(codigo -u "$PROF" -X PUT "$BASE/turnos/$ID/confirmar")"
comprobar "el paciente confirma el suyo"      200 "$(codigo -u "$PACI" -X PUT "$BASE/turnos/$ID/confirmar")"

if [ "${1:-}" != "--rapido" ]; then
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
    if curl -s -u "$PACI" "$BASE/turnos?profesionalId=2" | grep -q "\"id\":$ID2"; then
      liberado=si
      echo "  liberado a los $(( $(date +%s) - inicio )) segundos"
      break
    fi
  done
  comprobar "el hold vencido libera el turno"  "si" "$liberado"
fi

echo
echo "-----------------------------------------------"
echo "  $ok correctas, $fallos fallidas"
[ "$fallos" -eq 0 ] || exit 1
