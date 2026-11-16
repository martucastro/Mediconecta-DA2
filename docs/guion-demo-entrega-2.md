# MediConecta — Guion de la demo y defensa oral (Entrega Obligatoria N.º 2)

Duración objetivo: 20 a 25 minutos. La demo recorre un flujo de punta a punta que cruza componentes internos, mensajería y servicios web (login → disponibilidad → reserva con cobertura SOAP → confirmación con copago REST, sala de video REST y tópico → notificación y reclamo por cola), y después muestra las fallas a mitad del flujo. Todo lo que se muestra sale del código del repositorio, de `deploy/smoke-test.sh` y de la colección `postman/MediConecta-Demo.postman_collection.json`.

## 0. Preparación (antes de entrar, no cuenta en el tiempo)

1. PostgreSQL arriba con la base `mediconecta`. Si la base está vacía, definir antes del primer arranque `MEDICONECTA_ADMIN_PASSWORD`, `MEDICONECTA_PROFESIONAL_PASSWORD` y `MEDICONECTA_PACIENTE_PASSWORD` (si no, el sembrado genera contraseñas al azar y las escribe una sola vez en el log).
2. WildFly con el perfil completo, que es el que trae Artemis: `standalone.bat -c standalone-full.xml`. Ejecutado una vez `jboss-cli.sh --connect --file=deploy/mediconecta-setup.cli` (en Windows, conviene escribir los comandos de abajo, sin las comillas simples, dentro de una sesión interactiva de `jboss-cli.bat --connect`) (datasource, tópico, cola, DLQ y política de reintentos).
3. `mvn clean package` y copiar `target/mediconecta.war` a `standalone/deployments/`. Esperar `Deployed "mediconecta.war"`.
4. Ensayo rápido: `bash deploy/smoke-test.sh --rapido` debe terminar con 0 fallidas.
5. Pestañas abiertas: la SPA (`http://localhost:8080/mediconecta/`), el WSDL (`http://localhost:8080/mediconecta/legado/obrasocial?wsdl`), Postman con el entorno `MediConecta-Demo` (`token_medio_de_pago = tok_visa_demo`), la consola de WildFly a la vista (el log es parte de la evidencia) y una terminal con `jboss-cli --connect`.
6. Tener a mano el PDF del documento técnico (diagramas de secuencia de cada integración).
7. Opcional: reservar un turno extra al empezar, sin confirmarlo, para mostrar al final que el hold venció solo (5 minutos).

Tokens de la pasarela simulada (`PasarelaExternaResource`): cualquier token aprueba (`APPROVED`), salvo los que empiezan con `rechazar` (`REJECTED`) o con `caer` (responde 503 y MediConecta devuelve 502). En la SPA, la pantalla del hold muestra el selector **MEDIO DE PAGO** solo cuando el turno tiene copago, con tres opciones: "Tarjeta de prueba (se aprueba)" (`tok_visa_demo`, por defecto), "Tarjeta de prueba (se rechaza)" (`rechazar_demo`) y "Simular pasarela caída" (`caer_demo`). La confirmación siempre envía `{ "tokenMedioDePago": … }` en el cuerpo del `PUT`, y ante un 502 la SPA muestra el mensaje del backend.

## 1. Recorrido (camino feliz, unos 12 minutos)

| Min. | Paso | Herramienta | Qué se muestra | Resultado esperado |
|---|---|---|---|---|
| 0–2 | Arquitectura e integraciones | PDF, secciones 1 y 2 | Ocho componentes, tabla de integraciones y el criterio síncrono/asincrónico | — |
| 2–4 | Login y roles | SPA (login del paciente) y Postman, carpeta 01 | Login correcto; anónimo 401; paciente que lista usuarios 403; `GET /usuarios/{id}` propio 200 y ajeno 403 (SCRUM-102) | 200 / 401 / 403 |
| 4–5 | El profesional abre franjas | SPA, agenda del profesional (o Postman 02 "Abrir disponibilidad como PROFESIONAL") | Una franja `PRESENCIAL` y una `TELEMEDICINA`; un paciente que intenta abrir franjas recibe 403 | 201 |
| 5–7 | Reserva con cobertura (SOAP) | SPA: Disponibilidad → elegir profesional → reservar | La pantalla del hold muestra el copago (6000 de 20000, plan 70 %) y la cuenta regresiva. En el log: `Autorización AUT-OS-2002-CONSULTA registrada…`. Mostrar el WSDL en el navegador | 200, turno `EN_HOLD` |
| 7–9 | Confirmación con copago (REST) | SPA: en MEDIO DE PAGO dejar "Tarjeta de prueba (se aprueba)" y Confirmar | En el log: `Pasarela externa: solicitud de cobro por 6000.00 ARS`, luego `Notificacion simulada para paciente…` | 200, turno `CONFIRMADO` |
| 9–10 | Lo que pasó después del commit (tópico y cola) | Postman | `GET /api/notificaciones/mias` como paciente (la notificación); `GET /api/reclamos` como administrador (reclamo `ENVIADO` con número de presentación); `GET /api/pagos?turnoId={id}` como paciente (pago `APROBADO`) | 200 |
| 10–12 | Telemedicina (REST) | SPA o Postman 02b/06: reservar y confirmar la franja de telemedicina con `tok_visa_demo` | Copago 4500 (teleconsulta). En el log: `Proveedor de video externo: solicitud de sala para turno-{id}`. `GET /api/telemedicina/turno/{id}` como paciente (enlace de invitado) y como profesional (enlace de anfitrión) | 200, cada uno ve solo su enlace |

