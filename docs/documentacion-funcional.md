---
title: "MediConecta - Documentación funcional"
---

# MediConecta

**Documentación funcional - Historias de usuario por actor**

Este documento complementa `documento-tecnico.md` (que explica arquitectura y decisiones) con la vista funcional: qué puede hacer cada actor, con qué criterio de aceptación y contra qué endpoint real. Cada historia implementada está verificada contra el código (`src/main/java/**/presentacion/*Resource.java`, `web.xml`, la colección de Postman en `postman/`), no contra la intención original; donde el código y una entrega anterior de este documento discrepaban, gana el código.

Una historia "Implementada" tiene endpoint y rol funcionando de punta a punta. "En revisión" tiene una pull request abierta que todavía no está en esta rama. "Planificada" no tiene código: es responsabilidad e integración descritas en `documento-tecnico.md` (secciones 4 y 11), sin implementación.

## Índice

| Actor | Historia | Estado | Endpoint |
|---|---|---|---|
| Paciente | Registrarme en la plataforma | Implementada | `POST /api/usuarios` |
| Paciente | Iniciar sesión | Implementada | `POST /api/usuarios/login` |
| Paciente | Consultar la disponibilidad de un profesional | Implementada | `GET /api/turnos?profesionalId=` |
| Paciente | Reservar un turno (con hold temporal) | Implementada | `POST /api/turnos` |
| Paciente | Confirmar un turno reservado | Implementada | `PUT /api/turnos/{id}/confirmar` |
| Paciente | Cancelar un turno | Implementada | `PUT /api/turnos/{id}/cancelar` |
| Paciente | Consultar mi propia historia clínica | Implementada | `GET /api/historias/paciente/{pacienteId}` y `/entradas` |
| Paciente | Pagar el copago de un turno | Implementada | `POST /api/pagos` |
| Paciente | Consultar el estado de mi pago | Implementada | `GET /api/pagos/{id}` y `GET /api/pagos?turnoId=` |
| Paciente | Ver que mi cobertura se valide automáticamente al reservar | Implementada (SCRUM-91, PR #19) | — (`reservarTurno`, evento `TurnoEnReserva`) |
| Paciente | Ver sólo mis propios turnos, sin conocer sus id | Implementada (PR #20) | `GET /api/turnos/mios` |
| Paciente | Recibir una notificación cuando se confirma mi turno | Implementada (PR #15) | segundo suscriptor del tópico `TurnoConfirmado`; `GET /api/notificaciones/mias` |
| Paciente | Que se me cobre el copago automáticamente al confirmar el turno | Planificada (SCRUM-93) | — (`TurnoEnConfirmacion`, `PuntosDeExtension.COBRO_COPAGO`) |
| Profesional de salud | Iniciar sesión | Implementada | `POST /api/usuarios/login` |
| Profesional de salud | Abrir una franja horaria disponible | Implementada | `POST /api/turnos/disponibilidad` |
| Profesional de salud | Ver mi propia agenda de disponibilidad | Implementada | `GET /api/turnos?profesionalId=` |
| Profesional de salud | Crear la historia clínica de un paciente | Implementada | `POST /api/historias/paciente/{pacienteId}` |
| Profesional de salud | Agregar una entrada a una historia clínica | Implementada | `POST /api/historias/paciente/{pacienteId}/entradas` |
| Profesional de salud | Registrar una consulta (diagnóstico y recetas) | Implementada | `POST /api/historias/paciente/{pacienteId}/consultas` |
| Profesional de salud | Consultar la historia clínica de cualquier paciente que atiendo | Implementada | `GET /api/historias/paciente/{pacienteId}` y `/entradas` |
| Administrador de clínica | Iniciar sesión | Implementada | `POST /api/usuarios/login` |
| Administrador de clínica | Listar todos los usuarios registrados | Implementada | `GET /api/usuarios` |
| Administrador de clínica | Dar de alta profesionales y otros administradores | Implementada | `POST /api/usuarios` |
| Administrador de clínica | Reembolsar un pago | Implementada | `PUT /api/pagos/{id}/reembolso` |
| Administrador de clínica | Revisar los reclamos de facturación, incluidos los que quedaron en revisión manual | Implementada | `GET /api/reclamos` |
| Administrador de clínica | Afiliar a un paciente a una obra social | Implementada en negocio, sin endpoint expuesto | — (`ServicioDeObrasSociales.registrarAfiliacion`) |
| Sistema de la obra social (externo) | Recibir una consulta de cobertura | Implementada (legado simulado) | SOAP `validarCobertura` |
| Sistema de la obra social (externo) | Recibir un pedido de autorización de una prestación | Implementada (legado simulado) | SOAP `autorizarPrestacion` |
| Sistema de la obra social (externo) | Recibir la presentación de un reclamo ya autorizado | Implementada (legado simulado) | SOAP `presentarReclamo` |
| Pasarela de pago (externo) | Recibir un pedido de cobro | Implementada (simulador) | `POST /api/externo/pagos` |
| Pasarela de pago (externo) | Recibir un pedido de reembolso | Implementada (simulador) | sub-recurso `.../reembolsos` de `/api/externo/pagos` |
| Transversal (todos los roles) | Operar el sistema desde una interfaz web en vez de un cliente HTTP manual | Implementada para el login (PR #12 / #14); resto de pantallas pendiente (SCRUM-83 a 87) | SPA React, login conectado a `POST /api/usuarios/login` con redirección por rol |
| Transversal (paciente y profesional) | Atender o atenderse por videoconsulta | En revisión (PR #21, SCRUM-99); enganche a la confirmación planificado (SCRUM-95) | `POST /api/externo/salas`, `POST`/`GET /api/telemedicina/turno/{turnoId}` |

## Paciente

### Registrarme en la plataforma

**Como** paciente **quiero** registrarme con mi email y una contraseña **para** poder reservar turnos y acceder a mis propios datos.

- El registro es público: no requiere estar autenticado.
- El email es único; un registro con un email ya existente se rechaza con `400`.
- Si no indico un rol, o indico `PACIENTE`, la cuenta queda creada con ese rol. Pedir `PROFESIONAL` o `ADMINISTRADOR` sin estar autenticado como `ADMINISTRADOR` se rechaza (ver historia equivalente del administrador).
- La contraseña se guarda con PBKDF2 + salt (`documento-tecnico.md`, sección 11), nunca en texto plano ni logueada.

**Estado:** Implementada.
**Endpoint:** `POST /api/usuarios`.

### Iniciar sesión

**Como** paciente **quiero** autenticarme con mi email y contraseña **para** operar el resto de la API con mi propia identidad.

- Credenciales correctas: `200` y la sesión queda autenticada con HTTP Basic para las siguientes llamadas.
- Credenciales incorrectas o usuario inexistente: `401`, sin distinguir cuál de las dos cosas falló (para no filtrar qué emails existen).

**Estado:** Implementada.
**Endpoint:** `POST /api/usuarios/login`.

### Consultar la disponibilidad de un profesional

**Como** paciente **quiero** ver los turnos disponibles de un profesional **para** elegir cuál reservar.

- Requiere estar autenticado (`web.xml` protege `/api/turnos/*` para `PACIENTE`, `PROFESIONAL` y `ADMINISTRADOR`); un anónimo recibe `401`.
- Devuelve únicamente los turnos en estado `DISPONIBLE` del profesional indicado.

**Estado:** Implementada.
**Endpoint:** `GET /api/turnos?profesionalId=`.

### Reservar un turno (con hold temporal)

**Como** paciente **quiero** reservar un turno y que quede retenido por un tiempo limitado **para** poder confirmarlo sin que otro paciente me lo saque mientras decido.

- Al reservar, el turno pasa de `DISPONIBLE` a `EN_HOLD`, con `inicioHold` seteado.
- El hold dura aproximadamente 5 minutos; vencido ese plazo sin confirmación, `ExpiradorDeHolds` (`@Singleton`/`TimerService`) lo libera automáticamente, sin que el paciente tenga que hacer nada (documento-tecnico.md, sección 6.1).
- Reservar un turno que ya no está `DISPONIBLE` (porque otro paciente lo tomó primero) se rechaza con `409`.
- Solo puede reservar un paciente autenticado (`@RolesAllowed(PACIENTE)`).

**Estado:** Implementada.
**Endpoint:** `POST /api/turnos`.

### Confirmar un turno reservado

**Como** paciente **quiero** confirmar un turno que tengo reservado **para** que quede efectivamente agendado antes de que se venza el hold.

- Solo el paciente dueño del hold puede confirmar el turno propio; otro paciente autenticado recibe `403` (`ServicioDeTurnos.verificarQueElHoldEsDelCaller`).
- Confirmar cancela el temporizador de expiración de ese turno y lo pasa a `CONFIRMADO`.
- Al confirmar se publica el evento `TurnoConfirmado` en el tópico JMS correspondiente (documento-tecnico.md, sección 9.1), que hoy consume Facturación.
- Confirmar un turno ya confirmado, o que ya venció su hold, se rechaza con `409`.

**Estado:** Implementada.
**Endpoint:** `PUT /api/turnos/{id}/confirmar`.

### Cancelar un turno

**Como** paciente **quiero** cancelar un turno que reservé o confirmé **para** liberar el horario si ya no lo necesito.

- Solo el paciente dueño del turno puede cancelarlo.
- Cancelar libera el turno (vuelve a `DISPONIBLE`) y cancela cualquier temporizador de expiración pendiente.

**Estado:** Implementada.
**Endpoint:** `PUT /api/turnos/{id}/cancelar`.

### Consultar mi propia historia clínica

**Como** paciente **quiero** ver mis propios antecedentes, diagnósticos y recetas **para** conocer mi historial médico.

- Un paciente autenticado solo puede leer su propia historia clínica: `ServicioDeHistoriaClinica.verificarQuePuedeLeerLaHistoria` compara el `pacienteId` solicitado contra el `Principal` autenticado y rechaza con `403` si no coinciden.
- `PROFESIONAL` y `ADMINISTRADOR` sí pueden leer la historia de cualquier paciente (ver su propia historia, sección "Profesional de salud").

**Estado:** Implementada.
**Endpoint:** `GET /api/historias/paciente/{pacienteId}` (resumen) y `GET /api/historias/paciente/{pacienteId}/entradas` (listado de entradas).

### Pagar el copago de un turno

**Como** paciente **quiero** que se registre el cobro de mi copago contra la pasarela de pago **para** completar el pago de mi atención.

- El pago se persiste primero en una transacción propia (`PagoDAO.guardarEnNuevaTransaccion`) y después se envía a la pasarela externa; si la pasarela no responde, el pago queda registrado igual, con el estado que corresponda, no se pierde.
- Solo `PACIENTE` o `ADMINISTRADOR` pueden iniciar un cobro; un `PROFESIONAL` autenticado recibe `403` a nivel de negocio aunque `web.xml` permita la llamada a nivel grueso.
- Hoy el cobro se dispara con una llamada explícita a este endpoint. El disparo automático al confirmar el turno todavía no existe (ver historia "Que se me cobre el copago automáticamente al confirmar el turno").

**Estado:** Implementada.
**Endpoint:** `POST /api/pagos`.

### Consultar el estado de mi pago

**Como** paciente **quiero** consultar el estado de un pago propio, o los pagos de un turno propio **para** saber si ya se acreditó o si falló.

- Un paciente solo puede consultar pagos de turnos propios (`ServicioDePagos.verificarQueElTurnoEsDelCaller`); `PROFESIONAL` y `ADMINISTRADOR` pueden consultar cualquiera.

**Estado:** Implementada.
**Endpoint:** `GET /api/pagos/{id}` y `GET /api/pagos?turnoId=`.

### Ver que mi cobertura se valide automáticamente al reservar

**Como** paciente **quiero** que al reservar un turno se consulte automáticamente mi cobertura con la obra social **para** saber de antemano si está autorizada y cuánto voy a pagar de copago.

- El Adapter que hace esa consulta (`ServicioDeObrasSociales`, documento-tecnico.md sección 6.3) ya está enganchado a la reserva: `CoberturaEnLaReserva` observa `TurnoEnReserva` y completa la cobertura antes de retener el turno.
- Si el legado de la obra social no responde, la reserva completa falla con `503` (no se reserva como particular sin aviso); un paciente sin afiliación se reserva igual, como particular, pagando el valor total.

**Estado:** Implementada (SCRUM-91, PR #19).
**Endpoint:** no expone un endpoint propio; está integrada dentro de `POST /api/turnos`.

### Ver sólo mis propios turnos, sin conocer sus id

**Como** paciente **quiero** un listado de mis propios turnos **para** no tener que recordar el id de cada uno para confirmarlo o cancelarlo.

- Un paciente autenticado recibe sus turnos en estado `EN_HOLD` o `CONFIRMADO`.
- Un profesional autenticado recibe su propia agenda, en cualquier estado, con el filtro opcional `?fecha=` (formato `YYYY-MM-DD`) para acotarla a un día puntual.
- Un administrador autenticado recibe `403`: el endpoint es la vista personal de un paciente o un profesional, no una consulta administrativa.

**Estado:** Implementada (PR #20).
**Endpoint:** `GET /api/turnos/mios` (opcionalmente `?fecha=` para el profesional).

### Recibir una notificación cuando se confirma mi turno

**Como** paciente **quiero** recibir una notificación cuando mi turno se confirma **para** enterarme sin tener que consultar la aplicación.

- `ServicioDeTurnos.confirmarTurno` publica el evento `TurnoConfirmado` en un tópico JMS (documento-tecnico.md, sección 9.1); `NotificacionMDB` es el segundo suscriptor de ese tópico y genera la notificación del paciente.
- El paciente puede además consultar sus propias notificaciones con `GET /api/notificaciones/mias`; sólo `PACIENTE` puede llamarlo (`web.xml` protege `/api/notificaciones/*`).

**Estado:** Implementada (PR #15).
**Endpoint:** segundo suscriptor del tópico `TurnoConfirmado`; `GET /api/notificaciones/mias`.

### Que se me cobre el copago automáticamente al confirmar el turno

**Como** paciente **quiero** que el copago se cobre solo, al confirmar el turno **para** no tener que llamar a un endpoint de pago por separado.

- Existe el punto de extensión (`PuntosDeExtension.COBRO_COPAGO`, prioridad 100, dentro del evento `TurnoEnConfirmacion`), pero no hay ningún observador `@Observes` implementado todavía: el propio código deja el comentario "Pendiente" en `ServicioDeTurnos` y en `TurnoEnConfirmacion`.

**Estado:** Planificada (SCRUM-93). Sin código.
**Endpoint:** no aplica todavía.

## Profesional de salud

### Iniciar sesión

**Como** profesional de salud **quiero** autenticarme con mi email y contraseña **para** operar la API con mi propia identidad.

- Mismo mecanismo que el paciente (HTTP Basic, Jakarta Security).

**Estado:** Implementada.
**Endpoint:** `POST /api/usuarios/login`.

### Abrir una franja horaria disponible

**Como** profesional de salud **quiero** abrir una franja horaria **para** que los pacientes puedan reservarla.

- Solo un `PROFESIONAL` autenticado puede abrir disponibilidad (`@RolesAllowed(PROFESIONAL)` en `ServicioDeTurnos.abrirDisponibilidad`, no en el recurso JAX-RS); un `PACIENTE` recibe `403`.
- Abrir una franja sin horario, o con un horario ya pasado, se rechaza (validación agregada, documento-tecnico.md sección 11).

**Estado:** Implementada.
**Endpoint:** `POST /api/turnos/disponibilidad`.

### Ver mi propia agenda de disponibilidad

**Como** profesional de salud **quiero** ver mis turnos (disponibles, reservados, confirmados) **para** organizar mi agenda.

- El mismo endpoint que usa el paciente para consultar disponibilidad, filtrado por `profesionalId`.

**Estado:** Implementada.
**Endpoint:** `GET /api/turnos?profesionalId=`.

### Crear la historia clínica de un paciente

**Como** profesional de salud **quiero** crear la historia clínica de un paciente que todavía no tiene una **para** poder empezar a registrar su atención.

- Solo `PROFESIONAL` (`@RolesAllowed("PROFESIONAL")` en `ServicioDeHistoriaClinica.crearHistoria`).

**Estado:** Implementada.
**Endpoint:** `POST /api/historias/paciente/{pacienteId}`.

### Agregar una entrada a una historia clínica

**Como** profesional de salud **quiero** agregar un antecedente, un diagnóstico o una receta **para** dejar constancia puntual, fuera de una consulta completa.

- El tipo de entrada (`Antecedente`, `Diagnostico`, `Receta`) lo decide `EntradaClinicaFactory` según el DTO recibido, con sus propias validaciones (una receta sin medicamento, dosis o días de tratamiento se rechaza con `400`).

**Estado:** Implementada.
**Endpoint:** `POST /api/historias/paciente/{pacienteId}/entradas`.

### Registrar una consulta (diagnóstico y recetas)

**Como** profesional de salud **quiero** registrar el resultado completo de una consulta (un diagnóstico y, si corresponde, una o más recetas) **para** dejarlo persistido de forma atómica.

- Toda la consulta se persiste en una única transacción (`@TransactionAttribute(REQUIRED)` explícito en `registrarConsulta`); si una receta es inválida, se revierte también lo ya persistido en esa misma llamada.

**Estado:** Implementada.
**Endpoint:** `POST /api/historias/paciente/{pacienteId}/consultas`.

### Consultar la historia clínica de cualquier paciente que atiendo

**Como** profesional de salud **quiero** leer la historia clínica de cualquier paciente **para** atenderlo con su historial completo.

- `PROFESIONAL` y `ADMINISTRADOR` leen sin restricción adicional; no hay chequeo de "paciente propio" porque cualquiera de los dos roles puede necesitar atender o auditar a cualquier paciente.

**Estado:** Implementada.
**Endpoint:** `GET /api/historias/paciente/{pacienteId}` y `GET /api/historias/paciente/{pacienteId}/entradas`.

## Administrador de clínica

### Iniciar sesión

**Como** administrador de clínica **quiero** autenticarme con mi email y contraseña **para** operar la API con mi propia identidad.

**Estado:** Implementada.
**Endpoint:** `POST /api/usuarios/login`.

### Listar todos los usuarios registrados

**Como** administrador de clínica **quiero** ver el listado completo de usuarios **para** administrar pacientes, profesionales y otros administradores.

- Protegido en dos niveles: `web.xml` (sólo `ADMINISTRADOR` puede llamar a `GET /api/usuarios`) y `@RolesAllowed("ADMINISTRADOR")` repetido en el recurso JAX-RS y en el método de negocio; es el único endpoint con la anotación en ambas capas.

**Estado:** Implementada.
**Endpoint:** `GET /api/usuarios`.

### Dar de alta profesionales y otros administradores

**Como** administrador de clínica **quiero** crear cuentas con rol `PROFESIONAL` o `ADMINISTRADOR` **para** dar de alta al personal de la clínica.

- El mismo endpoint de registro acepta un rol elevado solo si quien llama ya está autenticado como `ADMINISTRADOR` (chequeo programático en `ServicioDeUsuarios.registrarUsuario`); sin esa condición, el registro queda forzado a `PACIENTE`.

**Estado:** Implementada.
**Endpoint:** `POST /api/usuarios`.

### Reembolsar un pago

**Como** administrador de clínica **quiero** reembolsar un pago ya cobrado **para** resolver un reclamo o un error de cobro.

- Solo `ADMINISTRADOR` (`@RolesAllowed(ADMINISTRADOR)` en `ServicioDePagos.reembolsar`).

**Estado:** Implementada.
**Endpoint:** `PUT /api/pagos/{id}/reembolso`.

### Revisar los reclamos de facturación

**Como** administrador de clínica **quiero** ver el listado de reclamos presentados a obras sociales, con su estado **para** dar seguimiento a la facturación y detectar los que quedaron en revisión manual.

- El reclamo no se crea con una llamada manual: se genera automáticamente cuando `ServicioDeTurnos.confirmarTurno` publica `TurnoConfirmado` y `TurnoConfirmadoFacturacionMDB` lo recibe (documento-tecnico.md, sección 9.3). No existe, ni existió, un `POST /api/reclamos`.
- Un reclamo que agota sus reintentos contra la obra social (5 intentos, backoff de 2 s a 30 s) o que recibe un rechazo definitivo queda en `EN_REVISION_MANUAL`, visible en este mismo listado.
- Solo `ADMINISTRADOR`; `PACIENTE` recibe `403`, anónimo recibe `401`.

**Estado:** Implementada.
**Endpoint:** `GET /api/reclamos`.

### Afiliar a un paciente a una obra social

**Como** administrador de clínica **quiero** afiliar a un paciente a una obra social, con su número de afiliado **para** que después pueda validarse su cobertura.

- `ServicioDeObrasSociales.registrarAfiliacion` existe, está protegido con `@RolesAllowed("ADMINISTRADOR")` y tiene pruebas, pero no tiene ningún endpoint JAX-RS que lo expuso todavía (documento-tecnico.md, sección 6.3): hoy sólo se usa desde `SeedDeAfiliaciones` al arrancar el servidor, para el paciente de prueba.

**Estado:** Implementada en negocio, sin endpoint expuesto.
**Endpoint:** ninguno (no hay un `*Resource` para `ServicioDeObrasSociales`).

## Sistemas externos

Estas historias describen al sistema externo como actor: qué espera recibir y qué responde. Del lado de MediConecta los consume `ServicioDeObrasSociales` (Adapter, SOAP) y `ServicioDePagos` (cliente REST); documento-tecnico.md, secciones 6.2, 6.3 y la sección 4.1 explican por qué cada canal es el que es.

### Obra social: recibir una consulta de cobertura

**Como** sistema legado de la obra social **quiero** recibir el DNI, el número de afiliado y el código de prestación **para** informar si la prestación está cubierta, con qué porcentaje y, si corresponde, el copago.

- Es una consulta: nunca devuelve número de autorización, y no persiste nada del lado de MediConecta.

**Estado:** Implementada (legado simulado, `externos.obrasocial`).
**Endpoint:** operación SOAP `validarCobertura`, WSDL en `http://localhost:8080/mediconecta/legado/obrasocial?wsdl`.

### Obra social: recibir un pedido de autorización

**Como** sistema legado de la obra social **quiero** recibir un pedido de autorización de una prestación **para** devolver, si corresponde, un número de autorización que MediConecta pueda usar más adelante para facturar.

- Un pedido con afiliado inexistente, DNI que no corresponde o prestación desconocida se rechaza con un SOAP Fault `Client`, no con una respuesta de negocio.

**Estado:** Implementada (legado simulado).
**Endpoint:** operación SOAP `autorizarPrestacion`.

### Obra social: recibir la presentación de un reclamo

**Como** sistema legado de la obra social **quiero** recibir una autorización ya emitida **para** reconocer el monto que me corresponde pagar por esa prestación.

- Valida que el número de autorización tenga el formato que la propia obra social emitió y que corresponda al afiliado y al DNI; una autorización que no corresponde es un pedido inválido (SOAP Fault `Client`), no un rechazo de negocio.

**Estado:** Implementada (legado simulado, SCRUM-97).
**Endpoint:** operación SOAP `presentarReclamo`.

### Pasarela de pago: recibir un pedido de cobro

**Como** pasarela de pago **quiero** recibir los datos de un turno y un monto **para** procesarlos y devolver una confirmación o un rechazo.

**Estado:** Implementada (simulador, `externos.pasarela`).
**Endpoint:** `POST /api/externo/pagos`.

### Pasarela de pago: recibir un pedido de reembolso

**Como** pasarela de pago **quiero** recibir el pedido de reembolso de un cobro ya procesado **para** revertirlo.

**Estado:** Implementada (simulador).
**Endpoint:** sub-recurso de reembolsos de `/api/externo/pagos` (`PasarelaDePagoRestClient`).

## Historias transversales

No pertenecen a un solo actor; afectan a varios a la vez.

### Operar el sistema desde una interfaz web

**Como** paciente, profesional o administrador **quiero** usar una interfaz web **para** no tener que llamar a la API con un cliente HTTP manual.

- La SPA (React) consume la misma API REST documentada arriba, con HTTP Basic.
- El login ya está conectado: `POST /api/usuarios/login` autentica contra la API real y redirige a cada usuario según su rol (paciente, profesional o administrador).
- El resto de las pantallas (agenda, disponibilidad, historia clínica, hold) todavía muestra datos de prototipo, no la respuesta real de la API; conectarlas es SCRUM-83 a SCRUM-87, pendiente.

**Estado:** Implementada para el login (PR #12 y PR #14); resto de las pantallas pendiente (SCRUM-83 a 87).
**Endpoint:** no agrega endpoints nuevos; el login consume `POST /api/usuarios/login`.

### Atender o atenderse por videoconsulta

**Como** paciente **quiero** una sala de videollamada para mi turno de telemedicina, y **como** profesional **quiero** atenderlo por ese mismo medio, **para** no requerir presencia física en el consultorio.

- `ModalidadTurno.TELEMEDICINA` ya existe como valor del turno. El proveedor de video simulado (PR #21, SCRUM-99) expone `POST /api/externo/salas` y `POST`/`GET /api/telemedicina/turno/{turnoId}`.
- Falta enganchar la creación de sala al flujo de confirmación del turno: eso es SCRUM-95, planificado.

**Estado:** En revisión (PR #21, SCRUM-99); enganche a la confirmación planificado (SCRUM-95).
**Endpoint:** `POST /api/externo/salas`, `POST`/`GET /api/telemedicina/turno/{turnoId}` (en revisión).
