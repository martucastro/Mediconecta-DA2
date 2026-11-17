# ADR 0001: El estado del hold vive en la fila del turno, no en la memoria del bean stateful

- **Estado:** Aceptada
- **Fecha:** 2026-09-13 (decisión); registrada como ADR el 2026-11-17

> **En una línea:** `ServicioDeTurnos` es `@Stateful`, pero la verdad del hold está en la base (`Turno.estado = EN_HOLD` e `inicioHold`) y la expiración la maneja un `@Singleton` aparte, `ExpiradorDeHolds`. El estado conversacional propio del bean casi no se usa, y lo decimos.

## Contexto

- Reservar retiene un turno 5 minutos a nombre del paciente. Confirmar o cancelar llega después, en otro pedido HTTP.
- La consigna pide un componente stateful justificado. Turnos es el único proceso que es una conversación (reservar → confirmar o cancelar), así que `ServicioDeTurnos` es `@Stateful`.
- La API no tiene sesión. La SPA se autentica con HTTP Basic en cada pedido (`AutenticacionBasica`). `TurnosResource` es `@RequestScoped` e inyecta el bean con `@Inject`, de modo que el contenedor crea una instancia de `ServicioDeTurnos` por pedido y la destruye al terminarlo (`@PostConstruct` y `@PreDestroy` lo registran en el log). La instancia que reserva no es la que confirma.
- Dos pacientes que quieren el mismo turno llegan en pedidos distintos y, por lo tanto, a instancias distintas.
- El hold tiene que vencer solo. La especificación no permite timers en un stateful: el javadoc de `jakarta.ejb.TimerService` habilita solo a beans stateless, singleton, message-driven y entity 2.x. Una primera versión con el `TimerService` dentro de `ServicioDeTurnos` compilaba, pero devolvía `500` en la primera reserva.
- Un reinicio de WildFly no puede dejar turnos retenidos para siempre.

## Decisión

La fuente de verdad del hold es la entidad `Turno`, no el bean.

1. **Reservar.** `ServicioDeTurnos.reservarTurno` toma el turno con lock pesimista (`TurnoDAO.buscarParaActualizar`, `PESSIMISTIC_WRITE`), dispara `TurnoEnReserva` (ADR 0003), marca `EN_HOLD` con `inicioHold = ahora` y le pide a `ExpiradorDeHolds.programar` el temporizador.
2. **Confirmar y cancelar.** `confirmarTurno` y `cancelarTurno` releen el turno por id, con lock, y no usan nada de la instancia. `confirmarTurno` además rechaza con `409` un hold cuyo `inicioHold + DURACION_HOLD_MS` ya pasó, aunque la fila siga `EN_HOLD`.
3. **Expirar.** La expiración vive en `ExpiradorDeHolds` (`@Singleton`, `@Lock(LockType.READ)`), con dos mecanismos que se cubren entre sí:
   - un temporizador no persistente por turno (`new TimerConfig(turnoId, false)`), que libera con precisión a los 5 minutos;
   - un barrido `@Schedule` cada minuto (`barrerHoldsVencidos`, que usa `TurnoDAO.listarHoldsVencidos`), que sobrevive a un reinicio porque lee la base.

   Los dos liberan con el mismo método privado, `liberar`: relee el turno bajo lock y solo lo toca si sigue `EN_HOLD` y su hold no empezó después del límite. `cancelar(turnoId)` cancela únicamente el temporizador de ese turno, filtrando `getTimers()` por el `info` con que se creó.
4. **El bean sigue `@Stateful`.** Aporta el ciclo de vida gestionado por el contenedor y agrupa las operaciones de la conversación. Su único campo conversacional, `turnoEnCursoId`, se escribe en `reservarTurno` y solo lo expone `getTurnoEnCursoId()`, que ningún otro código invoca. En la práctica, nada depende de ese estado.

## Alternativas consideradas

### A. Guardar el hold en la memoria del bean stateful

Por qué se descartó:

- **No lo ve nadie más.** Un hold en la memoria de una instancia es invisible para los pedidos de los otros pacientes, que corren en otras instancias. La fila es el único lugar donde esos pedidos se encuentran, y el lock pesimista los serializa.
- **No llega a la confirmación.** Con una instancia por pedido, la que confirma no es la que reservó, así que no encontraría el estado.
- **No sobrevive a un reinicio.** Un reinicio o un redespliegue perdería en silencio todos los holds activos.
- **Habría que inventar una sesión.** Para que sirviera, habría que conservar la referencia al bean entre pedidos (sesión HTTP o un bean `@SessionScoped`), que la API no tiene. Eso ataría al paciente a una instancia y a un navegador: no podría confirmar desde otra pestaña o dispositivo, y escalar a más de una instancia exigiría replicar la sesión.

