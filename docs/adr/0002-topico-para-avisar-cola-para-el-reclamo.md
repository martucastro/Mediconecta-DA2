# ADR 0002: Tópico `TurnoConfirmado` para avisar, cola `ReclamosFacturacion` para el reclamo

- **Estado:** Aceptada
- **Fecha:** 2026-10-01 (decisión); registrada como ADR el 2026-11-17

> **En una línea:** lo que reacciona a un turno ya confirmado es asincrónico. Un tópico reparte el hecho a cada interesado (`NotificacionMDB`, `TurnoConfirmadoFacturacionMDB`), y una cola convierte el reclamo a la obra social en un trabajo que procesa un único consumidor (`ReclamoMDB`), con reintentos, cola de mensajes muertos y `EN_REVISION_MANUAL`.

## Contexto

- Cuando un turno se confirma, hay dos reacciones independientes. Notificaciones avisa al paciente. Facturación presenta el reclamo a la obra social si el turno tenía cobertura autorizada.
- Ninguna de las dos decide si el turno se confirma: el paciente ya tiene su turno. No deberían demorar ni revertir esa confirmación.
- El reclamo tiene efecto en un tercero (SOAP `presentarReclamo`, ADR 0004), que puede fallar de forma transitoria. Se tiene que hacer exactamente una vez, con reintentos, y si se agotan tiene que quedar a la vista de un administrador.
- En el mismo sistema, otras integraciones son síncronas. El criterio sale de cuatro preguntas (documento técnico, sección 4.1):

| Pregunta | Síncrono | Asincrónico |
|---|---|---|
| ¿Quién necesita la respuesta, y cuándo? | Quien llama decide con ella si sigue: la cobertura define el copago de la reserva, y sin cobro aprobado no hay confirmación. | Quien llama ya terminó y solo avisa un hecho. |
| ¿Cuánto acoplamiento temporal se tolera? | Se acepta esperar al tercero, con timeouts (ADR 0004). | La caída del tercero no puede afectar al paciente. |
| ¿Uno o varios interesados? | Una respuesta para quien preguntó. | Tópico si son varios; cola si es un trabajo para uno solo. |
| ¿Quién impone el contrato? | El legado impone SOAP; la pasarela y el video ya exponen REST. | El canal lo elige el equipo: JMS en Artemis, dentro de WildFly. |

Por eso la cobertura (SOAP) y el cobro del copago (REST) son síncronos y corren dentro de la transacción del paciente (ADR 0003). La notificación y el reclamo, en cambio, son asincrónicos.

## Decisión

1. **Publicar el hecho.** `ServicioDeTurnos.confirmarTurno` publica un `MapMessage` (`turnoId`, `pacienteId`, `profesionalId` y `fechaHora` en ISO-8601) en `java:/jms/topic/TurnoConfirmado`. Usa el `JMSContext` inyectado, que se enlista en la transacción JTA: el mensaje sale recién en el commit, y si hay rollback no sale nunca. `ServicioDeTurnos` no conoce a ningún suscriptor.
2. **Dos suscriptores del tópico,** cada uno con su copia y su propia transacción:
   - `NotificacionMDB` valida el mensaje y llama a `ServicioDeNotificaciones.notificarTurnoConfirmado`, que es idempotente por turno (`NotificacionDAO.existePorTurno`).
   - `TurnoConfirmadoFacturacionMDB` (`@RunAs(ADMINISTRADOR)` y `@PermitAll`, porque un MDB no tiene usuario autenticado) llama a `ServicioDeFacturacion.registrarReclamo`. Ese método crea un `Reclamo` `PENDIENTE` solo si el turno sigue `CONFIRMADO`, tiene cobertura autorizada y todavía no tiene reclamo, y en la misma transacción encola el `reclamoId` en `java:/jms/queue/ReclamosFacturacion`.
3. **La cola tiene un único consumidor.** `ReclamoMDB` lee `reclamoId` y `JMSXDeliveryCount` y llama a `ServicioDeFacturacion.procesarReclamo(reclamoId, intento)`, que solo procesa reclamos `PENDIENTE`:

| Resultado de presentar el reclamo | Qué hace `procesarReclamo` | Estado del reclamo |
|---|---|---|
| Presentado | Guarda número de presentación y monto | `ENVIADO` |
| `ReclamoRechazadoException` (rechazo definitivo) | Registra el error y no relanza | `EN_REVISION_MANUAL` |
| `CanalDeReclamosNoDisponibleException`, intento menor a `MAX_INTENTOS` (5) | Registra el intento con `ReclamoDAO.registrarIntentoFallido` (`REQUIRES_NEW`, sobrevive al rollback) y relanza: Artemis reentrega | `PENDIENTE` |
| La misma falla en el intento 5 | Registra el intento y no relanza: el mensaje se confirma | `EN_REVISION_MANUAL` |

4. **Políticas en `deploy/mediconecta-setup.cli`.** La cola usa `max-delivery-attempts=5`, una demora de 2 s con multiplicador 2 y un máximo de 30 s, y su propia cola de mensajes muertos, `ReclamosFacturacionDLQ`. La DLQ solo recibe lo que falla cinco veces por un error no previsto, como una base caída: es la red de seguridad del broker, no el camino de negocio. El tópico tiene su propia política: 10 intentos, demora de 1 s y la DLQ general.

## Alternativas consideradas

