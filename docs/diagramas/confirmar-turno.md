# Confirmar turno: sala de video REST y publicación de `TurnoConfirmado`

Diagrama de secuencia de `PUT /api/turnos/{id}/confirmar` tal como está en `main` (SCRUM-112). Cada participante es una clase real del código; los nombres de métodos coinciden con los del repositorio.

**Cómo leerlo.** El bloque verde es una única transacción JTA, la de `ServicioDeTurnos.confirmarTurno` (`REQUIRED`): todo lo que pasa adentro se confirma junto o se revierte junto. El bloque azul ocurre después del commit, en otros hilos y en otras transacciones: el paciente ya recibió su respuesta. Línea continua: llamada sincrónica. Línea punteada: respuesta o mensaje JMS.

```mermaid
sequenceDiagram
    autonumber
    actor P as Paciente (SPA)
    participant R as TurnosResource
    participant T as ServicioDeTurnos<br/>(@Stateful)
    participant DB as PostgreSQL
    participant S as SalaAlConfirmar<br/>(@Priority SALA_DE_VIDEO = 200)
    participant TM as ServicioDeTelemedicina<br/>(@Stateless)
    participant RC as ProveedorDeVideoRestClient<br/>(Adapter REST)
    participant V as Proveedor de video externo<br/>(externos.video, simulado)
    participant E as ExpiradorDeHolds<br/>(@Singleton)
    participant TOP as Tópico JMS<br/>TurnoConfirmado
    participant N as NotificacionMDB
    participant F as TurnoConfirmadoFacturacionMDB
    participant Q as Cola JMS<br/>ReclamosFacturacion

    P->>R: PUT /api/turnos/{id}/confirmar (HTTP Basic, web.xml autentica)
    R->>T: confirmarTurno(id) (@RolesAllowed PACIENTE)

    rect rgba(46, 160, 67, 0.12)
        Note over T,TOP: Transacción JTA única de confirmarTurno (REQUIRED): empieza al entrar al EJB
        T->>DB: turnoDAO.buscarParaActualizar(id) (PESSIMISTIC_WRITE)
        DB-->>T: Turno EN_HOLD
        Note over T: turno inexistente: 400. No está EN_HOLD: 409.<br/>Hold de otro paciente (verificarQueElHoldEsDelCaller): 403.

        T->>T: eventoConfirmacion.fire(TurnoEnConfirmacion)
        Note over T,S: Evento CDI sincrónico: los observadores corren en este hilo<br/>y en esta transacción, por orden de @Priority.<br/>Hoy en main hay uno solo: SalaAlConfirmar (200).<br/>COBRO_COPAGO (100) no tiene observador todavía (SCRUM-93, PR #33).<br/>RECLAMO (300) queda sin usar: el reclamo sale por el tópico.

        T->>S: alConfirmar(evento) (@Transactional MANDATORY)
        alt modalidad PRESENCIAL
            S-->>T: return, sin llamar al proveedor
        else modalidad TELEMEDICINA
            S->>TM: crearSesion(turnoId) (REQUIRED, se une a la TX)
            TM->>T: obtenerTurnoParaActualizar(turnoId) (MANDATORY)
            T-->>TM: Turno (otra instancia del stateful, pero el lock ya es de esta TX)
            TM->>DB: sesionDAO.buscarPorTurno(turnoId)
            alt ya hay sala para este paciente (idempotente)
                DB-->>TM: SesionVideo existente
            else no hay sala, o era de un paciente anterior
                TM->>RC: crearSala(turnoId, fechaHora)
                RC->>V: POST /api/externo/salas {reference: "turno-{id}"}<br/>(timeouts: 3 s conexión, 5 s lectura)
                break proveedor caído, lento o con sala incompleta
                    V--xRC: 503, timeout o respuesta sin roomId / enlaces
                    RC--xTM: ProveedorDeVideoNoDisponibleException<br/>(@ApplicationException rollback = true)
                    TM--xS: se propaga, no se atrapa
                    S--xT: sale por el fire()
                    Note over T,DB: ROLLBACK de toda la TX: el turno sigue EN_HOLD,<br/>el temporizador del hold sigue vivo y el mensaje no sale
                    T--xR: excepción
                    R-->>P: 503 + Retry-After: 30 (ProveedorDeVideoNoDisponibleMapper)
                end
                V-->>RC: 200 {roomId, hostUrl, guestUrl}
                RC-->>TM: SalaDeVideo
                TM->>DB: sesionDAO.guardar / actualizar(SesionVideo)
            end
            TM-->>S: SesionCreada
            S-->>T: return
        end

        T->>T: turno.setEstado(CONFIRMADO)
        T->>E: cancelar(turnoId) (cancela el timer del hold)
        T->>DB: turnoDAO.actualizar(turno)
        T->>TOP: publicarTurnoConfirmado: MapMessage<br/>{turnoId, pacienteId, profesionalId, fechaHora}
        Note over T,TOP: El JMSContext está enlistado en la TX:<br/>el mensaje queda retenido hasta el commit
    end

    Note over T,TOP: COMMIT al salir del EJB: se graban turno y sala,<br/>se libera el lock y recién ahí Artemis entrega el mensaje
    T-->>R: Turno CONFIRMADO
    R-->>P: 200 TurnoDTO

    rect rgba(56, 132, 255, 0.12)
        Note over TOP,Q: Después del commit, asincrónico: Publish/Subscribe, cada MDB en su propia TX
        par Suscriptor 1: notificaciones
            TOP--)N: onMessage(MapMessage)
            N->>N: ServicioDeNotificaciones.notificarTurnoConfirmado(...)<br/>idempotente por turnoId, guarda Notificacion y simula el envío
        and Suscriptor 2: facturación
            TOP--)F: onMessage(MapMessage) (@RunAs ADMINISTRADOR)
            F->>F: ServicioDeFacturacion.registrarReclamo(turnoId)<br/>solo con cobertura autorizada, idempotente por turnoId
            opt cobertura autorizada y sin reclamo previo
                F--)Q: MapMessage {reclamoId}
                Note over Q: ReclamoMDB lo presenta a la obra social por SOAP.<br/>Hasta 5 entregas (espera de 2 s a 30 s). Agotadas: EN_REVISION_MANUAL.<br/>DLQ propia: ReclamosFacturacionDLQ.
            end
        end
        Note over N,F: Si un MDB falla, Artemis reentrega el mensaje.<br/>La confirmación del turno no se ve afectada.
    end
```

