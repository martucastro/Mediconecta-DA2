# MediConecta: diagramas de arquitectura

Versión editable en Miro: https://miro.com/app/board/uXjVEfW69Q0=/

Los dos diagramas de este archivo son la misma fuente Mermaid que está en el tablero, así que coinciden. GitHub los dibuja directamente.

**Convenciones.** Celeste: componente implementado y mergeado. Amarillo: en revisión (PR abierto). Gris: planificado. Violeta: sistema externo (simulado en el paquete `externos`). Naranja: destino JMS en Artemis. Línea continua: llamada sincrónica. Línea punteada: asincrónica (JMS) o planificada, según el rótulo.

## Arquitectura general: componentes y dependencias

Responde "quién llama a quién". Las dependencias sincrónicas son las que existen en el código: cada flecha es una llamada local a la fachada de otro componente, nunca a su DAO.

```mermaid
flowchart LR
    classDef impl fill:#9CE6FF,stroke:#2C97BB,color:#1C4657
    classDef review fill:#FFE86D,stroke:#A28E26,color:#574900
    classDef plan fill:#DDDDD8,stroke:#8A8A7E,color:#434339
    classDef ext fill:#B8ACFB,stroke:#8A7BE0,color:#231266
    classDef client fill:#B3E65F,stroke:#6E9A24,color:#2F440B

    spa["Frontend SPA<br/>React + TypeScript"]:::client
    api["API REST<br/>JAX-RS /api"]:::impl

    subgraph war["WAR mediconecta en WildFly 41"]
        usu["ServicioDeUsuarios<br/>Stateless"]:::impl
        tur["ServicioDeTurnos<br/>Stateful"]:::impl
        hc["ServicioDeHistoriaClinica<br/>Stateless"]:::impl
        os["ServicioDeObrasSociales<br/>Stateless, Adapter SOAP"]:::impl
        pag["ServicioDePagos<br/>Stateless, Adapter REST"]:::impl
        fac["ServicioDeFacturacion<br/>MDB sobre topico y cola"]:::impl
        noti["ServicioDeNotificaciones<br/>MDB sobre topico"]:::impl
        tel["ServicioDeTelemedicina<br/>Stateless, Adapter REST"]:::impl
    end

    leg[("Legado de la obra social<br/>SOAP")]:::ext
    pas[("Pasarela de pago<br/>REST")]:::ext
    vid[("Proveedor de video<br/>REST")]:::ext

    spa -->|"HTTP REST"| api
    api --> usu
    api --> tur
    api --> hc
    api --> pag
    api --> fac
    tur -->|"sincronico"| usu
    hc -->|"sincronico"| usu
    os -->|"sincronico"| usu
    pag -->|"obtenerTurno"| tur
    pag -->|"sincronico"| usu
    fac -->|"obtenerTurno"| tur
    fac -->|"presentarReclamo"| os
    tur -.->|"asincronico: TurnoConfirmado"| fac
    tur -.->|"asincronico: TurnoConfirmado"| noti
    os -->|"SOAP"| leg
    pag -->|"REST"| pas
    tur -->|"evento CDI sincronico: cobertura al reservar"| os
    tur -->|"evento CDI sincronico: copago al confirmar (SCRUM-93)"| pag
    tur -->|"evento CDI sincronico: sala de video al confirmar (SCRUM-95)"| tel
    tel -->|"REST"| vid
```

## Arquitectura de integración (SOA)

Responde "qué se conecta con qué y por qué canal". Cada conexión indica su canal: REST, SOAP, cola JMS, tópico JMS, llamada local EJB o evento CDI sincrónico. La justificación de cada elección está en la sección 4.1 del documento técnico.

```mermaid
flowchart LR
    classDef impl fill:#9CE6FF,stroke:#2C97BB,color:#1C4657
    classDef review fill:#FFE86D,stroke:#A28E26,color:#574900
    classDef plan fill:#DDDDD8,stroke:#8A8A7E,color:#434339
    classDef ext fill:#B8ACFB,stroke:#8A7BE0,color:#231266
    classDef client fill:#B3E65F,stroke:#6E9A24,color:#2F440B
    classDef broker fill:#FFB575,stroke:#CC7830,color:#542700

    spa["Frontend SPA<br/>React + TypeScript"]:::client

    subgraph wf["WildFly 41, WAR mediconecta"]
        api["API REST<br/>JAX-RS /api"]:::impl
        tur["ServicioDeTurnos"]:::impl
        pag["ServicioDePagos"]:::impl
        os["ServicioDeObrasSociales"]:::impl
        fac["ServicioDeFacturacion"]:::impl
        noti["ServicioDeNotificaciones"]:::impl
        tel["ServicioDeTelemedicina<br/>Stateless, Adapter REST"]:::impl
        subgraph art["ActiveMQ Artemis, broker JMS"]
            top["Topico<br/>TurnoConfirmado"]:::broker
            cola["Cola<br/>ReclamosFacturacion"]:::broker
            dlq["Cola de no entregados<br/>ReclamosFacturacionDLQ"]:::broker
        end
    end

    subgraph externos["Sistemas externos"]
        leg[("Legado de la obra social<br/>SOAP con WSDL")]:::ext
        pas[("Pasarela de pago<br/>REST")]:::ext
        vid[("Proveedor de video<br/>REST")]:::ext
    end

    spa -->|"REST JSON con HTTP Basic"| api
    api -->|"llamada local EJB"| tur
    api -->|"llamada local EJB"| pag
    api -->|"llamada local EJB"| fac
    api -->|"llamada local EJB"| tel
    tur -->|"topico JMS: publica al confirmar"| top
    top -->|"topico JMS: suscriptor"| fac
    top -->|"topico JMS: suscriptor"| noti
    fac -->|"cola JMS: encola el reclamo"| cola
    cola -->|"cola JMS: MDB consume con reintentos"| fac
    cola -.->|"mensajes no procesables"| dlq
    fac -->|"llamada local EJB: presentarReclamo"| os
    fac -->|"llamada local EJB: obtenerTurno"| tur
    pag -->|"llamada local EJB: obtenerTurno"| tur
    os -->|"SOAP: validarCobertura, autorizarPrestacion, presentarReclamo"| leg
    pag -->|"REST: POST /externo/pagos"| pas
    tur -->|"evento CDI sincronico: cobertura al reservar"| os
    tur -->|"evento CDI sincronico: copago al confirmar (SCRUM-93)"| pag
    tur -->|"evento CDI sincronico: sala al confirmar (SCRUM-95)"| tel
    tel -->|"REST"| vid
```

