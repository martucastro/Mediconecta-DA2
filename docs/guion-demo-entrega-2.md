# MediConecta — Guion de la demo y defensa oral (Entrega Obligatoria N.º 2)

Duración objetivo: 20 a 25 minutos. La demo recorre un flujo de punta a punta que cruza componentes internos, mensajería y servicios web (login → disponibilidad → reserva con cobertura SOAP → confirmación con copago REST, sala de video REST y tópico → notificación y reclamo por cola), y después muestra las fallas a mitad del flujo. Todo lo que se muestra sale del código del repositorio, de `deploy/smoke-test.sh` y de la colección `postman/MediConecta-Demo.postman_collection.json` con su entorno.

## 0. Preparación (antes de entrar, no cuenta en el tiempo)

1. PostgreSQL arriba con la base `mediconecta`. Si la base está vacía, definir antes del primer arranque `MEDICONECTA_ADMIN_PASSWORD`, `MEDICONECTA_PROFESIONAL_PASSWORD` y `MEDICONECTA_PACIENTE_PASSWORD` con `cambiar123`, que es la contraseña que esperan el entorno de Postman y el smoke test (si no se definen, el sembrado genera contraseñas al azar y las escribe una sola vez en el log).
2. WildFly con el perfil completo, que es el que trae Artemis: `standalone.bat -c standalone-full.xml`. Ejecutado una vez, desde la raíz del repositorio, `jboss-cli.sh --connect --file=deploy/mediconecta-setup.cli` (datasource, tópico, cola y DLQ, con las políticas de reentrega del tópico y de la cola).
3. `mvn clean package` y copiar `target/mediconecta.war` a `standalone/deployments/`. Esperar `Deployed "mediconecta.war"`.
4. Ensayo rápido: `bash deploy/smoke-test.sh --rapido` debe terminar con `… correctas, 0 fallidas` (con `JBOSS_CLI=… bash deploy/smoke-test.sh --rapido --resiliencia` también ensaya las fallas de la sección 2b). Deja datos propios (turnos del 15/01/2027 con sus notificaciones, pagos y reclamos, y una franja de telemedicina libre) que después aparecen en los listados de la demo; no interfieren.
5. Pestañas abiertas: la SPA (`http://localhost:8080/mediconecta/`) dos veces, una para el paciente y otra para el profesional (la sesión de la SPA es por pestaña; abrir la segunda escribiendo la URL, no duplicando la primera), el WSDL (`http://localhost:8080/mediconecta/legado/obrasocial?wsdl`), Postman con el entorno `postman/MediConecta-Demo.postman_environment.json` (en Postman figura como "MediConecta - Demo Entrega Obligatoria N1"; `token_medio_de_pago = tok_visa_demo`), la consola de WildFly a la vista (el log es parte de la evidencia) y una terminal con `jboss-cli --connect`.
6. Comandos de `jboss-cli` en Windows: conviene dejar abierta una sesión interactiva de `jboss-cli.bat --connect` y escribir ahí los comandos de la sección 2 sin las comillas simples; responde al instante, mientras que cada invocación suelta arranca una JVM (la primera tardó unos 20 s en el ensayo). Pasados como argumento, los comandos entre comillas simples funcionan tal cual en Git Bash (`jboss-cli.sh`) y en PowerShell (`.\jboss-cli.bat`), pero no en `cmd.exe`, que no reconoce las comillas simples.
7. La consola también muestra el SQL de Hibernate y un `ERROR` con stack trace en cada 403 que decide un EJB (`WFLYEJB0034`) y en cada reintento fallido de la cola (`AMQ154004`): es lo esperado. Para encontrar las líneas citadas, buscar el texto (Ctrl+F) o filtrar `standalone/log/server.log`.
8. En Postman, correr una vez la carpeta `00 - Setup`: registra a Paciente B. Sin ella, los pedidos de PACIENTE_B de las carpetas 02 y 07 responden 401 y sus pruebas fallan.
9. Tener a mano el PDF del documento técnico (diagramas de secuencia de cada integración).
10. Opcional: al empezar, abrir una franja extra en la agenda del profesional y reservarla sin confirmarla como Paciente B (`POST /api/turnos` con sus credenciales), para mostrar al final que el hold venció solo (5 minutos). Un paciente solo puede tener un hold vigente (SCRUM-132): si la reserva el paciente semilla, sus reservas siguientes responden 409 hasta que el hold vence.