Frase para cerrar el recorrido: "La reserva y la confirmación son cada una una transacción JTA; lo que decide si el flujo sigue (cobertura, cobro, sala) es síncrono y está adentro; lo que reacciona a un turno ya confirmado (notificación y reclamo) es asincrónico y corre después del commit".

## 2. Fallas a mitad del flujo (unos 8 minutos)

Para cada caso: reservar una franja nueva (`POST /api/turnos` con `{"turnoId": X}` como paciente) y después confirmarla con el cuerpo indicado (`PUT /api/turnos/X/confirmar`, `Content-Type: application/json`). Los dos primeros casos también se muestran desde la SPA, eligiendo la opción correspondiente en MEDIO DE PAGO.

| Caso | Cómo provocarlo | Respuesta | Qué mostrar después |
|---|---|---|---|
| Pago rechazado | SPA: "Tarjeta de prueba (se rechaza)"; Postman: cuerpo `{"tokenMedioDePago": "rechazar_demo"}` | 409 | El turno sigue `EN_HOLD` (SPA o `GET /api/turnos/mios`). Confirmar de nuevo con la tarjeta que se aprueba: 200 (se reintenta dentro del hold) |
| Pasarela caída | SPA: "Simular pasarela caída"; Postman: cuerpo `{"tokenMedioDePago": "caer_demo"}` | 502, con el mensaje del backend en la SPA | El turno sigue `EN_HOLD`; nada se cobró |
| Copago sin medio de pago | Postman: cuerpo `{}` | 400 | El turno sigue `EN_HOLD` |
| Legado de la obra social caído | `jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:add(value="http://127.0.0.1:1/x")'` y reservar | 503 | `GET /api/turnos?profesionalId={id}`: la franja sigue `DISPONIBLE`. Volver atrás con `jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:remove'` |
| Reintentos de la cola | Reservar con el legado arriba; romper la URL del legado como en la fila anterior; confirmar con `tok_visa_demo` (200); mirar el log y `GET /api/reclamos` como administrador | — | El reclamo suma intentos (reentregas a los 2, 4, 8 y 16 s). Variante A: restaurar la URL antes del quinto intento y el reclamo pasa a `ENVIADO`. Variante B: dejarla rota y queda `EN_REVISION_MANUAL` con 5 intentos. `…/jms-queue=ReclamosFacturacionQueue:count-messages` y `…/jms-queue=ReclamosFacturacionDLQ:count-messages` quedan en 0 |
| Proveedor de video caído (opcional) | `jboss-cli.sh --connect '/system-property=mediconecta.video.simular-caida:add(value=true)'` y confirmar una franja de telemedicina con `tok_visa_demo` | 503 con `Retry-After: 30` | El turno sigue `EN_HOLD`. En el log se ve que la pasarela aprobó el cobro y `GET /api/pagos?turnoId=X` muestra un pago `PENDIENTE`: es el cobro huérfano (sección 6 del documento). Quitar la propiedad con `:remove` |
| Hold vencido (opcional) | El turno reservado al principio | — | Pasados 5 minutos volvió a `DISPONIBLE` sin que nadie lo tocara (`ExpiradorDeHolds`) |

La DLQ no se demuestra en vivo: con la lógica actual, el quinto intento marca el reclamo `EN_REVISION_MANUAL` y confirma el mensaje, así que a `ReclamosFacturacionDLQ` solo llegan mensajes cuyas cinco entregas fallan por un error no previsto (por ejemplo, la base caída). Se explica con el diagrama de la sección 3.3.

