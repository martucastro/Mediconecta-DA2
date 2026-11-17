# Decisiones de arquitectura (ADR)

Cada ADR registra una decisión de arquitectura relevante de MediConecta: el problema, lo que se decidió, las alternativas que se descartaron y por qué, y lo que la decisión cuesta. Todas citan las clases reales del código y se verificaron contra `src/main/java`.

| N.º | Decisión | Estado | Fecha |
|---|---|---|---|
| [0001](0001-estado-del-hold-en-la-fila-del-turno.md) | El estado del hold vive en la fila del turno, no en la memoria del bean stateful | Aceptada | 2026-09-13 |
| [0002](0002-topico-para-avisar-cola-para-el-reclamo.md) | Tópico `TurnoConfirmado` para avisar, cola `ReclamosFacturacion` para el reclamo | Aceptada | 2026-10-01 |
| [0003](0003-puntos-de-extension-con-eventos-cdi-sincronicos.md) | Puntos de extensión del turno con eventos CDI sincrónicos, no con JMS | Aceptada | 2026-09-28 |
| [0004](0004-adapter-para-el-legado-soap.md) | Adapter propio para el legado SOAP de la obra social | Aceptada | 2026-09-28 |

La fecha es la de la decisión. Los cuatro ADR se redactaron después, el 2026-11-17, a partir del código y de los documentos técnicos.

## Cómo leerlos

- **Por dónde empezar.** 0001 explica el componente stateful. 0002 y 0003 son las dos caras de la misma línea divisoria: lo que decide si un paso del turno ocurre es síncrono y comparte su transacción (0003); lo que reacciona a un hecho ya confirmado es asincrónico y corre en su propia transacción (0002). 0004 es la integración con el tercero que impone su contrato.
- **Formato.** Es el de Michael Nygard: título, estado, fecha, contexto, decisión, alternativas consideradas (cada una con el motivo del descarte) y consecuencias, positivas y negativas. Cada ADR cierra con las referencias al código.
- **Un ADR no se reescribe.** Si una decisión cambia, se agrega un ADR nuevo con el número siguiente, y el anterior pasa a estado "Reemplazada por ADR NNNN".