Tokens de la pasarela simulada (`PasarelaExternaResource`): cualquier token aprueba (`approved`), salvo los que empiezan con `rechazar` (`rejected`) o con `caer` (responde 503 y MediConecta devuelve 502). En la SPA, la pantalla del hold muestra el selector **MEDIO DE PAGO** solo cuando el turno tiene copago, con tres opciones: "Tarjeta de prueba (se aprueba)" (`tok_visa_demo`, por defecto), "Tarjeta de prueba (se rechaza)" (`rechazar_demo`) y "Simular pasarela caída" (`caer_demo`). La confirmación siempre envía `{ "tokenMedioDePago": … }` en el cuerpo del `PUT`, y ante un rechazo (409) o una caída de la pasarela (502) la SPA muestra el mensaje del backend.

## 1. Recorrido (camino feliz, unos 12 minutos)

| Min. | Paso | Herramienta | Qué se muestra | Resultado esperado |
|---|---|---|---|---|
| 0–2 | Arquitectura e integraciones | PDF, secciones 1 y 2 | Ocho componentes, tabla de integraciones y el criterio síncrono/asincrónico | — |
| 2–4 | Login y roles | SPA (login del paciente) y Postman, carpeta 01 | Login correcto; anónimo 401; paciente que lista usuarios 403; `GET /usuarios/{id}` propio 200 y ajeno 403 (SCRUM-102) | 200 / 401 / 403 |
| 4–5 | El profesional abre franjas | SPA, agenda del profesional en la otra pestaña (o Postman 02 "Abrir disponibilidad como PROFESIONAL") | Una franja `PRESENCIAL` y una `TELEMEDICINA`. Abrir de nuevo el mismo horario da 409 ("El profesional ya tiene una franja abierta el dd/mm/aaaa hh:mm"). Un paciente que intenta abrir franjas recibe 403 (Postman 02 "Abrir disponibilidad como PACIENTE (403)"). Dejar la agenda a la vista: se actualiza sola cada 15 s | 201 (409 / 403) |
| 5–7 | Reserva con cobertura (SOAP) | SPA: Disponibilidad → elegir profesional → elegir horario (cada botón muestra la modalidad) → reservar | La pantalla del hold muestra la cobertura ("Autorizada · 70 %"), el copago ($ 6.000, el 30 % de un arancel de 20.000) y la cuenta regresiva desde 05:00. En el log: `Autorización AUT-OS-2002-CONSULTA registrada…`. Mostrar el WSDL en el navegador | 200, turno `EN_HOLD` |
| 7–9 | Confirmación con copago (REST) | SPA: en MEDIO DE PAGO dejar "Tarjeta de prueba (se aprueba)" y Confirmar | La SPA muestra "TURNO CONFIRMADO". En el log: `Pasarela externa: solicitud de cobro por 6000.00 ARS`, luego `Notificacion simulada para paciente…`. En la pestaña del profesional, el turno pasa solo a `CONFIRMADO` (en menos de 15 s, sin recargar) | 200, turno `CONFIRMADO` |
| 9–10 | Lo que pasó después del commit (tópico y cola) | Postman, carpeta 05 (detalle en la sección 1b), o `curl -u` | `GET /api/notificaciones/mias` como paciente ("Tu turno #X quedo confirmado para el dd/mm/aaaa a las hh:mm."; de ahí sale el id del turno); `GET /api/reclamos` como administrador (reclamo `ENVIADO` con número de presentación `PRES-AUT-OS-2002-CONSULTA`); `GET /api/pagos?turnoId={id}` como paciente (pago `APROBADO`). Los listados incluyen también lo que dejó el smoke test | 200 |
| 10–12 | Telemedicina (REST) | SPA o Postman 02b/07: reservar y confirmar la franja de telemedicina con `tok_visa_demo` | Copago 4500 (teleconsulta). En el log: `Proveedor de video externo: solicitud de sala para turno-{id}`. La SPA ofrece "Unirse a la consulta". `GET /api/telemedicina/turno/{id}` como paciente (enlace de invitado, rol `PACIENTE`) y como profesional (enlace de anfitrión, rol `PROFESIONAL`) | 200, cada uno ve solo su enlace |