## 3. Respuestas breves para las preguntas esperadas

**¿Por qué cada integración es síncrona o asincrónica?**
- Cobertura con la obra social (SOAP, síncrono): el contrato SOAP lo impone el legado, y el copago que se cobra al confirmar depende de esa respuesta. Sin respuesta no se reserva (503).
- Pasarela de pago (REST, síncrono): sin cobro aprobado no se confirma; es un partner moderno con API REST.
- Proveedor de video (REST, síncrono): invariante "todo turno de telemedicina confirmado tiene su enlace".
- Tópico `TurnoConfirmado` (asincrónico): es un hecho consumado con varios interesados independientes; el publicador no los conoce ni los espera.
- Cola `ReclamosFacturacion` (asincrónico): el reclamo se procesa exactamente una vez, con reintentos y DLQ, y la caída de la obra social no afecta al paciente.
- Regla general: lo que decide si el flujo sigue es síncrono; lo que reacciona después del commit es asincrónico.

**¿Qué pasa si algo falla a mitad de la confirmación?** Toda la confirmación es una transacción JTA (`REQUIRED`). Los observadores corren antes de cambiar el estado, y las excepciones son `@ApplicationException(rollback = true)`: el turno sigue `EN_HOLD`, el temporizador sigue vivo y el mensaje al tópico no sale. Pasarela caída 502, rechazo 409, video caído 503. Lo que JTA no deshace es una llamada HTTP ya hecha: si el cobro se aprobó y después falla la sala, queda un cobro huérfano. Hoy queda un rastro `PENDIENTE` y se concilia a mano; la compensación automática es trabajo de la entrega final.

**¿Por qué tópico para `TurnoConfirmado` y cola para los reclamos?** Tópico: uno-a-muchos, cada suscriptor con su copia. Cola: un único consumidor y reintentos que no le llegan a quien no falló.

**¿Por qué el mensaje sale recién al commit?** El `JMSContext` inyectado se enlista en la transacción JTA, igual que el `EntityManager`: no puede haber un turno confirmado sin evento ni un evento sin turno confirmado.

**¿Por qué eventos CDI síncronos y no `@ObservesAsync`?** Un observador asincrónico corre en otro hilo y fuera de la transacción: su falla ya no podría impedir la confirmación. Con `@Observes` y `@Transactional(MANDATORY)`, el cobro y la sala deciden si el turno se confirma.

**¿Qué pasa si Artemis entrega dos veces el mismo mensaje?** Los consumidores son idempotentes por `turnoId` (notificación y reclamo) o por estado (`procesarReclamo` ignora reclamos ya `ENVIADO` o en revisión manual).

**¿Por qué 502 para la pasarela y 503 para el legado y el video?** 502 indica que un servidor intermedio recibió una respuesta inválida o nula del proveedor de pagos; los otros dos se modelaron como servicio no disponible con posibilidad de reintentar. Es una decisión de cada `ExceptionMapper`.

**¿Cuál es el componente stateful y cuáles stateless?** `ServicioDeTurnos` es `@Stateful` porque su proceso es una conversación (reservar → confirmar o cancelar); el contenedor crea una instancia por petición. El estado de esa conversación es durable a propósito, en la fila del turno (`EN_HOLD` e `inicioHold`), para sobrevivir a un reinicio y poder confirmarse desde otra petición o dispositivo. Su expiración vive en `ExpiradorDeHolds` (`@Singleton`), porque la especificación no permite timers en un stateful. El resto de las fachadas son `@Stateless`.

**¿Dónde está el WSDL y cómo se consume?** En `/mediconecta/legado/obrasocial?wsdl`. El Adapter `SistemaDeObraSocialSoap` usa `Service.create` sin descargarlo y fija timeouts de 2 s y 5 s.

**¿Cómo se aplican los roles?** Dos capas: `security-constraint` en `web.xml` por URL y `@RolesAllowed` en cada EJB; las reglas que dependen del dato (el hold es del caller) usan el `SessionContext`. Todo rechazo termina en 403 por `AccesoDenegadoMapper`.

## 4. Plan de contingencia

- Si falla la SPA: hacer el mismo recorrido con Postman (carpetas 01, 02, 02b, 05 y 06).
- Si falla el servidor: mostrar la salida guardada de `deploy/smoke-test.sh` y recorrer los diagramas de secuencia del PDF.
- Si un caso de falla no se reproduce: volver a ejecutar el paso con una franja nueva (cada falla deja el turno anterior `EN_HOLD`).
