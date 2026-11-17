# ADR 0003: Puntos de extensión del turno con eventos CDI sincrónicos, no con JMS

- **Estado:** Aceptada
- **Fecha:** 2026-09-28 (decisión, SCRUM-94); registrada como ADR el 2026-11-17

> **En una línea:** los pasos de otros componentes que deciden si una reserva, una confirmación o una cancelación ocurre se enganchan como observadores `@Observes` de eventos CDI. Los observadores son `@Transactional(MANDATORY)` y corren dentro de la transacción JTA del turno, así que su falla revierte todo. El precio es que el rollback no alcanza a los terceros por HTTP: de ahí el cobro huérfano.

## Contexto

- Varios componentes tienen que intervenir en un paso del turno y poder vetarlo:

| Paso del turno | Quién interviene | Si falla |
|---|---|---|
| Reservar | Obras sociales: cobertura y copago (SCRUM-91) | No se reserva |
| Confirmar | Pagos: cobro del copago (SCRUM-93) | No se confirma un turno impago |
| Confirmar | Telemedicina: sala de video (SCRUM-95) | No se confirma un turno de telemedicina sin enlace |
| Cancelar un turno confirmado | Facturación: anular el reclamo, y Pagos: reembolsar el copago (SCRUM-124) | El turno sigue `CONFIRMADO` |

- El paciente necesita el resultado en el mismo pedido: qué copago paga, si el cobro fue rechazado (`409`) o si un tercero no respondió (`502` o `503`), para reintentar mientras dure el hold.
- Las tarjetas se desarrollaban en paralelo y todas tocaban el mismo método, `confirmarTurno`.
- Ya existía un canal asincrónico, el tópico `TurnoConfirmado` (ADR 0002), para lo que reacciona después del commit.

## Decisión

1. **`ServicioDeTurnos` dispara eventos y no conoce a nadie.** Inyecta `Event<TurnoEnReserva>`, `Event<TurnoEnConfirmacion>` y `Event<TurnoEnCancelacion>`, y llama a `fire()` antes de cambiar el estado del turno. No importa ninguna clase de obras sociales, pagos, telemedicina ni facturación.
2. **Cada observador vive en su componente.** Es un bean `@ApplicationScoped` con un método `@Observes @Priority(PuntosDeExtension.X)` y `@Transactional(TxType.MANDATORY)`:

| Evento | Observador | Prioridad (`PuntosDeExtension`) |
|---|---|---|
| `TurnoEnReserva` | `CoberturaEnLaReserva` (obras sociales) | `COBERTURA` = 100 |
| `TurnoEnConfirmacion` | `CobroAlConfirmar` (pagos) | `COBRO_COPAGO` = 100 |
| `TurnoEnConfirmacion` | `SalaAlConfirmar` (telemedicina) | `SALA_DE_VIDEO` = 200 |
| `TurnoEnCancelacion` | `ReclamoAlCancelar` (facturación) | `ANULACION_RECLAMO` = 100 |
| `TurnoEnCancelacion` | `ReembolsoAlCancelar` (pagos) | `REEMBOLSO_COPAGO` = 200, último porque el rollback no lo deshace |

3. **Tres reglas para todo observador**, escritas en el javadoc de `PuntosDeExtension` y en el documento técnico (sección 9.2):
   - **Sincrónico, siempre.** `@Observes`, nunca `@ObservesAsync`.
   - **`MANDATORY`.** El observador corre en la transacción del turno. Si alguien dispara el evento fuera de una transacción, el contenedor lo rechaza en vez de ejecutarlo suelto.
   - **Errores con rollback.** Para cortar el flujo, el observador lanza una excepción `@ApplicationException(rollback = true)`: `DatosInvalidosException`, `ConflictoDeNegocioException`, `ObraSocialNoDisponibleException`, `PasarelaNoDisponibleException` o `ProveedorDeVideoNoDisponibleException`. La excepción atraviesa `fire()` sin envolverse en `EJBException` y llega a los mappers del paquete raíz, que responden `400`, `409`, `503`, `502` y `503`, respectivamente.

## Alternativas consideradas

### A. JMS (cola o tópico) para estos pasos

Se descartó por tres motivos:

- **Llega tarde para vetar.** Un mensaje enlistado en la transacción sale recién en el commit y se procesa en otra transacción. Para entonces el turno ya está confirmado, y el consumidor ya no puede impedirlo.
- **Obliga a una saga.** Para no dejar un turno confirmado e impago, habría que agregar un estado intermedio, un consumidor que lo resuelva y una compensación por cada falla. Eso es mucho más código que el que se quería evitar.
- **El paciente no tendría respuesta.** No sabría en el mismo pedido si su cobro fue aprobado, ni podría reintentar mientras dura el hold.