### 1b. Mensajería asincrónica, paso a paso (Postman, carpeta `05`; unos 4 minutos)

Sirve como bloque propio o como detalle del paso 9–10. Requisitos: sección 0 completa (WildFly con `standalone-full.xml`, `mediconecta-setup.cli` ejecutado, WAR desplegado), entorno de Postman seleccionado (`base_url` termina en `/mediconecta/api`) y `00 - Setup` corrido. Dejar la consola de WildFly (o `standalone/log/server.log`) visible, con el formato `(%t)` que trae WildFly: la columna entre paréntesis es el nombre del hilo.

| Orden | Qué se hace | Qué se ve |
|---|---|---|
| 1 | Carpeta `05`, pedido **1 - Abrir disponibilidad** (PROFESIONAL) | 201; el test guarda `turno_id_async` |
| 2 | Pedido **2 - Reservar** (PACIENTE_A) | 201, `EN_HOLD` (5 minutos para confirmar) |
| 3 | Pedido **3 - Confirmar** (PACIENTE_A, con `token_medio_de_pago = tok_visa_demo`) | 200. Mirar la consola **inmediatamente**: aparece `Notificacion simulada para paciente …: Tu turno #X quedo confirmado …` en un hilo `activemq-client-global` |
| 4 | Pedido **4 - Verificar la notificación** (PACIENTE_A) | 200 y la notificación del turno (`canal` `LOG`, `mensaje`, `fechaEnvio`). Si el test dice que no la encuentra, reenviar el pedido: es el MDB terminando en otro hilo |
| 5 | Pedido **5 - Verificar el reclamo** (ADMINISTRADOR) | Reclamo del turno `ENVIADO` (o `PENDIENTE` unos segundos): segundo suscriptor del tópico, cola y SOAP |

**Cómo se evidencia que fue asincrónico** (mostrar al menos las dos primeras):

1. **El hilo en el log.** Las líneas del pedido HTTP del `PUT /confirmar` (el SQL de la confirmación, el cobro `Pasarela externa: solicitud de cobro…`) salen con un hilo de Undertow, `(default task-N)`. La línea `Notificacion simulada para paciente …` sale con un hilo del cliente de Artemis, `(Thread-N (activemq-client-global))`. Además, el segundo suscriptor del tópico (facturación: el SQL de `insert into reclamos`) corre en *otro* hilo `activemq-client-global` distinto del de la notificación. Ejemplo real del ensayo, en el orden en que aparecen en el log:

```
09:58:47,399 INFO  [stdout] (default task-2) Hibernate: update turnos set coberturaAutorizada=?, … (la confirmación)
09:58:47,477 INFO  [stdout] (Thread-3 (activemq-client-global)) Hibernate: insert into reclamos …
09:58:47,481 INFO  [ar.edu.uade.da2.mediconecta.notificaciones.negocio.ServicioDeNotificaciones] (Thread-2 (activemq-client-global)) Notificacion simulada para paciente 3: Tu turno #1 quedo confirmado para el 04/06/2027 a las 10:17.
```

   Dos hilos distintos del de la petición = el consumo no ocurrió dentro de ella. Para señalarlo, buscar (Ctrl+F) `Notificacion simulada` y mirar el paréntesis; o en PowerShell: `Select-String -Path standalone\log\server.log -Pattern 'Notificacion simulada|activemq-client-global'`. Los números de hilo cambian en cada arranque.
