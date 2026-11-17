# ServicioDeRecordatorios (Node.js + NestJS)

Componente de MediConecta escrito en otra tecnología que el resto del sistema.
Es un proceso Node.js aparte de WildFly y se integra solo por mensajería: es el
**tercer suscriptor del tópico JMS `TurnoConfirmado`**, junto a
`NotificacionMDB` y `TurnoConfirmadoFacturacionMDB`.

Por cada turno confirmado:

1. Programa un recordatorio para el paciente **24 h antes del turno**, o para
   ya mismo si falta menos que eso.
2. Lo guarda en su propio almacén, un archivo JSON (`data/recordatorios.json`).
   No toca las tablas de `mediconecta`.
3. Cuando llega la hora, lo "envía": escribe una línea de log, igual que el
   canal simulado de `ServicioDeNotificaciones`. Revisa los pendientes al
   arrancar, al recibir cada evento y cada minuto.

`GET /recordatorios` devuelve todos los recordatorios y `GET /recordatorios?turnoId=5`
los de un turno.

```
src/
  presentacion/  suscriptor-turno-confirmado.ts  (el "MDB" de Node: AMQP 1.0)
                 recordatorios.controller.ts     (GET /recordatorios)
  negocio/       servicio-de-recordatorios.ts    (cuándo y qué recordar)
  datos/         repositorio-de-recordatorios.ts (archivo JSON)
```

## Cómo se integra

| Aspecto | Valor |
|---|---|
| Protocolo | AMQP 1.0 (estándar OASIS), con la librería `rhea`, contra el Artemis embebido de WildFly |
| Puerto | 5672 (acceptor `amqp`, lo crea `deploy/mediconecta-setup.cli`) |
| Dirección | `jms.topic.TurnoConfirmadoTopic`: WildFly le antepone `jms.topic.` al nombre del tópico JMS |
| Cuerpo | El `MapMessage` que publica `ServicioDeTurnos` llega como mapa AMQP con las mismas claves: `turnoId`, `pacienteId`, `profesionalId` y `fechaHora` (ISO-8601 local, sin zona) |
| Suscripción | Durable: container-id `mediconecta-recordatorios` y enlace `recordatorios`. Artemis guarda los eventos mientras este proceso está caído y los entrega al reconectar |
| Seguridad | SASL PLAIN contra el `ApplicationRealm` de WildFly, con un usuario de aplicación del rol `recordatorios` |
| Errores | Un mensaje ilegible se descarta con un aviso, como en `NotificacionMDB`. Si falla guardar, el mensaje se libera y Artemis lo reentrega con la política del tópico (10 intentos y después la DLQ) |

`ServicioDeTurnos` no se modificó: publica el mismo evento de siempre, sin saber
que hay un suscriptor en otra tecnología.

## Cómo correrlo

Requiere Node.js 20 o posterior (probado con Node.js 26) y WildFly configurado con
`deploy/mediconecta-setup.cli` (paso 6: acceptor AMQP y permisos del tópico).

1. Crear el usuario de aplicación en WildFly. No es un comando de CLI, y la
   clave es un ejemplo:

   ```
   add-user.bat -a -u recordatorios -p recordatorios123 -g recordatorios
   ```

2. Instalar, probar y compilar:

   ```
   cd recordatorios
   npm ci
   npm test
   npm run build
   ```

3. Arrancar:

   ```
   # Linux, macOS o Git Bash
   ARTEMIS_CLAVE=recordatorios123 npm start

   # Windows (cmd)
   set ARTEMIS_CLAVE=recordatorios123
   npm start
   ```

| Variable | Por defecto | Para qué |
|---|---|---|
| `ARTEMIS_CLAVE` | (obligatoria) | Clave del usuario de aplicación |
| `ARTEMIS_USUARIO` | `recordatorios` | Usuario de aplicación de WildFly |
| `ARTEMIS_HOST` / `ARTEMIS_PUERTO` | `127.0.0.1` / `5672` | Acceptor AMQP. Con `port-offset` de WildFly, sumarlo al puerto |
| `PORT` | `3000` | Puerto HTTP de la API (solo escucha en `127.0.0.1`) |
| `RECORDATORIOS_ARCHIVO` | `data/recordatorios.json` | Almacén de recordatorios |

Si WildFly no está levantado, el servicio arranca igual y reintenta la conexión
solo. Con usuario, clave o permisos incorrectos el proceso termina con el error
de Artemis, porque reintentar no lo arregla.

## Demo

Con WildFly y este servicio corriendo:

```bash
BASE=http://localhost:8080/mediconecta/api
# El profesional abre una franja para mañana y el paciente la reserva y la confirma
curl -s -u profesional@mediconecta.com:cambiar123 -H "Content-Type: application/json" \
     -d '{"fechaHora":"2026-11-18T09:00:00"}' $BASE/turnos/disponibilidad      # -> "id": 3
curl -s -u paciente@mediconecta.com:cambiar123 -H "Content-Type: application/json" \
     -d '{"turnoId":3}' $BASE/turnos
curl -s -u paciente@mediconecta.com:cambiar123 -X PUT -H "Content-Type: application/json" \
     -d '{"tokenMedioDePago":"tok_visa_demo"}' $BASE/turnos/3/confirmar

curl -s "http://localhost:3000/recordatorios?turnoId=3"
```

En el log de este servicio aparece:

```
LOG [SuscriptorTurnoConfirmado] TurnoConfirmado recibido: turno 3, paciente 3, 2026-11-18T09:00
LOG [ServicioDeRecordatorios] Recordatorio programado para el turno 3: se envia el 2026-11-17T17:54:10.508Z
LOG [ServicioDeRecordatorios] Recordatorio enviado al paciente 3: tiene un turno el 2026-11-18T09:00 (turno 3)
```

Para mostrar la suscripción durable: cortar este servicio, confirmar otro turno
y volver a arrancarlo. El evento llega apenas reconecta.

## Limitaciones conocidas

- No hay evento `TurnoCancelado`: si el turno se cancela después de
  confirmarse, el recordatorio igual se envía.
- `fechaHora` viaja sin zona horaria (es un `LocalDateTime` de Java). Este
  servicio la interpreta en la zona del sistema, así que tiene que correr con la
  misma zona que WildFly.
- La API no tiene autenticación y por eso solo escucha en `127.0.0.1`.
- El almacén es un archivo JSON que se carga entero en memoria. Alcanza para la
  demo. Con más volumen, conviene pasarlo a SQLite.