## Qué pasa dentro de la transacción y qué después del commit

| Dentro de la transacción JTA de `confirmarTurno` | Después del commit (asincrónico) |
| --- | --- |
| Lock del turno con `buscarParaActualizar` y validaciones (estado `EN_HOLD`, que el hold sea del caller) | `NotificacionMDB` → `ServicioDeNotificaciones.notificarTurnoConfirmado` |
| Evento CDI `TurnoEnConfirmacion` → `SalaAlConfirmar` → `ServicioDeTelemedicina.crearSesion` → `ProveedorDeVideoRestClient` → proveedor externo | `TurnoConfirmadoFacturacionMDB` → `ServicioDeFacturacion.registrarReclamo` → cola `ReclamosFacturacion` |
| Alta o actualización de `SesionVideo` | `ReclamoMDB` presenta el reclamo por SOAP, con reintentos y DLQ |
| Turno pasa a `CONFIRMADO`, `ExpiradorDeHolds.cancelar` y `turnoDAO.actualizar` | |
| Envío del mensaje al tópico `TurnoConfirmado` (sale recién al commit) | |

## Casos a tener presentes

- **Solo hay un observador de `TurnoEnConfirmacion` en `main`.** Es `SalaAlConfirmar` (200). La cobertura (`CoberturaEnLaReserva`, `COBERTURA` = 100) observa otro evento, `TurnoEnReserva`, y corre al reservar, no al confirmar. El copago (`COBRO_COPAGO` = 100) se suma con la PR #33 (SCRUM-93), que todavía está abierta.
- **Carrera con el vencimiento del hold.** Si el timer de `ExpiradorDeHolds` vence mientras se confirma, `liberar` corre en su propia transacción (`REQUIRES_NEW`) y pide el mismo `PESSIMISTIC_WRITE` sobre el turno. Queda esperando el commit de la confirmación, después lee `CONFIRMADO` y no hace nada. El lock serializa los dos caminos.
- **Orden entre suscriptores.** Notificaciones y facturación no tienen orden entre sí ni dependen uno del otro. Cada uno es idempotente por `turnoId`, así que una reentrega no duplica la notificación ni el reclamo.

Una salvedad: la llamada HTTP al proveedor de video está adentro de la transacción en el tiempo, pero no participa de ella. Si la sala se crea y después falla algo posterior, el rollback borra la fila de `SesionVideo`, pero la sala del proveedor ya existe. No queda nadie con ese enlace y el reintento pide una sala nueva, así que el efecto es una sala huérfana sin uso. Lo mismo, con dinero, es lo que analiza la PR #33 para el cobro del copago.

## Párrafo para el documento técnico

> Texto propuesto para que Juan Pablo lo integre en `docs/documento-tecnico.md` (por ejemplo, junto a las secciones 9.1, 9.2 y 9.4). No se modificó ese documento.

La confirmación de un turno (`PUT /api/turnos/{id}/confirmar`) separa con precisión lo que tiene que ser atómico de lo que puede ser eventual. `ServicioDeTurnos.confirmarTurno` corre en una única transacción JTA: bloquea la fila del turno, valida que el hold siga vigente y sea del paciente que llama, y dispara el evento CDI sincrónico `TurnoEnConfirmacion`. Sus observadores corren en el mismo hilo y en la misma transacción, en el orden que fija `PuntosDeExtension`. Hoy el único es `SalaAlConfirmar` (prioridad 200): si el turno es de telemedicina, pide la sala a `ServicioDeTelemedicina`, que llega al proveedor de video externo por REST a través del Adapter `ProveedorDeVideoRestClient`, con timeouts cortos para no dejar la fila bloqueada. Si el proveedor no responde, la `ProveedorDeVideoNoDisponibleException` (`rollback = true`) sale por el `fire()` y revierte todo: el turno sigue `EN_HOLD`, su temporizador sigue vivo y el paciente recibe un 503 con `Retry-After` para reintentar. Si todo sale bien, el turno pasa a `CONFIRMADO`, se cancela su temporizador y se publica `TurnoConfirmado` con un `JMSContext` enlistado en la misma transacción, de modo que el mensaje solo existe si la confirmación se commiteó. A partir del commit, el tópico entrega el evento en paralelo a dos suscriptores independientes, `NotificacionMDB` y `TurnoConfirmadoFacturacionMDB`, cada uno en su propia transacción y con reentrega de Artemis ante fallas. Así, notificar al paciente o encolar el reclamo a la obra social nunca puede deshacer ni demorar una confirmación ya hecha, y `ServicioDeTurnos` no conoce a ninguno de los dos componentes. El cobro del copago (SCRUM-93) se sumará como el observador de prioridad 100, antes de la sala, sin tocar `confirmarTurno` más allá del dato del medio de pago.