2. **Después del commit, no dentro.** `confirmarTurno` publica el mensaje dentro de su transacción y el MDB solo lo recibe cuando esa transacción cierra: en el ejemplo, unos 80 ms después del `update turnos` de la confirmación. Por eso el pedido 4 es una verificación posterior: la notificación existe porque `NotificacionMDB` la escribió, no porque el `PUT` la creara (el `PUT` no devuelve ninguna notificación). La consola de Postman deja la hora local de la respuesta (`t_respuesta_confirmar`) para ubicarla en el log. No afirmar que la notificación se registra *después* de la respuesta HTTP: ambas ocurren a pocas decenas de milisegundos y el orden no está garantizado.
3. **El registro persistido.** El pedido 4 muestra que el mensaje se consumió sin mirar el log (la tabla de notificaciones la escribe `NotificacionMDB` vía `ServicioDeNotificaciones`).
4. *(Opcional)* Idempotencia y reentrega: ver la sección "Política de redelivery" del README y la fila "Reintentos de la cola" de la sección 2.

Si no aparece nada en el log: comprobar que el tópico existe (`/subsystem=messaging-activemq/server=default/jms-topic=TurnoConfirmadoTopic:read-resource` en `jboss-cli`) y que el despliegue no dejó errores del MDB.

Frase para cerrar el recorrido: "La reserva y la confirmación son cada una una transacción JTA; lo que decide si el flujo sigue (cobertura, cobro, sala) es síncrono y está adentro; lo que reacciona a un turno ya confirmado (notificación y reclamo) es asincrónico y corre después del commit".

## 2. Fallas a mitad del flujo (unos 8 minutos)

Los casos de la pasarela, del legado y del video también están armados en Postman (carpeta `08`, sección 2b). Para hacerlos a mano, antes de empezar abrir en la agenda del profesional al menos cuatro franjas presenciales y una de telemedicina; sus ids salen de `GET /api/turnos?profesionalId={id}` (el id del profesional, de `GET /api/usuarios/profesionales`). Para cada caso: soltar el hold anterior si quedó alguno (`PUT /api/turnos/X/cancelar`; un segundo hold del mismo paciente da 409), reservar una franja nueva (`POST /api/turnos` con `{"turnoId": X}` como paciente) y después confirmarla con el cuerpo indicado (`PUT /api/turnos/X/confirmar`, `Content-Type: application/json`). Los dos primeros casos también se muestran desde la SPA, eligiendo la opción correspondiente en MEDIO DE PAGO.