### A. Llamadas síncronas desde `confirmarTurno`

Notificar y presentar el reclamo dentro de la confirmación. Se descartó por tres motivos:

- Ataba la confirmación a la disponibilidad del legado: el paciente no podría confirmar por una falla que no le concierne.
- Alargaba la transacción del paciente con la fila del turno bloqueada.
- Los reintentos con espera creciente ocuparían el hilo del pedido.

### B. Una sola cola para los dos interesados

Se descartó porque una cola entrega cada mensaje a un solo consumidor: notificaciones y facturación competirían por el mismo mensaje, y cada turno llegaría a uno solo de los dos.

### C. Procesar el reclamo directamente en el suscriptor del tópico, sin cola propia

Se descartó por tres motivos:

- La política de reentrega de Artemis se configura por dirección. La del tópico rige para todas sus suscripciones, así que el reclamo no podría tener sus propios reintentos ni su DLQ dedicada.
- Reintentar volviendo a publicar en el tópico le llegaría de nuevo a todos los suscriptores, no solo al que falló.
- Con la cola, el reclamo queda registrado (`PENDIENTE`) y encolado en una misma transacción. A partir de ahí es un trabajo con identidad propia (`reclamoId`), separado del aviso.

### D. Facturación como observador CDI de `TurnoEnConfirmacion`

Estaba previsto en `PuntosDeExtension.RECLAMO` (300). Se descartó por dos motivos:

- El observador correría dentro de la transacción del paciente: una falla de facturación revertiría la confirmación, y su trabajo la demoraría.
- La tarjeta habría tenido que tocar el flujo de confirmación en paralelo con SCRUM-91, 93 y 95.

Con el tópico, el reclamo solo existe para una confirmación que ya hizo commit. La constante quedó sin uso, a propósito.

## Consecuencias

### Positivas

- La confirmación no espera a notificaciones, a facturación ni al legado, y no depende de ellos. Si un suscriptor falla después del commit, el turno sigue `CONFIRMADO` y Artemis reentrega.
- Sumar un tercer interesado es sumar un MDB suscripto al tópico, sin tocar turnos.
- El reclamo nunca se pierde en silencio: termina `ENVIADO` o en `EN_REVISION_MANUAL`, a la vista del administrador en `GET /api/reclamos`. En la verificación en ejecución se observaron reintentos a +2, +4, +8 y +16 s, y el reclamo terminó `EN_REVISION_MANUAL` tras 5 intentos (documento técnico de la Entrega N.º 2, sección 3.3).
- Los mensajes solo llevan ids: cada consumidor lee el estado de la base y no se duplican datos.

### Negativas

- **Consistencia eventual.** La notificación y el reclamo aparecen después de la confirmación. Eso abre carreras con la cancelación, que se resuelven con verificaciones de estado: `registrarReclamo` exige `CONFIRMADO`, `procesarReclamo` solo procesa `PENDIENTE` y `ReclamoAlCancelar` anula o deriva el reclamo.
- **Entrega al menos una vez.** Cada consumidor tiene que ser idempotente. Hoy lo son por turno o por el estado del reclamo, pero cada consumidor nuevo tiene que acordarse.
- **Suscripciones no durables.** Ningún MDB del tópico declara `subscriptionDurability`. Un mensaje publicado mientras un suscriptor no está conectado, por ejemplo durante un redespliegue, se pierde para ese suscriptor sin dejar rastro. Declarar suscripciones durables lo resolvería.
- **Configuración duplicada.** `ServicioDeFacturacion.MAX_INTENTOS` y `max-delivery-attempts` del script tienen que coincidir a mano.
- **Identidad prestada.** `TurnoConfirmadoFacturacionMDB` corre con rol de administrador (`@RunAs`) para poder leer el turno.
- **Más difícil de probar.** Hay más infraestructura que configurar y verificar de punta a punta. Las pruebas unitarias de los MDB (`NotificacionMDBTest`, `TurnoConfirmadoFacturacionMDBTest`, `ReclamoMDBTest`) no reemplazan la verificación contra Artemis.

## Referencias en el código

- [`turnos/negocio/ServicioDeTurnos.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ServicioDeTurnos.java) (`publicarTurnoConfirmado`)
- [`notificaciones/presentacion/NotificacionMDB.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/notificaciones/presentacion/NotificacionMDB.java)
- [`notificaciones/negocio/ServicioDeNotificaciones.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/notificaciones/negocio/ServicioDeNotificaciones.java)
- [`facturacion/presentacion/TurnoConfirmadoFacturacionMDB.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/presentacion/TurnoConfirmadoFacturacionMDB.java)
- [`facturacion/presentacion/ReclamoMDB.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/presentacion/ReclamoMDB.java)
- [`facturacion/negocio/ServicioDeFacturacion.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/negocio/ServicioDeFacturacion.java)
- [`facturacion/datos/ReclamoDAO.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/datos/ReclamoDAO.java) y [`facturacion/datos/EstadoReclamo.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/datos/EstadoReclamo.java)
- [`deploy/mediconecta-setup.cli`](../../deploy/mediconecta-setup.cli), secciones 4 y 5
- Documento técnico, [secciones 4.1, 8.6, 9.1 y 9.3](../documento-tecnico.md); [documento técnico de la Entrega N.º 2](../documento-tecnico-entrega-2.md), secciones 2 y 3