JMS sí se usa para lo que reacciona a un hecho ya confirmado (ADR 0002).

### B. Eventos CDI asincrónicos (`@ObservesAsync`)

Se descartó por el mismo motivo de fondo que JMS: el observador corre en otro hilo y fuera de la transacción, así que su falla ya no puede impedir la confirmación.

### C. Llamadas directas desde `ServicioDeTurnos` a cada fachada

La semántica transaccional es la misma: misma transacción y mismo rollback. Se descartó por acoplamiento:

- `ServicioDeTurnos` tendría que importar cuatro componentes.
- Cada tarjeta habría editado el mismo método, con conflictos de merge entre ramas.
- Invertía la dirección de dependencias que ya fijaba `TurnoConfirmado`: turnos anuncia, los demás reaccionan.

## Consecuencias

### Positivas

- **Atomicidad sobre los recursos transaccionales.** Al confirmar, el cambio de estado del turno, la `SesionVideo` y el mensaje `TurnoConfirmado` se confirman o se revierten juntos. Al cancelar, pasa lo mismo con el estado del turno y el del reclamo.
- **El paciente puede reintentar.** Como el evento se dispara antes de tocar el estado, si un observador de la confirmación falla, el turno sigue `EN_HOLD` con su temporizador vivo.
- **Cada componente se engancha sin tocar turnos.** Cada tarjeta agregó su observador en su propio componente, y cada uno se prueba solo (`CobroAlConfirmarTest`, `SalaAlConfirmarTest`).
- **Los errores llegan intactos.** Llegan a presentación con su propio código HTTP, sin traducciones intermedias.

### Negativas

- **El rollback JTA no alcanza a las llamadas HTTP ya completadas.** Alcanza a PostgreSQL y al `JMSContext` enlistado, pero no a la pasarela de pago ni al proveedor de video:
  - **Cobro huérfano.** `CobroAlConfirmar` (100) cobra antes que `SalaAlConfirmar` (200). Si después falla la sala o el commit, la base se revierte, pero el cobro en la pasarela ya ocurrió.
  - **Reembolso hecho.** Lo mismo pasa al revés al cancelar: si el commit falla después del reembolso, el dinero ya se devolvió.
  - **Sala huérfana.** Si un paso posterior revierte la transacción, la sala ya creada en el proveedor queda sin registro local.

  Para el cobro huérfano, la entrega final incorpora un mecanismo de compensación que actúa cuando la transacción de confirmación se revierte después de cobrar. Se documenta junto con ese cambio. Este ADR deja asentado por qué hace falta: el evento sincrónico da atomicidad sobre la base y JMS, no sobre los terceros.
- **Transacciones más largas.** La transacción del paciente espera a cada tercero con la fila del turno bloqueada: hasta 7 s a la obra social (ADR 0004) y hasta 3 s de conexión más 5 s de lectura al proveedor de video.
- **Orden y resultados implícitos.** El orden no se lee en el método que dispara: está en `PuntosDeExtension`. Los resultados tampoco son valores de retorno: el observador escribe sobre el `Turno` que viaja en el evento (cobertura, copago).
- **Reglas sin control del compilador.** Nada impide que un observador nuevo use `@ObservesAsync` o se olvide del `MANDATORY`. Las reglas viven en javadoc y en la documentación.

## Referencias en el código

- [`turnos/negocio/ServicioDeTurnos.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ServicioDeTurnos.java)
- [`turnos/negocio/PuntosDeExtension.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/PuntosDeExtension.java)
- [`turnos/negocio/TurnoEnReserva.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/TurnoEnReserva.java), [`TurnoEnConfirmacion.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/TurnoEnConfirmacion.java) y [`TurnoEnCancelacion.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/TurnoEnCancelacion.java)
- [`obrassociales/negocio/CoberturaEnLaReserva.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/CoberturaEnLaReserva.java)
- [`pagos/negocio/CobroAlConfirmar.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/pagos/negocio/CobroAlConfirmar.java) y [`pagos/negocio/ReembolsoAlCancelar.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/pagos/negocio/ReembolsoAlCancelar.java)
- [`telemedicina/negocio/SalaAlConfirmar.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/telemedicina/negocio/SalaAlConfirmar.java)
- [`facturacion/negocio/ReclamoAlCancelar.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/negocio/ReclamoAlCancelar.java)
- [`comun/negocio/DatosInvalidosException.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/comun/negocio/DatosInvalidosException.java) y [`comun/negocio/ConflictoDeNegocioException.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/comun/negocio/ConflictoDeNegocioException.java)
- Documento técnico, [secciones 8.5, 9.2 y 9.4](../documento-tecnico.md); [documento técnico de la Entrega N.º 2](../documento-tecnico-entrega-2.md), sección 6; [arquitectura](../arquitectura.md), cobro del copago al confirmar