## Cobro del copago al confirmar: qué pasa si la pasarela falla (SCRUM-93)

`confirmarTurno` corre en una sola transacción JTA. El observador `CobroAlConfirmar`
(prioridad `COBRO_COPAGO` = 100) cobra el copago **dentro** de esa transacción,
antes de la sala de video (`SALA_DE_VIDEO` = 200). El rollback de JTA deshace la
base y JMS, pero **no deshace un cobro REST que ya salió**: la pasarela es un
tercero por HTTP. De ahí los tres desenlaces.

```mermaid
sequenceDiagram
    autonumber
    participant P as Paciente
    participant T as confirmarTurno (TX JTA)
    participant C as CobroAlConfirmar (100)
    participant G as Pasarela ($ externo)
    participant S as SalaAlConfirmar (200)

    P->>T: PUT /turnos/{id}/confirmar { tokenMedioDePago }
    T->>C: fire(TurnoEnConfirmacion)
    Note over C: copago null o 0 → return,<br/>no se cobra ni se pide medio de pago
    C->>G: cobrar(copago, token)

    alt A) la pasarela no responde
        G--xC: PasarelaNoDisponibleException (rollback=true)
        Note over T: rollback total: nada cobrado,<br/>turno sigue EN_HOLD, timer vivo
        T-->>P: 502 — reintentar
    else B) cobró OK, pero un paso posterior falla
        G-->>C: APROBADO ($ ya se movió)
        T->>S: fire → crear sala
        S--xT: ProveedorDeVideoNoDisponibleException (rollback=true)
        Note over T,G: rollback deshace la fila Pago,<br/>pero el $ externo ya salió = COBRO HUÉRFANO.<br/>El rastro PENDIENTE (REQUIRES_NEW) sobrevive<br/>→ compensar con ServicioDePagos.reembolsar
        T-->>P: 502/503
    else B') la pasarela rechaza el cobro
        G-->>C: RECHAZADO (no lanza)
        C--xT: ConflictoDeNegocioException (rollback=true)
        Note over T: no se confirma un turno impago
        T-->>P: 409
    else C) todo OK
        G-->>C: APROBADO
        T->>S: sala creada
        Note over T: commit único:<br/>turno CONFIRMADO + Pago APROBADO + mensaje TurnoConfirmado
        T-->>P: 200
    end
```

**Defensa de cada caso:**

- **A — pasarela caída:** `PasarelaNoDisponibleException` es `@ApplicationException(rollback = true)`; atraviesa el `fire()`, revierte todo, el turno sigue `EN_HOLD` con su temporizador vivo y el paciente reintenta. **No se cobró nada.** Mismo patrón que la sala (sección 9.4 del documento técnico).
- **B — cobro huérfano (el caso que la consigna pide defender):** si el cobro se aprueba y después falla la sala, JTA revierte la base pero el dinero externo ya se movió. Es inevitable con un cobro REST inmediato porque el recurso externo no es transaccional. Se acota así: `ServicioDePagos` inserta el `Pago` en `PENDIENTE` con `REQUIRES_NEW` **antes** de llamar a la pasarela, así ese rastro sobrevive al rollback y permite **compensar con un reembolso** (`ServicioDePagos.reembolsar`, que ya existe).
- **B' — rechazo del cobro:** la pasarela no lanza ante un rechazo; devuelve el `Pago` en `RECHAZADO`. `CobroAlConfirmar` lo traduce a `ConflictoDeNegocioException` (409) para no dejar un turno confirmado sin pagar.
- **C — camino feliz:** una sola transacción confirma el turno, aprueba el pago y publica `TurnoConfirmado` de forma atómica.

El medio de pago (`tokenMedioDePago`) viaja en el cuerpo del `PUT /turnos/{id}/confirmar`: el paciente confirma pagando, sin necesidad de un vault de medios guardados. Un turno sin copago (cobertura total o aún sin cotizar) se confirma sin cuerpo, como antes.
