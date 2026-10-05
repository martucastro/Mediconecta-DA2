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
        noti["ServicioDeNotificaciones<br/>MDB, en revision"]:::review
        tel["ServicioDeTelemedicina<br/>planificado"]:::plan
    end

    leg[("Legado de la obra social<br/>SOAP")]:::ext
    pas[("Pasarela de pago<br/>REST")]:::ext
    vid[("Proveedor de video<br/>REST, planificado")]:::plan

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
    tur -.->|"planificado SCRUM-91: cobertura al reservar"| os
    tur -.->|"planificado SCRUM-93: copago al confirmar"| pag
    tur -.->|"planificado SCRUM-95: sala de video"| tel
    tel -.->|"REST, planificado"| vid
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
        noti["ServicioDeNotificaciones<br/>en revision"]:::review
        tel["ServicioDeTelemedicina<br/>planificado"]:::plan
        subgraph art["ActiveMQ Artemis, broker JMS"]
            top["Topico<br/>TurnoConfirmado"]:::broker
            cola["Cola<br/>ReclamosFacturacion"]:::broker
            dlq["Cola de no entregados<br/>ReclamosFacturacionDLQ"]:::broker
        end
    end

    subgraph ext["Sistemas externos"]
        leg[("Legado de la obra social<br/>SOAP con WSDL")]:::ext
        pas[("Pasarela de pago<br/>REST")]:::ext
        vid[("Proveedor de video<br/>REST, planificado")]:::plan
    end

    spa -->|"REST JSON con HTTP Basic"| api
    api -->|"llamada local EJB"| tur
    api -->|"llamada local EJB"| pag
    api -->|"llamada local EJB"| fac
    tur -->|"topico JMS: publica al confirmar"| top
    top -->|"topico JMS: suscriptor"| fac
    top -.->|"topico JMS: suscriptor, en revision"| noti
    fac -->|"cola JMS: encola el reclamo"| cola
    cola -->|"cola JMS: MDB consume con reintentos"| fac
    cola -.->|"mensajes no procesables"| dlq
    fac -->|"llamada local EJB: presentarReclamo"| os
    fac -->|"llamada local EJB: obtenerTurno"| tur
    pag -->|"llamada local EJB: obtenerTurno"| tur
    os -->|"SOAP: validarCobertura, autorizarPrestacion, presentarReclamo"| leg
    pag -->|"REST: POST /externo/pagos"| pas
    tur -.->|"evento CDI sincronico, planificado SCRUM-91"| os
    tur -.->|"evento CDI sincronico, planificado SCRUM-95"| tel
    tel -.->|"REST, planificado"| vid
```
