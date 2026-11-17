# MediConecta — Guion de la demo final (Entrega Final)

Duración objetivo: 25 minutos. Recorre el sistema de punta a punta con los tres roles y muestra cada integración en el momento en que actúa. El detalle de cada paso de Postman y las preguntas esperadas siguen en `docs/guion-demo-entrega-2.md`.

## 0. Preparación (antes de entrar)

1. PostgreSQL con la base `mediconecta`. Antes del primer arranque de WildFly: `MEDICONECTA_ADMIN_PASSWORD`, `MEDICONECTA_PROFESIONAL_PASSWORD` y `MEDICONECTA_PACIENTE_PASSWORD` en `cambiar123`, y `JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires` (README).
2. Usuario de aplicación para el servicio de Node: `add-user.bat -a -u recordatorios -p recordatorios123 -g recordatorios`.
3. WildFly con `standalone-full.xml` y, una vez, `jboss-cli --connect --file=deploy/mediconecta-setup.cli` (datasource, tópico, cola con su DLQ, acceptor AMQP 5672). Desplegar `target/mediconecta.war` y esperar `Deployed "mediconecta.war"`.
4. Ensayo: `JBOSS_CLI=… bash deploy/smoke-test.sh --rapido --resiliencia` termina con `… correctas, 0 fallidas`. Después, limpiar la base (3 usuarios semilla, 0 turnos, la afiliación OS-2002).
5. `ServicioDeRecordatorios`: en `recordatorios/`, `npm ci`, `npm run build` y `ARTEMIS_CLAVE=recordatorios123 npm start`. Debe loguear `Suscripcion durable activa en jms.topic.TurnoConfirmadoTopic`.
6. Pestañas: la SPA tres veces, una por rol (la sesión es por pestaña: abrir cada una escribiendo la URL), el WSDL (`/mediconecta/legado/obrasocial?wsdl`), Postman con `postman/MediConecta-Demo.postman_environment.json` y la carpeta `00 - Setup` ya corrida, la consola de WildFly y la de Node a la vista, y una sesión de `jboss-cli --connect` abierta.

Un paciente solo puede tener un hold vigente (SCRUM-132): si una reserva queda sin confirmar, soltarla antes de reservar otra, o la segunda responde 409.

## 1. Recorrido con los tres roles (unos 12 minutos)

| Paso | Rol y herramienta | Qué se hace | Qué se ve |
|---|---|---|---|
| 1 | Profesional, SPA | Login; cae en **Mi agenda**. Abre una franja presencial y una de telemedicina para hoy | Las franjas como libres; repetir el horario da el mensaje de conflicto (409). La agenda se actualiza sola cada 15 s |
| 2 | Paciente, SPA | Login; **Reservar un turno** → profesional → horario presencial → reservar | Pantalla del hold: cobertura "Autorizada · 70 %", copago $ 6.000 y cuenta regresiva desde 05:00. Cobertura por **SOAP**: log `Autorización AUT-OS-2002-CONSULTA registrada…` |
| 3 | Paciente, SPA | MEDIO DE PAGO "Tarjeta de prueba (se aprueba)" → **Confirmar turno** | "TURNO CONFIRMADO". Cobro por **REST**: log `Pasarela externa: solicitud de cobro por 6000.00 ARS`. En la pestaña del profesional el turno pasa solo a `CONFIRMADO` |
| 4 | Paciente, SPA | **Notificaciones** y **Mi historia clínica** | "Tu turno #X quedo confirmado…" (la escribió un MDB, ver sección 2). La historia todavía no existe: "Se abre en tu primera consulta" |
| 5 | Profesional, SPA | En la agenda, **Historia clínica** del paciente → **Abrir historia clínica** → registrar una consulta (CIE-10, diagnóstico y una receta) | "Consulta registrada." y la entrada en la historia. El paciente la ve al recargar **Mi historia clínica**. Una receta sin medicamento da el mensaje de validación (400) |
| 6 | Administrador, SPA | Login; cae en `/admin` | **Reclamos a obras sociales**: el reclamo del turno en `Enviado`, con monto y número de presentación. Filtro por estado y paginado |
| 7 | Paciente, SPA | Reservar y confirmar la franja de telemedicina | Copago $ 4.500 y **Unirse a la consulta**: el proveedor de video (**REST**) creó la sala dentro de la confirmación |

## 2. Lo que pasa después del commit: tópico y cola (unos 4 minutos)

La confirmación publica `TurnoConfirmado` en el **tópico** dentro de su transacción; los suscriptores lo reciben recién al commit, cada uno con su copia y en su propio hilo (`Thread-N (activemq-client-global)` en el log, no `default task-N`):

| Suscriptor | Tecnología | Evidencia |
|---|---|---|
| `NotificacionMDB` | Jakarta EE (MDB) | `Notificacion simulada para paciente 3: Tu turno #X quedo confirmado…` y la fila en `GET /api/notificaciones/mias` |
| `TurnoConfirmadoFacturacionMDB` | Jakarta EE (MDB) | `insert into reclamos` y el reclamo `PENDIENTE`; encola el pedido en la **cola** `ReclamosFacturacion` |
| `ServicioDeRecordatorios` | Node.js + NestJS, AMQP 1.0 | En la consola de Node: `TurnoConfirmado recibido: turno X…` y `Recordatorio programado para el turno X…`; `curl http://localhost:3000/recordatorios?turnoId=X` |