### B. El `TimerService` dentro de `ServicioDeTurnos`

Fue la primera versión. Se descartó porque la especificación no lo permite y fallaba en ejecución (`500` en la primera reserva). Además, la cancelación de entonces recorría `getTimers()` sin filtrar: cancelaba los temporizadores de todos los pacientes, cuyos holds quedaban retenidos para siempre.

### C. Solo el barrido periódico, sin temporizador por turno

Se descartó porque un barrido por minuto libera con hasta 60 s de demora, y porque el temporizador por turno es el que muestra el servicio de timers gestionado por el contenedor. Se conservan los dos a propósito: el temporizador da precisión y el barrido da durabilidad.

### D. Declarar `ServicioDeTurnos` como `@Stateless`

Con el estado en la base, un `@Stateless` alcanzaría técnicamente. Se descartó por la consigna, que pide un componente stateful, y porque turnos es el único proceso conversacional. Es una razón de la consigna, no técnica.

## Consecuencias

### Positivas

- El hold sobrevive a un reinicio. La fila queda `EN_HOLD`, el barrido la libera dentro del minuto siguiente al vencimiento, y `confirmarTurno` rechaza un hold vencido aunque el barrido todavía no haya pasado.
- El paciente puede confirmar desde otro pedido, pestaña o dispositivo.
- La concurrencia se resuelve en un solo lugar: el lock de la fila. `buscarParaActualizar` relee el turno al tomarlo, así que el barrido no pisa una confirmación recién hecha.
- La lógica se prueba sin contenedor: `ExpiradorDeHoldsTest` usa un reloj fijo (`Clock`) y `ServicioDeTurnosTest` cubre reserva, confirmación y cancelación.

### Negativas

- El `@Stateful` se justifica más por la consigna y por agrupar la conversación que por necesidad técnica. `turnoEnCursoId` no lo lee nadie: quien espere ver estado conversacional real no lo encuentra.
- Se crea y se destruye una instancia por pedido, cuando un bean `@Stateless` del pool alcanzaría.
- Hay dos mecanismos de expiración que mantener. El temporizador no es persistente y se pierde al reiniciar: lo cubre el barrido, con hasta un minuto de demora.
- La transacción de la reserva mantiene la fila bloqueada mientras espera a la obra social, hasta 7 s (ADR 0004).

### Qué cambiaríamos

1. **Sacar el estado que no se usa.** Quitaríamos `turnoEnCursoId` y `getTurnoEnCursoId()`. Sin la exigencia de la consigna, declararíamos `ServicioDeTurnos` como `@Stateless`.
2. **Atar cada temporizador a su hold.** El temporizador vive en la JVM que lo creó. Con más de una instancia, cancelar un hold en una instancia no cancela el temporizador de la otra. `liberar` revalida el estado bajo lock, así que un turno `CONFIRMADO` no se libera. Pero si el turno se vuelve a reservar, un temporizador viejo puede liberar antes de tiempo el hold nuevo, porque el margen de un minuto de `expirar` solo protege los holds que empezaron hace menos de un minuto. Guardar en el `info` del temporizador el `inicioHold` que le corresponde, y compararlo al vencer, cerraría ese hueco.

## Referencias en el código

- [`turnos/negocio/ServicioDeTurnos.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ServicioDeTurnos.java)
- [`turnos/negocio/ExpiradorDeHolds.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ExpiradorDeHolds.java)
- [`turnos/datos/Turno.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/datos/Turno.java)
- [`turnos/datos/TurnoDAO.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/datos/TurnoDAO.java)
- [`turnos/datos/EstadoTurno.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/datos/EstadoTurno.java)
- [`turnos/presentacion/TurnosResource.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/turnos/presentacion/TurnosResource.java)
- [`usuarios/presentacion/AutenticacionBasica.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/usuarios/presentacion/AutenticacionBasica.java)
- [`ExpiradorDeHoldsTest.java`](../../src/test/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ExpiradorDeHoldsTest.java) y [`ServicioDeTurnosTest.java`](../../src/test/java/ar/edu/uade/da2/mediconecta/turnos/negocio/ServicioDeTurnosTest.java)
- Documento técnico, secciones [6.1 y 9](../documento-tecnico.md)