| Caso | Cómo provocarlo | Respuesta | Qué mostrar después |
|---|---|---|---|
| Pago rechazado | SPA: "Tarjeta de prueba (se rechaza)"; Postman: cuerpo `{"tokenMedioDePago": "rechazar_demo"}` | 409 ("El copago del turno X fue rechazado por la pasarela (estado RECHAZADO).") | El turno sigue `EN_HOLD` (SPA o `GET /api/turnos/mios`). Confirmar de nuevo con la tarjeta que se aprueba: 200 (se reintenta dentro del hold). `GET /api/pagos?turnoId=X` muestra el intento `RECHAZADO` y después el `APROBADO` |
| Pasarela caída | SPA: "Simular pasarela caída"; Postman: cuerpo `{"tokenMedioDePago": "caer_demo"}` | 502, con el mensaje del backend en la SPA ("La pasarela respondió con estado HTTP 503.") | El turno sigue `EN_HOLD`; nada se cobró: `GET /api/pagos?turnoId=X` muestra solo un intento `PENDIENTE` |
| Copago sin medio de pago | Postman: cuerpo `{}` | 400 | El turno sigue `EN_HOLD`. Un cuerpo que no es JSON válido también da 400 ("El cuerpo del pedido no es JSON válido o tiene tipos incorrectos.") |
| Legado de la obra social caído | `jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:add(value="http://127.0.0.1:1/x")'` y reservar | 503 ("El sistema de la obra social no respondió. Intentá de nuevo en unos minutos.") | `GET /api/turnos?profesionalId={id}`: la franja sigue `DISPONIBLE`. Volver atrás con `jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:remove'` |
| Reintentos de la cola | Reservar con el legado arriba; romper la URL del legado como en la fila anterior; confirmar con `tok_visa_demo` (200); mirar el log y `GET /api/reclamos` como administrador | — | El reclamo suma intentos (reentregas a los 2, 4, 8 y 16 s; cada intento deja en el log `El legado de la obra social no respondió a presentarReclamo…`). Variante A: restaurar la URL antes del quinto intento, que llega unos 30 s después de confirmar, y el reclamo pasa a `ENVIADO`. Variante B: dejarla rota y queda `EN_REVISION_MANUAL` con 5 intentos; después quitar la propiedad con `:remove`, porque sin el legado ninguna reserva funciona. `/subsystem=messaging-activemq/server=default/jms-queue=ReclamosFacturacionQueue:count-messages` y `…/jms-queue=ReclamosFacturacionDLQ:count-messages` quedan en 0 |
| Proveedor de video caído (opcional) | `jboss-cli.sh --connect '/system-property=mediconecta.video.simular-caida:add(value=true)'` y confirmar una franja de telemedicina con `tok_visa_demo` | 503 con `Retry-After: 30` | El turno sigue `EN_HOLD`. En el log, `Pasarela externa: solicitud de cobro por 4500.00 ARS` aparece antes que `Proveedor de video externo: solicitud de sala para turno-X`: el cobro (que con `tok_visa_demo` se aprueba) salió antes de que fallara la sala. `CompensacionDeCobro` lo devuelve después del rollback: `GET /api/pagos?turnoId=X` muestra el pago `REEMBOLSADO` (o `COMPENSACION_PENDIENTE` si el reembolso también falló). Quitar la propiedad con `jboss-cli.sh --connect '/system-property=mediconecta.video.simular-caida:remove'` |
| Hold vencido (opcional) | El turno reservado al principio | — | Pasados 5 minutos volvió a `DISPONIBLE` sin que nadie lo tocara. En el log: `Hold vencido (temporizador): el turno X vuelve a estar disponible.` (`ExpiradorDeHolds`) |

La DLQ no se demuestra en vivo: con la lógica actual, el quinto intento marca el reclamo `EN_REVISION_MANUAL` y confirma el mensaje, así que a `ReclamosFacturacionDLQ` solo llegan mensajes cuyas cinco entregas fallan por un error no previsto (por ejemplo, la base caída). Se explica con el diagrama de la sección 3.3.

### 2b. Demo de resiliencia con Postman (carpeta `08`; unos 6 minutos)

Cada falla se provoca, se muestra y se revierte. Requisitos: los de la sección 1b y una terminal con el `jboss-cli` de WildFly. En Postman, las subcarpetas se corren en orden y de a una; los pedidos `NOTA` no cambian nada: llevan en su descripción el comando que hay que pegar en la terminal.

