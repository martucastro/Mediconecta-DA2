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

RESERVA=$(curl -s -u "$PACI" -H "Content-Type: application/json" -d "{\"turnoId\":$ID}" "$BASE/turnos")
comprobar "reservar deja el turno EN_HOLD"    "si" "$(printf '%s' "$RESERVA" | grep -q EN_HOLD && echo si || echo no)"
comprobar "la respuesta NO filtra el hash"    "si" "$(printf '%s' "$RESERVA" | grep -q contrasena && echo no || echo si)"
comprobar "otro usuario NO confirma el hold"  403 "$(codigo -u "$PROF" -X PUT "$BASE/turnos/$ID/confirmar")"
comprobar "el paciente confirma el suyo"      200 "$(codigo -u "$PACI" -X PUT "$BASE/turnos/$ID/confirmar")"

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
# presentarReclamo (SCRUM-97 T4): presenta una autorizacion ya emitida, no
# evalua cobertura de nuevo.
R=$(soapReclamo 30333444 OS-2002 AUT-OS-2002-CONSULTA)
comprobar "presentarReclamo reconoce arancel x porcentaje del plan" "14000.00" \
  "$(printf '%s' "$R" | campo montoReconocido)"
comprobar "autorizacion desconocida en presentarReclamo da SOAP Fault Client" "si" \
  "$(soapReclamo 30333444 OS-2002 AUT-FALSO | grep -Eq '<faultcode[^>]*>[^<]*Client</faultcode>' && echo si || echo no)"

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