`ReclamoMDB` consume la **cola** (un solo consumidor, reintentos y DLQ propia), presenta el reclamo al legado por SOAP (`presentarReclamo`) y lo deja `ENVIADO` con `PRES-AUT-OS-2002-CONSULTA`: es lo que muestra el panel del administrador en el paso 6.

Para mostrar la suscripción durable de Node: detener el servicio, confirmar otro turno y volver a arrancarlo; el evento llega al reconectar.

## 3. Contratos: SOAP y REST (unos 3 minutos)

- **SOAP**: el WSDL publica `validarCobertura`, `autorizarPrestacion` y `presentarReclamo`, cada una con su `wsdl:fault` `PedidoInvalidoException`. En Postman, carpeta 06: los cuatro planes (100 %, 70 %, 40 %, sin cobertura) y un afiliado inexistente, que vuelve como SOAP Fault con `faultcode` `Client`. La API lo traduce a 400 ("La obra social rechazó el pedido…"); con el legado caído, a 503.
- **REST**: la API propia (`/mediconecta/api`, Basic, errores en JSON), la pasarela de pago y el proveedor de video simulados. En Postman, carpetas 01 (401/403 por rol), 07 (enlaces de la sala por rol) y 09 (afiliaciones: el administrador las registra, el paciente ve la suya).

## 4. Fallas y compensación (unos 6 minutos)

Postman, carpeta `08`, en orden; los comandos de `jboss-cli` están en la descripción de cada pedido `NOTA`.

| Caso | Cómo | Qué se ve |
|---|---|---|
| Pasarela caída o tarjeta rechazada | SPA o `08a`: `caer_demo` / `rechazar_demo` | 502 / 409. El turno sigue `EN_HOLD` y no hay ningún pago `APROBADO` |
| Legado caído en la reserva | `08b`: `:add` de `mediconecta.obrasocial.url` | 503 y la franja sigue `DISPONIBLE` |
| Legado caído al reclamar | `08c`: romper, confirmar (200), restaurar antes de los ~30 s | El reclamo suma `intentos` y después queda `ENVIADO`: la cola reintenta sola |
| Cobro huérfano | `08d`: `:add` de `mediconecta.video.simular-caida` y confirmar telemedicina | 503: la sala falló después del cobro. El turno sigue `EN_HOLD` y el pago queda `REEMBOLSADO` (`CompensacionDeCobro`). Al restaurar, se confirma con un solo `APROBADO` |
| Circuit breaker | Ver abajo | Tras 3 fallas seguidas el adaptador deja de llamar al sistema externo y falla al instante; pasados 10 s, una llamada de prueba lo cierra |
| Hold vencido | Una reserva sin confirmar (de Paciente B, para no bloquear al paciente semilla) | A los 5 minutos vuelve a `DISPONIBLE`: `Hold vencido (temporizador)…` (`ExpiradorDeHolds`) |

Antes de seguir: en `jboss-cli`, `:read-children-names(child-type=system-property)` no debe listar ninguna de las dos propiedades.

**Circuit breaker (unos 2 minutos).** Los tres adaptadores externos (`obra-social`, `pasarela-de-pago` y `proveedor-de-video`) usan `CircuitBreaker`: umbral de 3 fallas y 10 s abierto (`mediconecta.<sistema>.cb.umbral` y `cb.abiertoMs`). Correr, con la consola de WildFly a la vista:

```
JBOSS_CLI=…/bin/jboss-cli.sh SERVER_LOG=…/standalone/log/server.log bash deploy/demo-circuit-breaker.sh
```

1. Tira el proveedor de video y confirma 6 veces una teleconsulta. Todas responden 503 con `Retry-After`, y en el log aparece una sola vez `Circuit breaker 'proveedor-de-video': ABIERTO…`.
2. Con el circuito abierto, otro paciente reserva y confirma un turno presencial con su copago, y el resto de la API responde 200.
3. Restaura el proveedor y, pasados 11 s, la misma teleconsulta se confirma (`…: CERRADO, el sistema externo volvio.`). El paciente paga una sola vez: cada intento fallido se reembolsó.

Con la pasarela el contrato no cambia: con el circuito abierto, `--resiliencia` del smoke muestra que un token que aprueba también da 502, sin llamar a la pasarela.

## 5. Cierre

Frase: "Lo que decide si el turno se confirma (cobertura, cobro y sala) es síncrono y está dentro de la transacción; lo que reacciona a un turno ya confirmado (notificación, reclamo y recordatorio) es asincrónico, corre después del commit y uno de sus suscriptores está escrito en otra tecnología".

Si falla la SPA, el mismo recorrido está en Postman (carpetas 00, 01, 02 completa, 02b, 03, 05, 06, 07 y 09). Si falla el servidor, mostrar la salida guardada de `deploy/smoke-test.sh` y los diagramas de secuencia del documento.