| Subcarpeta | Qué se muestra | Qué se ve |
|---|---|---|
| `08a` Pasarela de pago | Una franja, tres fallas del cobro y recuperación: `caer_demo` → 502, `rechazar_demo` → 409, cuerpo `{}` → 400; después `tok_visa_demo` → 200 | El turno sigue `EN_HOLD` (pedido 6). `GET /pagos`: antes, `PENDIENTE` (sin id de transacción) y `RECHAZADO`, ninguno `APROBADO` (pedido 7); después también `APROBADO` (pedido 9). En el log: `Pasarela externa: solicitud de cobro por 6000.00 ARS` por cada intento |
| `08b` Legado caído en la reserva | Pedido 2 (terminal): `:add` de `mediconecta.obrasocial.url` apuntando a `http://127.0.0.1:1/x`. Reservar → 503 | El mensaje "El sistema de la obra social no respondió…", la franja sigue `DISPONIBLE` (pedido 4) y en el log un `WARNING` "El legado de la obra social no respondió a autorizarPrestacion…". Pedido 5 (terminal): `:remove`; la misma franja se reserva (pedido 6) |
| `08c` Legado caído al reclamar | Reservar con el legado arriba, romperlo, confirmar (200: la confirmación no depende del legado) | El reclamo suma `intentos` con `ultimoError` (pedido 5) y en el log, por cada intento, un `WARNING` "…no respondió a presentarReclamo…" y un `ERROR AMQ154004` con stack trace (es el reintento de la cola, lo esperado). Restaurar (pedido 6) **antes del quinto intento** (unos 30 s) y reenviar el pedido 7 hasta ver `ENVIADO` |
| `08d` Video caído | Pedido 3 (terminal): `:add` de `mediconecta.video.simular-caida` con `true`. Confirmar telemedicina → 503 con `Retry-After: 30` | El turno sigue `EN_HOLD`. En el log, `Pasarela externa: solicitud de cobro por 4500.00 ARS` aparece antes que `Proveedor de video externo: solicitud de sala…`: el cobro salió antes de que fallara la sala, y se reembolsó solo (pedido 5b: `REEMBOLSADO`). Restaurar (pedido 6) y confirmar de nuevo (200): un solo `APROBADO` (pedido 8) |

**Antes de seguir con otra parte de la demo, comprobar que no quedó ninguna falla activa**: sin el legado ninguna reserva funciona. En `jboss-cli`: `/system-property=mediconecta.obrasocial.url:read-resource` y `/system-property=mediconecta.video.simular-caida:read-resource` deben responder "not found" (`WFLYCTL0216`).

El mismo recorrido está automatizado en `deploy/smoke-test.sh`: sin opciones cubre `08a` (pasarela), y con `--resiliencia` (y `JBOSS_CLI`) agrega `08b`, `08c` y `08d`, quitando las propiedades al salir aunque falle. Es la comprobación previa a la demo (sección 0, paso 4).

## 3. Respuestas breves para las preguntas esperadas

**¿Por qué cada integración es síncrona o asincrónica?**
- Cobertura con la obra social (SOAP, síncrono): el contrato SOAP lo impone el legado, y el copago que se cobra al confirmar depende de esa respuesta. Sin respuesta no se reserva (503).
- Pasarela de pago (REST, síncrono): sin cobro aprobado no se confirma; es un partner moderno con API REST.
- Proveedor de video (REST, síncrono): invariante "todo turno de telemedicina confirmado tiene su enlace".
- Tópico `TurnoConfirmado` (asincrónico): es un hecho consumado con varios interesados independientes; el publicador no los conoce ni los espera.
- Cola `ReclamosFacturacion` (asincrónico): el reclamo se procesa exactamente una vez, con reintentos y DLQ, y la caída de la obra social no afecta al paciente.
- Regla general: lo que decide si el flujo sigue es síncrono; lo que reacciona después del commit es asincrónico.

**¿Qué pasa si algo falla a mitad de la confirmación?** Toda la confirmación es una transacción JTA (`REQUIRED`). Los observadores corren antes de cambiar el estado, y las excepciones son `@ApplicationException(rollback = true)`: el turno sigue `EN_HOLD`, el temporizador sigue vivo y el mensaje al tópico no sale. Pasarela caída 502, rechazo 409, video caído 503. Lo que JTA no deshace es una llamada HTTP ya hecha: si el cobro se aprobó y después falla la sala, queda un cobro huérfano. `CompensacionDeCobro` lo reembolsa después del rollback y el pago queda `REEMBOLSADO`; si el reembolso también falla, queda `COMPENSACION_PENDIENTE` con un `WARN` para el administrador.

**¿Qué pasa si el paciente cancela?** `PUT /api/turnos/{id}/cancelar`, solo sobre un turno propio (el de otro paciente da 403). Liberar un hold devuelve la franja a `DISPONIBLE`. Cancelar un turno confirmado es una sola transacción: el reclamo `PENDIENTE` pasa a `ANULADO` (o el ya `ENVIADO` a `EN_REVISION_MANUAL`, porque el legado no tiene anulación), la pasarela reembolsa (`Pasarela externa: reembolso de tx_…`) y el pago queda `REEMBOLSADO`; el turno queda `CANCELADO` y el paciente lo sigue viendo.

**¿Por qué tópico para `TurnoConfirmado` y cola para los reclamos?** Tópico: uno-a-muchos, cada suscriptor con su copia. Cola: un único consumidor y reintentos que no le llegan a quien no falló.

**¿Por qué el mensaje sale recién al commit?** El `JMSContext` inyectado se enlista en la transacción JTA, igual que el `EntityManager`: no puede haber un turno confirmado sin evento ni un evento sin turno confirmado.

**¿Por qué eventos CDI síncronos y no `@ObservesAsync`?** Un observador asincrónico corre en otro hilo y fuera de la transacción: su falla ya no podría impedir la confirmación. Con `@Observes` y `@Transactional(MANDATORY)`, el cobro y la sala deciden si el turno se confirma.

**¿Qué pasa si Artemis entrega dos veces el mismo mensaje?** Los consumidores son idempotentes por `turnoId` (notificación y reclamo) o por estado (`procesarReclamo` solo procesa reclamos `PENDIENTE`: ignora los `ENVIADO`, en revisión manual o `ANULADO`).

**¿Por qué 502 para la pasarela y 503 para el legado y el video?** 502 indica que un servidor intermedio recibió una respuesta inválida o nula del proveedor de pagos; los otros dos se modelaron como servicio no disponible con posibilidad de reintentar. Es una decisión de cada `ExceptionMapper`.

**¿Cuál es el componente stateful y cuáles stateless?** `ServicioDeTurnos` es `@Stateful` porque su proceso es una conversación (reservar → confirmar o cancelar); el contenedor crea una instancia por petición. El estado de esa conversación es durable a propósito, en la fila del turno (`EN_HOLD` e `inicioHold`), para sobrevivir a un reinicio y poder confirmarse desde otra petición o dispositivo. Su expiración vive en `ExpiradorDeHolds` (`@Singleton`), porque la especificación no permite timers en un stateful. El resto de las fachadas son `@Stateless`.

**¿Dónde está el WSDL y cómo se consume?** En `/mediconecta/legado/obrasocial?wsdl`. El Adapter `SistemaDeObraSocialSoap` usa `Service.create` sin descargarlo y fija timeouts de 2 s y 5 s (con el afiliado lento OS-5005, la reserva responde 503 a los 5 s).

**¿Cómo se aplican los roles?** Dos capas: `security-constraint` en `web.xml` por URL y `@RolesAllowed` en cada EJB; las reglas que dependen del dato (el hold es del caller) usan el `SessionContext`. Un rechazo de `web.xml` lo responde el propio contenedor con 403; uno del EJB lanza `EJBAccessException`, que `AccesoDenegadoMapper` traduce a 403 (por ejemplo, Postman 02 "PACIENTE_B confirma hold ajeno (403)"). Sin credenciales válidas, 401.

## 4. Plan de contingencia

- Si falla la SPA: hacer el mismo recorrido con Postman (carpetas 00 si no se corrió, 01, 02 completa, incluido el pedido que confirma el turno, 02b, 05, 06 y 07; el paso 9–10 está en la carpeta 05 y se muestra paso a paso en la sección 1b). Si el turno de la 02 queda sin confirmar, la 05 y la 07 responden 409 hasta que vence el hold: un paciente solo puede tener uno.
- Si falla el servidor: mostrar la salida guardada de `deploy/smoke-test.sh` y recorrer los diagramas de secuencia del PDF.
- Si un caso de falla no se reproduce: soltar el hold anterior (las fallas del cobro y de la sala lo dejan `EN_HOLD`, y un segundo hold del paciente da 409), volver a ejecutar el paso con una franja nueva y revisar con `/system-property=*:read-resource` que no haya quedado una propiedad de una falla anterior.
