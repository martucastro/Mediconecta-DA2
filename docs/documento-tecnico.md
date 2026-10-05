---
title: "MediConecta - Documento Técnico"
---

# MediConecta

**Documento técnico - Entrega Obligatoria N.º 1**

**Materia:** Desarrollo de Aplicaciones II
**Comisión:** Lunes TM
**Entrega:** Entrega Obligatoria N.º 1
**Fecha:** 14/09/2026

**Integrantes:**

| Apellido y nombre | Legajo |
|---|---|
| Castro, Martina | 1167379 |
| López Scala, Manuel | 1165832 |
| Mantegazza, Juan Pablo | 1152457 |
| Perez Ciccone, Luca | 1134329 |
| Pereyra Metnik, Gonzalo | 1075364 |

---

## 1. Introducción

MediConecta conecta pacientes con profesionales de la salud independientes y con clínicas para gestionar turnos presenciales y de telemedicina, incluyendo la facturación a obras sociales y prepagas. Este documento describe la arquitectura, el stack elegido, los ocho componentes de negocio, los patrones de diseño aplicados y el fundamento de cada decisión.

De los ocho componentes, tres ya están implementados: `ServicioDeUsuarios`, `ServicioDeTurnos` y `ServicioDeHistoriaClinica`. Los cinco restantes están definidos a nivel de responsabilidad e integración, sin código (sección 11).

Respecto de una entrega anterior, la separación en capas pasó de ser una convención de nombres a una estructura de paquetes real, y la autenticación quedó conectada de punta a punta y verificada en despliegue. También se corrigió un error en el temporizador de expiración de turnos (sección 6.1), evidencia de comprensión del modelo de componentes de Jakarta EE.

## 2. Elección del stack tecnológico

El criterio del grupo fue explícito: priorizar la cobertura **nativa** de los requisitos de la cátedra por sobre la afinidad con un lenguaje o framework. Se evaluaron alternativas como Spring Boot, .NET, Quarkus, Micronaut y Node con NestJS.

El stack elegido es:

- **Jakarta EE 11** sobre **WildFly** como contenedor de aplicaciones.
- **PostgreSQL** como motor de base de datos.
- **Maven** como herramienta de build.
- **IntelliJ IDEA Ultimate** como entorno de desarrollo.

### 2.1 El criterio de desempate: el componente stateful

La consigna exige evidencia concreta de que el contenedor gestiona el ciclo de vida de al menos un componente con estado conversacional (`ServicioDeTurnos`, que sostiene el hold de un turno reservado). Ese requisito, y no SOAP, decidió el stack.

Ninguna otra alternativa permitida tiene un equivalente nativo al `@Stateful` de Enterprise JavaBeans: en Spring Boot, .NET, Quarkus, Micronaut o Node/NestJS, el estado por conversación se programa a mano (un scheduler propio, una entrada en Redis con TTL, un `HostedService` con timers). Jakarta EE lo ofrece como parte de la especificación del contenedor, que instancia, destruye y expira estas instancias sin infraestructura adicional. La sección 6.1 muestra que aprovecharlo también exige entender sus límites.

### 2.2 Argumentos secundarios

- **ActiveMQ Artemis embebido**: mensajería asincrónica (`ServicioDeNotificaciones`, `ServicioDeFacturacion`) sin infraestructura adicional.
- **Apache CXF embebido**: JAX-WS para la integración SOAP con la obra social.

### 2.3 Un caveat honesto sobre SOAP

SOAP (JAX-WS) es **opcional** en Jakarta EE desde la versión 9, no obligatorio en todo servidor compatible. Por eso elegir WildFly no es un detalle secundario: es WildFly, no la especificación en abstracto, quien empaqueta CXF sin configuración extra. Con un servidor sin JAX-WS por defecto, la integración SOAP habría requerido agregar esa dependencia manualmente.

## 3. Arquitectura en capas

El sistema se organiza en tres capas: presentación, negocio y datos. La regla general es que cada capa depende de la inmediatamente inferior y que las reglas de negocio viven exclusivamente en la capa de negocio; la sección 3.1 aclara un matiz a esa regla.

| Capa | Responsabilidad | Tecnologías | Qué NO hace |
|---|---|---|---|
| Presentación | Recibe la interacción externa, valida formato de entrada, serializa la respuesta | React (SPA), recursos JAX-RS, endpoints JAX-WS | No decide nada del dominio; no contiene reglas de negocio ni de autorización |
| Negocio | Reglas del dominio, límites transaccionales, seguridad por rol | EJB (`@Stateless`, `@Stateful`, `@Singleton`), CDI, JTA, Jakarta Security | No conoce detalles de SQL ni de la SPA |
| Datos | Traduce objetos de dominio a filas de base de datos | Patrón DAO, JPA/Hibernate, PostgreSQL | No contiene reglas de negocio |

A diferencia de una entrega anterior, esta separación ya no depende del sufijo de una clase (`*Resource`, `Servicio*`, `*DAO`) conviviendo en un mismo paquete: hoy es una estructura de paquetes real. Bajo el paquete raíz `ar.edu.uade.da2.mediconecta`, los componentes que siguen esta estructura de tres subpaquetes homónimos de las capas (`presentacion`, `negocio`, `datos`) son `usuarios`, `turnos`, `historiaclinica` y `pagos`. A eso se suman `comun.negocio`, con las excepciones de negocio que comparten los cuatro componentes (`DatosInvalidosException`, `ConflictoDeNegocioException`), y `externos.pasarela`, que no es un componente de MediConecta sino la simulación del partner externo de pagos, expuesta como su propio recurso REST bajo `/api/externo/*` (sección 5).

La regla de dependencia queda así verificable con sólo mirar los imports: una clase de `datos` que importara algo de `presentacion` sería visible de inmediato como una violación de la arquitectura, cosa que la convención de nombres anterior no permitía detectar.

En el paquete raíz quedan las clases fuera de esa estructura: `ApiActivator` (`Application` de JAX-RS) y los `ExceptionMapper` transversales `AccesoDenegadoMapper`, `ErrorInesperadoMapper`, `DatosInvalidosMapper` y `ConflictoDeNegocioMapper` (estos dos últimos traducen las excepciones de `comun.negocio` a `400` y `409`). Es deliberado: son infraestructura JAX-RS que atraviesa los componentes de negocio por igual, y ubicarlos dentro de un componente sugeriría una pertenencia que no existe. `PasarelaNoDisponibleMapper`, en cambio, vive junto a la excepción que traduce, en `pagos.negocio`, porque es específica de ese componente.

### 3.1 Un matiz sobre la regla de capas adyacentes: los DTO conocen la entidad

La tabla de arriba describe la intención general, pero no es estrictamente "cada capa habla solo con la inmediata inferior": los DTO de presentación (`TurnoDTO`, `UsuarioDTO`, `HistoriaClinicaDTO`, entre otros) se construyen directamente a partir de la entidad JPA de `datos` (por ejemplo, `TurnoDTO(Turno turno)`), sin pasar por un objeto intermedio de `negocio`. La entidad hace de modelo compartido entre presentación y datos para ese propósito puntual. Presentación sigue sin importar `EntityManager` ni escribir SQL, y sigue sin decidir ninguna regla de negocio: lo único que cruza la capa de negocio es la forma del dato, no su comportamiento.

Hay además un paquete que no es nuestro, aunque viva en el mismo WAR: `externos.obrasocial`, la simulación del sistema legado de la obra social (sección 6.2). No se divide en capas porque no es un componente que diseñamos, sino un tercero del que sólo nos importa el contrato SOAP. Ninguna clase de los componentes lo importa: la única forma de llegar a él es su WSDL, igual que con el sistema real.

## 4. Los ocho componentes del sistema

| # | Componente | Tipo de EJB / integración | Responsabilidad | Estado |
|---|---|---|---|---|
| 1 | ServicioDeUsuarios | `@Stateless` | Registro, autenticación, perfiles | Implementado |
| 2 | ServicioDeTurnos | `@Stateful` (con `ExpiradorDeHolds` como `@Singleton` colaborador) | Disponibilidad, reserva, cancelación, hold de aproximadamente 5 minutos | Implementado |
| 3 | ServicioDeHistoriaClinica | `@Stateless` | Antecedentes, diagnósticos, recetas | Implementado |
| 4 | ServicioDeObrasSociales | Adapter vía SOAP | Validación de cobertura contra sistema legado | Implementado (sección 6.3), contra el legado simulado de la sección 6.2 |
| 5 | ServicioDePagos | REST | Cobro de copagos contra pasarela de pago | No implementado |
| 6 | ServicioDeTelemedicina | `@Stateless`, Adapter REST | Sala de video de los turnos de telemedicina | Implementado (sección 6.4), contra el proveedor simulado; falta el enganche con la confirmación (SCRUM-95) |
| 7 | ServicioDeNotificaciones | `@MessageDriven` (tópico JMS) | Notificación asincrónica de eventos | No implementado |
| 8 | ServicioDeFacturacion | `@MessageDriven` (tópico y cola JMS) | Reclamo de facturación a obras sociales/prepagas por turnos con cobertura autorizada | Implementado, con el canal real hacia la obra social (sección 9.3) |

Que los componentes ya convivan integrados y desplegados juntos fue justamente lo que permitió detectar y corregir la incompatibilidad entre `@Stateful` y `TimerService` descrita en la sección 6.1: un problema que solo se manifiesta con el sistema desplegado, no en aislamiento.

## 5. Caso de uso representativo: reservar un turno con cobertura

Este flujo atraviesa las tres capas y varios de los ocho componentes:

1. La SPA envía `POST /api/turnos`; el pedido llega a `TurnosResource`.
2. `ServicioDeTurnos` marca el turno como `EN_HOLD` y delega en `ExpiradorDeHolds` (`@Singleton` con `TimerService`) la programación del temporizador.
3. `TurnoDAO` verifica la disponibilidad del turno vía JPA.
4. `ServicioDeObrasSociales` (Adapter) valida la cobertura del paciente mediante una llamada SOAP al sistema legado de la obra social.
5. Si la cobertura es parcial, `ServicioDePagos` cobra el copago con una llamada REST a la pasarela de pago externa.
6. Toda la secuencia queda en una transacción declarativa; si un paso falla, se hace rollback de los anteriores.
7. Al confirmarse el turno se publica `TurnoConfirmado` en un tópico JMS, que `ServicioDeNotificaciones` consume asincrónicamente.
8. La SPA recibe `201 Created`.

Los pasos 1 a 4, 6 (parcialmente, sección 11) y la mitad publicadora del paso 7 (sección 9.1) están respaldados por código real, verificado en la sección 10. El paso 4 lo ejecuta `CoberturaEnLaReserva`, un observador de `TurnoEnReserva` que llama a `ServicioDeObrasSociales` (sección 6.3). El paso 5 y el lado consumidor del paso 7 (`ServicioDeNotificaciones`) describen el diseño previsto para los componentes aún no implementados (secciones 4 y 11).

## 6. Evidencia de implementación

Esta sección documenta lo que existe en el código; la sección 3 ya muestra dónde vive cada clase.

- **ServicioDeUsuarios** (`@Stateless`, `@PermitAll` de clase): `UsuarioDAO`, `Usuario` (entidad `usuarios`), `UsuariosResource` (`@RolesAllowed("ADMINISTRADOR")` en el listado), `PasswordUtil` (SHA-256 sin salt, sección 11). El `@PermitAll` no es descuido: sin él, WildFly bloquearía registro y login en cuanto el bean tuviera alguna anotación de seguridad.
- **ServicioDeTurnos y `ExpiradorDeHolds`**: el cambio más importante de esta entrega, detallado en 6.1.
- **ServicioDeHistoriaClinica** (`@Stateless`, Facade, sección 8.2): `ServicioDeHistoriaClinicaLocal` (`@Local`, cinco operaciones), `EntradaClinicaFactory` (Factory, sección 8.3), `HistoriaClinicaDAO`/`EntradaClinicaDAO`, `HistoriaClinica` (paciente por id, no `@ManyToOne`, porque esa tabla es de otro componente), `EntradaClinica` (herencia de tabla única; subclases `Antecedente`/`Diagnostico`/`Receta`), `HistoriaClinicaResource`.

### 6.1 ServicioDeTurnos y la expiración del hold

`ServicioDeTurnos` sigue siendo `@Stateful` y sostiene el hold del turno, pero ya **no** aloja el `TimerService` que lo expira: inyecta un colaborador, `ExpiradorDeHolds`, y le delega tanto la programación del temporizador al reservar como su cancelación al confirmar o cancelar.

No es una opinión del equipo: el javadoc de `jakarta.ejb.TimerService` (API `jakarta.jakartaee-api` 11.0.0) enumera qué tipos de bean pueden registrar timers:

> "The enterprise bean Timer Service allows stateless session beans, singleton session beans, message-driven beans, and enterprise bean 2.x entity beans to be registered for timer callback events."

Los *stateful* session beans no figuran en esa lista. En una iteración anterior, con el timer dentro de `ServicioDeTurnos`, WildFly inyectaba un `TimerService` no funcional y `POST /api/turnos` devolvía `500` en la primera reserva. La solución fue separar dos responsabilidades en dos tipos de bean. `ServicioDeTurnos` conserva el estado conversacional, que es lo que justifica que sea stateful. `ExpiradorDeHolds`, un `@Singleton`, concentra el `TimerService` y el método anotado `@Timeout` que el contenedor invoca al vencer cada plazo. La división no es un rodeo para esquivar una limitación: expresa que la expiración programada no pertenece a la conversación con un cliente sino al contenedor, y que por eso debe vivir en un bean cuyo ciclo de vida no dependa de esa conversación.

Corregir esto dejó a la vista un segundo problema. La versión anterior cancelaba recorriendo todos los temporizadores devueltos por `getTimers()` y cancelándolos sin discriminar. El javadoc de ese método es explícito: devuelve "all active timers associated with this bean", es decir todos los del bean, no los de un paciente en particular. Esa implementación cancelaba también los holds de los demás pacientes, que quedaban retenidos para siempre. La versión actual identifica cada temporizador por el dato con el que fue creado, el identificador del turno, y cancela únicamente el que corresponde.

Entidades: `Turno` (`paciente`/`profesional` `@ManyToOne`, `estado`, `inicioHold`, `modalidad`, `consultorio` y los cuatro campos de cobertura: `coberturaAutorizada`, `coberturaPorcentaje`, `copago`, `numeroAutorizacion`), `ModalidadTurno` (`PRESENCIAL`, `TELEMEDICINA`), `EstadoTurno` (`DISPONIBLE`, `EN_HOLD`, `CONFIRMADO`, `CANCELADO`) y `TurnoDAO`. `TurnosResource` agrega `POST /api/turnos/disponibilidad`, con la restricción de rol en `ServicioDeTurnos.abrirDisponibilidad` (`@RolesAllowed("PROFESIONAL")`), no en el recurso JAX-RS, consistente con la sección 3.

### 6.2 Sistema legado de la obra social (simulado)

El sistema legado de la obra social no existe, pero la integración tiene que ser SOAP de verdad. Por eso se simula con un endpoint JAX-WS publicado por el CXF de WildFly, que es contra lo que se integrará el Adapter `ServicioDeObrasSociales`.

**WSDL:** `http://localhost:8080/mediconecta/legado/obrasocial?wsdl`

La URL se fija con un mapeo explícito en `web.xml` en vez de depender del nombre que JBossWS asigna por defecto, para que el contrato tenga una dirección estable. El endpoint no tiene `security-constraint`: representa a un tercero, que no conoce a los usuarios de MediConecta.

Las dos primeras operaciones reciben `dni`, `numeroAfiliado` y `codigoPrestacion` y devuelven una `respuestaCobertura` con `autorizado`, `plan`, `porcentajeCobertura`, `arancel`, `copago`, `numeroAutorizacion` y `mensaje`:

- `validarCobertura`: consulta; nunca devuelve número de autorización.
- `autorizarPrestacion`: misma evaluación y, si queda autorizada, un número de autorización (`AUT-<afiliado>-<prestación>`).

Una tercera operación, agregada para SCRUM-97, presenta ante la obra social una prestación ya autorizada: `presentarReclamo(dni, numeroAfiliado, numeroAutorizacion)` devuelve una `respuestaReclamo` con `numeroPresentacion` y `montoReconocido`. A diferencia de las otras dos, no evalúa plan ni cobertura: valida que `numeroAutorizacion` tenga el formato determinista que emite `autorizarPrestacion` y corresponda al afiliado (y al DNI), y reconoce `arancel × porcentaje del plan` (el complemento del copago). Una autorización que no corresponda al afiliado, con formato desconocido o para un afiliado inexistente es, igual que en las otras operaciones, un pedido inválido que vuelve como SOAP Fault `Client`.

Las respuestas son deterministas, con un afiliado por plan para poder mostrar cada caso. El porcentaje depende del plan, y el copago es `arancel × (100 − porcentaje) / 100`. Las prestaciones son `CONSULTA` (arancel 20000.00) y `TELECONSULTA` (15000.00).

| DNI | Afiliado | Plan | Cobertura | Resultado para `CONSULTA` |
|---|---|---|---|---|
| 30111222 | OS-1001 | `PLAN_ALTO` | 100 % | autorizado, copago 0.00 |
| 30333444 | OS-2002 | `PLAN_MEDIO` | 70 % | autorizado, copago 6000.00 |
| 30444555 | OS-3003 | `PLAN_BASICO` | 40 % | autorizado, copago 12000.00 |
| 30555666 | OS-4004 | `SIN_COBERTURA` | 0 % | no autorizado, copago 20000.00 |
| 30777888 | OS-5005 | `PLAN_ALTO` | 100 % | tarda 30 s en responder: existe para demostrar el timeout del Adapter (6.3) |

Se distinguen dos tipos de negativa. "Sin cobertura" es una respuesta válida del legado: el afiliado existe y el plan no cubre. Un afiliado inexistente, un DNI que no corresponde al número de afiliado o una prestación desconocida son en cambio pedidos que el legado no puede evaluar, y vuelven como SOAP Fault de código `Client`, porque el error es del cliente y no del legado; el WSDL no declara un `wsdl:fault` propio. Una falla interna del legado sería, en cambio, un fault `Server`. El Adapter trata la primera como un resultado de negocio, el fault `Client` como un pedido inválido (`400`) y cualquier otra falla como legado no disponible (`503`), ver sección 6.3.

Una consecuencia del caveat de la sección 2.3: `jakarta.jakartaee-api` 11 ya no incluye JAX-WS, así que el `pom.xml` declara `jakarta.xml.ws-api` 4.0 con alcance `provided`. En tiempo de ejecución la implementación la pone WildFly; la dependencia sólo existe para compilar.

### 6.3 ServicioDeObrasSociales: el Adapter

Es el caso de libro del patrón Adapter. Hacia adentro, el sistema pregunta en su propio idioma: `validarCobertura(pacienteId, Prestacion)` y recibe una `Cobertura` (autorizada, porcentaje, copago, número de autorización). Hacia afuera, el legado sólo entiende DNI, número de afiliado, códigos de prestación y sobres SOAP. El componente traduce en los dos sentidos, y ningún tipo del contrato SOAP sale de él.

| Capa | Clase | Qué hace |
|---|---|---|
| Negocio | `ServicioDeObrasSociales` (`@Stateless`) | Fachada: `validarCobertura`, `autorizarPrestacion`, `presentarReclamo`, `registrarAfiliacion` |
| Negocio | `Cobertura`, `Prestacion`, `ResultadoPresentacion` | Objetos de dominio devueltos y enum de prestaciones |
| Negocio | `SistemaDeObraSocial` | El port del Adapter: interfaz propia, sin ningún tipo SOAP, que devuelve `Cobertura` o `ResultadoPresentacion` |
| Negocio | `negocio.soap.SistemaDeObraSocialSoap` (con `ObraSocialLegadoPort` y `RespuestaCoberturaXml`) | Única implementación: el cliente JAX-WS |
| Negocio | `ObraSocialNoDisponibleException` y `ObraSocialNoDisponibleMapper` | El legado no responde; se traduce a `503` |
| Negocio | `SeedDeAfiliaciones` | Afilia al paciente de prueba (OS-2002) en el arranque |
| Datos | `AfiliacionDePaciente`, `AutorizacionDePrestacion` y sus DAO | Tablas `afiliaciones_obra_social` y `autorizaciones_prestacion` |

Los pedidos inválidos usan `comun.negocio.DatosInvalidosException`, la misma que el resto de los componentes, que el mapper del paquete raíz ya traduce a `400`.

**Dónde vive SOAP.** Sólo en el subpaquete `obrassociales.negocio.soap`: el contrato del lado cliente (`ObraSocialLegadoPort`, un SEI escrito a mano con los mismos nombres y namespace que el WSDL), el espejo JAXB de la respuesta y el cliente. El port y su implementación viven en `negocio`, igual que `PasarelaDePagoAdapter` y su cliente REST en pagos: la fachada habla contra la interfaz `SistemaDeObraSocial`, que recibe y devuelve tipos de dominio y falla con excepciones de negocio. Así hay una sola traducción, de SOAP al dominio, y no dos encadenadas. Si mañana la obra social migrara a REST, cambiaría una clase y la fachada no se enteraría; la capa de datos del componente queda sólo con lo que se persiste. Se verifica con los imports: fuera de `externos.obrasocial` y `obrassociales.negocio.soap` ningún archivo importa `jakarta.xml.ws`, `jakarta.jws`, `jakarta.xml.soap` ni `jakarta.xml.bind`.

**Por qué `Service.create` sin descargar el WSDL.** El cliente se construye con el nombre del servicio y las anotaciones del SEI, sin pedirle el WSDL al legado. Si lo pidiera, crear el cliente ya dependería de que el legado esté vivo, y esa descarga ocurriría fuera de los timeouts configurados.

**Timeouts explícitos.** Conexión 2 s y respuesta 5 s, puestos en el request context de cada llamada. Sin ellos, un legado que acepta la conexión y no contesta retiene indefinidamente el hilo del pedido, y con él la transacción y al usuario. URL y timeouts son propiedades de sistema (`mediconecta.obrasocial.url`, `mediconecta.obrasocial.timeoutConexionMs`, `mediconecta.obrasocial.timeoutRespuestaMs`) que se leen en cada llamada, así que se pueden cambiar con `jboss-cli` sin redesplegar.

**Tres desenlaces.** El adaptador clasifica lo que vuelve del legado:

1. Una respuesta, con o sin cobertura, es una `Cobertura`. "Sin cobertura" no es un error: es `autorizada = false`.
2. Un SOAP Fault de código `Client` (`Sender` en SOAP 1.2) significa que el legado entendió el pedido y lo rechaza: `DatosInvalidosException`, `400`. Reintentar no sirve.
3. Cualquier otra cosa, es decir un fault `Server`, una conexión rechazada, un timeout o una respuesta vacía, es `ObraSocialNoDisponibleException`, `503`. Se puede reintentar.

El mensaje de la tercera está pensado para mostrarse tal cual ("El sistema de la obra social no respondió. Intentá de nuevo en unos minutos."); el detalle técnico queda en el log del adaptador y como causa de la excepción, no en el mensaje. Separar el segundo caso del tercero por el código del fault importa: si todo fault fuera "pedido rechazado", una falla interna del legado le diría al usuario que sus datos son inválidos. Una autorización que el legado da por buena pero sin número también cae en el tercero: sin número no sirve para facturar.

**Transacciones y persistencia.** `validarCobertura` es `NOT_SUPPORTED`: no escribe nada y no tiene sentido retener una transacción durante una llamada remota. `autorizarPrestacion` es `REQUIRED`: si el legado autoriza, guarda número y fecha de autorización, porcentaje y copago en `autorizaciones_prestacion`, que es lo que facturación va a necesitar. Al participar de la transacción de quien lo invoca, si la reserva del turno falla después, la fila local se deshace con ella (la autorización remota no, ver más abajo). Las negativas y los errores no generan filas. `presentarReclamo` es `NOT_SUPPORTED`, por la misma razón que `validarCobertura`: no persiste nada local (eso lo hace `facturacion`, sección 9.3) y su llamada remota no tiene por qué arrastrar la transacción de quien la invoca (`ServicioDeFacturacion.procesarReclamo`); además, al correr fuera de esa transacción, su propia excepción de negocio (`@ApplicationException(rollback = true)`) no puede arriesgarse a hacer rollback de una transacción que no es suya.

**Afiliación.** `Usuario` no tiene DNI ni número de afiliado, y no le corresponde: son datos de la relación con la obra social. Viven en `AfiliacionDePaciente`, tabla propia del componente, que referencia al paciente por id y no con `@ManyToOne`, con el mismo criterio que `HistoriaClinica`. `registrarAfiliacion` es `@RolesAllowed("ADMINISTRADOR")`. El resto de la fachada es `@PermitAll`: quién puede reservar o autorizar lo decide el componente que la invoca, que es el que conoce el caso de uso.

**Pruebas.** La lógica se prueba sin contenedor ni red. La fachada, con el port y los DAO como dobles: qué se guarda y cuándo (sólo si se autorizó, y con número), y qué se rechaza (paciente inexistente o que no es paciente, sin afiliación, prestación nula). El adaptador, con un spy que reemplaza `nuevoPuerto`, la única parte que necesita un runtime de JAX-WS, y un `SOAPFault` simulado: traducción de la respuesta, respuesta nula y cada tipo de falla contra la excepción que corresponde. También tienen pruebas el mapper a `503` y el cálculo de copago del simulador. Lo que sí requiere el servidor desplegado (el cableado de CDI y CXF, los timeouts reales y el código `Client` del fault) se verifica en la sección 10.

#### Integración con la reserva (SCRUM-91)

`CoberturaEnLaReserva` (en `obrassociales.negocio`) observa `TurnoEnReserva` con `@Priority(PuntosDeExtension.COBERTURA)`. Corre después de asignar el paciente y antes del hold, en la transacción de la reserva. Elige la `Prestacion` según la modalidad (`PRESENCIAL` es `CONSULTA`; `TELEMEDICINA` es `TELECONSULTA`), llama a `ServicioDeObrasSociales.cotizarReserva` y escribe en los campos de cobertura que ya tenía `Turno`: `coberturaAutorizada`, `coberturaPorcentaje`, `copago` y `numeroAutorizacion`. La entidad y `ServicioDeTurnos.reservarTurno` no cambiaron.

`cotizarReserva` devuelve siempre una `Cobertura`:

| Situación del paciente | Qué pasa | `copago` del turno |
|---|---|---|
| Afiliado con cobertura (alta o parcial) | Pide la autorización al legado y la guarda en `autorizaciones_prestacion` | Lo que informa el legado (0.00, 6000.00, 12000.00 para una consulta) |
| Afiliado a un plan sin cobertura (OS-4004) | El legado no autoriza; no se guarda nada | Valor total que informa el legado |
| Sin obra social registrada | No se llama al legado; se reserva como particular | Valor total: `Prestacion.getArancel()` (20000.00 / 15000.00) |

Sin cobertura el paciente no queda bloqueado: el turno se reserva igual y `copago` es el valor total, que es lo que SCRUM-93 va a cobrar.

**Si el legado no responde, la reserva falla entera (`503`).** Se eligió esto y no reservar como particular con un aviso, por tres motivos:

- La llamada es sincrónica porque el paciente necesita saber antes de confirmar si está cubierto. Reservar sin saberlo le mostraría un copago que puede no ser el real.
- SCRUM-93 cobra `turno.getCopago()` al confirmar: con la opción del aviso, un afiliado podría pagar el valor total por una falla del legado.
- Es atómica sin trabajo extra. `ObraSocialNoDisponibleException` ya es `@ApplicationException(rollback = true)`, así que revierte la reserva (el turno sigue `DISPONIBLE`, sin hold y sin fila de autorización) y llega al mapper como `503` con un mensaje para el paciente. La otra opción obligaba a atrapar la excepción dentro del componente para que la transacción no quedara marcada, y a que SCRUM-93 reverificara antes de cobrar.

El costo es que, con el legado caído, nadie reserva hasta que vuelva; el paciente puede reintentar apenas se recupere.

Límites que siguen vigentes:

- La autorización remota no se compensa. Si la reserva falla después de que el legado autorizó, la fila local se deshace pero la autorización remota queda. Lo mismo si el hold vence o se cancela: `Turno.liberar()` limpia el turno, pero la fila de `autorizaciones_prestacion` y la autorización del legado quedan. Hace falta una baja o idempotencia por turno.
- Un timeout no es un rechazo: si el legado autorizó pero la respuesta se perdió, hay una autorización remota sin fila local.
- La transacción de la reserva puede esperar al legado hasta 7 s (2 de conexión más 5 de respuesta), con la fila del turno bloqueada. El hold arranca recién después, así que esa espera no le resta tiempo al paciente.
- Si el legado rechaza los datos de afiliación (un DNI que no corresponde al afiliado), la reserva responde `400` en vez de reservarse como particular: es un dato mal cargado, no una falta de cobertura.
- Sólo el paciente de prueba tiene afiliación (OS-2002, plan medio). `registrarAfiliacion` existe pero no está expuesta por HTTP.
- La fachada no verifica que el paciente le pertenezca a quien llama: el observador pasa siempre el paciente del turno, nunca un id que venga del cliente.

### 6.4 ServicioDeTelemedicina y el proveedor de video (simulado)

**El tercero.** `externos.video.ProveedorDeVideoExternoResource` simula un proveedor de videoconsultas con `POST /api/externo/salas`: recibe una referencia opaca y la fecha, y devuelve el id de la sala y dos enlaces, `hostUrl` para el anfitrión y `guestUrl` para el invitado. Vive bajo `/api/externo/*`, fuera de la seguridad de la aplicación, por el mismo motivo que la pasarela de pago: un tercero no se autentica con las credenciales de nuestros usuarios. Ningún componente lo importa; se lo alcanza solo por HTTP. Las salas son de Jitsi Meet, que crea la sala cuando alguien entra a la URL, así que los enlaces funcionan sin API key. El nombre de la sala lleva un UUID porque es su único control de acceso. Para probar la falla, responde `503` si la referencia empieza con `caer` o si está activa la propiedad `mediconecta.video.simular-caida`.

**El componente**, en sus tres capas:

- **Negocio.** `ServicioDeTelemedicina` (`@Stateless`, Facade) habla contra `ProveedorDeVideoAdapter`. Su implementación, `ProveedorDeVideoRestClient`, usa el Jakarta REST Client API con DTOs propios (`SalaExternaRequest`/`SalaExternaResponse`, el mismo shape JSON del tercero sin importar sus clases) y traduce `host` a profesional y `guest` a paciente. El turno se lee con `ServicioDeTurnos.obtenerTurno`, nunca de su tabla.
- **Datos.** `SesionVideo` (tabla `sesiones_video`) guarda el turno, el paciente y el profesional por id, el id de la sala, los dos enlaces y el estado.
- **Presentación.** `TelemedicinaResource` expone `POST` y `GET /api/telemedicina/turno/{turnoId}`.

**Quién ve qué.** Cada participante recibe solo su enlace: el profesional el de anfitrión, el paciente el de invitado. `@RolesAllowed` admite PACIENTE y PROFESIONAL, y la regla fina (que el turno sea del caller) se resuelve con el `SessionContext`, como en turnos y pagos. Cualquier otro, administrador incluido, recibe `403`: para la sala el administrador es un tercero más, y ningún caso de uso necesita que entre a una consulta.

**Reglas de `crearSesion`:**

1. Primero se verifica que el caller sea parte del turno. Va antes que todo para que un tercero no pueda averiguar nada del turno por el mensaje de error.
2. Solo un turno de `TELEMEDICINA` lleva sala. Uno presencial responde `409` sin llamar al proveedor.
3. El turno tiene que estar tomado por un paciente. Se acepta `EN_HOLD` y no solo `CONFIRMADO` porque el paso 2/2 va a crear la sala dentro de `confirmarTurno`, antes de que el turno cambie de estado.
4. Es idempotente: si el turno ya tiene sala para ese paciente, se devuelve sin volver a llamar al proveedor.
5. Si la sala era de un paciente anterior (su hold venció y otro tomó el turno), se pide una sala nueva y se reemplaza la anterior: el enlace viejo lo conoce alguien que ya no es parte del turno. Por la misma razón, `obtenerEnlace` resuelve los participantes del turno actual y no de la sesión guardada.

**Proveedor que no responde.** El cliente fija timeouts (3 s de conexión, 5 s de respuesta), porque en el paso 2/2 la llamada va a correr dentro de la confirmación, con la fila del turno bloqueada. Cualquier falla, sea timeout, error HTTP o una respuesta incompleta, sale como `ProveedorDeVideoNoDisponibleException` (`rollback = true`), que se traduce a `503` con `Retry-After`, y no se guarda nada a medias. Qué hace la confirmación del turno ante esa falla se decide en SCRUM-95.

**Pruebas.** Sin contenedor ni red:
- La fachada, con el Adapter, el DAO y `ServicioDeTurnos` como dobles: creación con los dos enlaces, turno presencial, turno sin paciente, paciente ajeno, idempotencia, renovación para un paciente nuevo, proveedor caído, el enlace de cada rol y el `403`.
- La traducción del cliente REST.
- El simulador del proveedor, incluida la caída.

La llamada HTTP real y el cableado de seguridad se verifican con `deploy/smoke-test.sh` y la carpeta 06 de la colección de Postman.

## 7. Autenticación y autorización

La autenticación, pendiente antes, ya está implementada y verificada en ejecución (sección 10). Toda la configuración se declara en una única clase, `ConfiguracionDeSeguridad`, mediante dos anotaciones de Jakarta Security: `@BasicAuthenticationMechanismDefinition`, que establece autenticación HTTP Basic, y `@DatabaseIdentityStoreDefinition`, que apunta el almacén de identidades al mismo datasource de la aplicación. Esa segunda anotación define dos consultas: una recupera el hash de la contraseña a partir del correo, y otra recupera el rol del usuario, que el contenedor publica como grupo.

El valor de resolverlo así es que **no hay código de autenticación escrito por el equipo**. No se validan credenciales a mano ni se gestionan sesiones: el contenedor resuelve la identidad antes de que la petición llegue al componente de negocio, y recién entonces las anotaciones de autorización tienen contra qué decidir. La única pieza propia es un verificador de contraseñas que implementa la interfaz `PasswordHash` de la especificación, necesario porque los hashes existentes usan SHA-256 y no el algoritmo por defecto (sin salt; sección 11).

Cuando el contenedor rechaza una llamada por rol, la `EJBAccessException` se traduce a `403` mediante `AccesoDenegadoMapper`. `ErrorInesperadoMapper` cumple un rol complementario: intercepta cualquier otra excepción, la registra y devuelve `500` genérico, salvo que ya sea una `WebApplicationException`.

`web.xml` protege los tres componentes a nivel grueso; encima, cada operación de negocio tiene su restricción `@RolesAllowed`, más fina:

| Recurso | Rol grueso (`web.xml`) | Operación de negocio | Rol fino (`@RolesAllowed`) |
|---|---|---|---|
| `/api/usuarios` (GET) | ADMINISTRADOR | `listarUsuarios` | ADMINISTRADOR |
| `/api/turnos/*` (todos) | PACIENTE, PROFESIONAL, ADMINISTRADOR | `abrirDisponibilidad` | PROFESIONAL |
| `/api/turnos` (POST) | PACIENTE, ADMINISTRADOR | `reservarTurno`, `confirmarTurno`, `cancelarTurno` | PACIENTE |
| `/api/historias/*` (todos) | PROFESIONAL, PACIENTE, ADMINISTRADOR | `crearHistoria`, `agregarEntrada`, `registrarConsulta` | PROFESIONAL |
| `/api/historias/*` (todos) | PROFESIONAL, PACIENTE, ADMINISTRADOR | `obtenerHistoriaDePaciente`, `listarEntradas` | PROFESIONAL, PACIENTE, ADMINISTRADOR |

Esa tabla, sin embargo, no alcanza para expresar toda la regla de negocio. `@RolesAllowed` solo decide en función del rol del que llama: no ve los argumentos del método. Un paciente con rol PACIENTE podría leer *cualquier* historia clínica, no solo la propia, porque la anotación no compara el `pacienteId` recibido contra la identidad de quien llama. Por eso, donde la regla depende de un dato del método y no solo del rol, la autorización se resuelve programáticamente consultando el `SessionContext` que el contenedor inyecta. `ServicioDeHistoriaClinica` deja pasar sin más control a quien tenga rol PROFESIONAL o ADMINISTRADOR, porque ambos necesitan leer cualquier historia para atender; para el resto, recupera el usuario autenticado a partir del `Principal` y compara su identificador contra el paciente solicitado, rechazando la operación si no coinciden.

El mismo patrón se repite en `ServicioDeTurnos.verificarQueElHoldEsDelCaller`, para que un paciente no confirme ni cancele el hold de otro. En ambos casos, un rol con visibilidad total pasa sin más chequeo; para el resto se compara la identidad del `Principal` contra el dueño del recurso. Al lanzar la misma `EJBAccessException` del rechazo declarativo, ambos caminos terminan en el mismo `AccesoDenegadoMapper` y el cliente recibe `403`, sin distinguir si lo bloqueó una anotación o una regla escrita a mano.

## 8. Patrones de diseño aplicados

### 8.1 DAO (Data Access Object)

**Dónde:** `UsuarioDAO`, `TurnoDAO`, `HistoriaClinicaDAO`, `EntradaClinicaDAO`.

**Problema que resuelve:** separar el acceso a datos (`EntityManager`, queries) de la lógica de negocio; sin esto, cada `Servicio*` mezclaría reglas de negocio con persistencia.

**Alternativa descartada:** inyectar `EntityManager` directamente en el EJB de negocio. Se descartó porque acopla el negocio a la forma concreta de acceder a los datos, dificulta testearlo aislado (no hay un punto único para reemplazarlo por un doble de prueba), y porque distintos componentes necesitan datos de otro sin tocar su tabla (`ServicioDeHistoriaClinica` resuelve si un id es un paciente vía `ServicioDeUsuarios`, nunca contra la tabla `usuarios`).

Cada DAO sigue el mismo patrón: `@Stateless`, `@PersistenceContext` inyectado, y métodos de persistencia como única superficie de acceso a su tabla.

### 8.2 Facade

**Dónde:** `ServicioDeHistoriaClinicaLocal` (interfaz `@Local`) como única puerta de entrada al componente de historia clínica.

**Problema que resuelve:** el componente tiene varias piezas colaborando (`ServicioDeHistoriaClinica`, DAO, `EntradaClinicaFactory`). El propio javadoc de la interfaz lo documenta:

> "Interfaz de negocio del componente ServicioDeHistoriaClinica. Es el único contrato que los consumidores conocen: la implementación, los DAO y el factory quedan ocultos detrás de estas cinco operaciones."

Cualquier otro componente que necesite datos de la historia clínica (por ejemplo, `ServicioDeTelemedicina`) depende únicamente de esa interfaz, no de sus colaboradores internos.

**Alternativa descartada:** exponer directamente los DAO (o la clase sin interfaz). Se descartó porque rompería el encapsulamiento (otro componente podría saltarse validaciones como `validarPaciente`), y porque el flujo transaccional de `registrarConsulta` (`@TransactionAttribute(TransactionAttributeType.REQUIRED)` explícito, que persiste un diagnóstico y varias recetas atómicamente) dejaría de estar garantizado si un consumidor externo invocara los DAO por separado. Si el factory rechaza una receta inválida, el rollback deshace también lo ya persistido en esa llamada.

### 8.3 Factory

**Dónde:** `EntradaClinicaFactory`, que decide qué subclase de `EntradaClinica` instanciar (`Antecedente`, `Diagnostico` o `Receta`) según el tipo del DTO de entrada.

El javadoc de la clase documenta que concentra dos responsabilidades: decidir qué subclase instanciar y validar los campos obligatorios de ese tipo en particular, porque una receta sin medicamento es inválida pero un diagnóstico sin medicamento es perfectamente normal.

**Por qué no un switch disperso:** la alternativa sería un `switch` sobre el tipo de entrada dentro de `ServicioDeHistoriaClinica`, repetido en `agregarEntrada` y `registrarConsulta`. Se descartó porque cada tipo tiene validaciones propias e incompatibles: una receta exige `medicamento`, `dosis` y `diasTratamiento` mayor a cero; un diagnóstico exige `codigoCIE10` y `descripcion`; un antecedente exige `tipoAntecedente` y `detalle`. Concentradas en el Factory, agregar un cuarto tipo se resuelve modificando un solo archivo. Si el texto de una entrada llegara sin control hasta el `INSERT`, el error saldría como `500` de base de datos, cuando en realidad es un dato inválido del cliente y corresponde `400`.

## 9. Decisión de diseño destacada: dónde vive el estado del hold

El estado del hold **no** se guarda únicamente en memoria del bean stateful `ServicioDeTurnos`: se guarda en la entidad `Turno`, en los campos `estado` (`EN_HOLD`) e `inicioHold`.

El bean mantiene un campo en memoria, `turnoEnCursoId`, pero es solo una referencia de conveniencia, no la fuente de verdad: `confirmarTurno` y `cancelarTurno` vuelven a buscar el `Turno` en la base de datos por el id recibido, sin leer `turnoEnCursoId`.

Esto es deliberado, por dos razones:

1. **El hold debe sobrevivir a un reinicio del contenedor.** Si `EN_HOLD` viviera solo en memoria del bean, un reinicio de WildFly perdería silenciosamente todos los holds activos, dejando turnos reservados pero sin temporizador corriendo para liberarlos.
2. **El paciente puede confirmar desde otra solicitud HTTP.** Un bean `@Stateful` está atado a una instancia concreta del contenedor; si la confirmación llega en una solicitud distinta de la que generó el hold, como ocurre cuando el contenedor inyecta una nueva instancia por request, ningún estado en memoria del bean anterior estaría disponible.

El `@Stateful` se limita a sostener la referencia conversacional y delegar en el `@Singleton` que expira el hold (sección 6.1); lo que debe sobrevivir entre llamadas está en la base de datos, no en memoria. Esto también explica por qué el timer, aunque hoy vive en `ExpiradorDeHolds`, sigue sin resolver un reinicio del contenedor (sección 11).

### 9.1 Publicación de `TurnoConfirmado`

Al confirmar un turno, `ServicioDeTurnos.confirmarTurno` publica un mensaje en el tópico JMS `java:/jms/topic/TurnoConfirmado`, inyectando `JMSContext` y resolviendo el tópico con `@Resource(lookup = ...)`.

La publicación ocurre **dentro de la misma transacción JTA** que la confirmación, no después: el `JMSContext` inyectado se enlista en esa transacción como cualquier otro recurso transaccional (igual que el `EntityManager`). Esto da una garantía en los dos sentidos:

- Si `confirmarTurno` hace rollback (turno sin hold, hold de otro paciente), el mensaje nunca se envía, porque nunca se llega a publicar.
- Si la publicación fallara, la excepción no controlada aborta la transacción y la confirmación tampoco queda persistida — no puede quedar un turno `CONFIRMADO` en la base sin que el evento haya salido, ni viceversa.

El mensaje es un `MapMessage` (no JSON) con cuatro campos: `turnoId`, `pacienteId`, `profesionalId` (los tres como `long`) y `fechaHora` (como `String` ISO-8601, porque `MapMessage` no admite `LocalDateTime`). Es un formato provisorio: falta acordarlo con quien implemente `ServicioDeNotificaciones` (sección 4, aún no implementado).

`ServicioDeTurnos` no importa ninguna clase de `ServicioDeNotificaciones`: publica por nombre JNDI del tópico, sin saber si hay algún consumidor suscripto. La configuración del propio tópico en Artemis (WildFly) es una dependencia externa a este cambio, no algo que resuelva el código de la aplicación.

### 9.2 Puntos de extensión: eventos CDI sincrónicos

Cuatro integraciones se enganchan al flujo de turnos: la validación de cobertura (SCRUM-91), el cobro del copago (SCRUM-93), la sala de video (SCRUM-95) y el encolado del reclamo a la obra social. Había dos formas de conectarlas.

| | Evento CDI sincrónico (elegida) | Llamadas directas a cada fachada |
|---|---|---|
| Acoplamiento | `ServicioDeTurnos` no importa a ninguno de los componentes: dispara el evento y no sabe quién lo observa. | `ServicioDeTurnos` inyecta e importa las cuatro fachadas. |
| Trabajo en paralelo | Cada card agrega un observador en su propio componente; nadie más toca `confirmarTurno`. | Cada card edita el mismo método, con conflictos de merge entre ramas. |
| Orden | Lo fija `@Priority` sobre el observador, con las constantes de `PuntosDeExtension`. Es menos visible: hay que ir a esa clase para verlo. | Es el orden de las líneas. |
| Resultados | El observador escribe sobre el `Turno` que viaja en el evento (cobertura, copago). Es implícito. | Valores de retorno. |
| Transacción y errores | Un observador `@Observes` corre en el mismo hilo y la misma transacción JTA; su excepción sale por `fire()` y revierte todo. | Idéntico. |

La transaccionalidad es la misma en las dos; la diferencia es quién conoce a quién. Se eligió el evento porque el problema concreto de este sprint era que cuatro ramas tocaran `Turno` y `ServicioDeTurnos` a la vez, y porque mantiene la dirección de dependencias que el sistema ya usaba con `TurnoConfirmado`: el componente de turnos anuncia, los demás reaccionan.

Hay dos eventos:

- `TurnoEnReserva`, dentro de `reservarTurno`, después de asignar el paciente y antes de retener el turno. Si la cobertura rechaza, no queda ningún hold que liberar.
- `TurnoEnConfirmacion`, dentro de `confirmarTurno`, después de validar el hold y antes de marcar el turno `CONFIRMADO` y cancelar su temporizador. Si un observador falla, el turno sigue `EN_HOLD`, el temporizador sigue vivo y el paciente puede reintentar.

`PuntosDeExtension` fija el orden: `COBRO_COPAGO` (100), `SALA_DE_VIDEO` (200), `RECLAMO` (300). El cobro va antes que la sala por decisión de producto, y los huecos permiten intercalar un paso sin renumerar. La sala se crea en la confirmación y nunca en la reserva: un hold que vence no tiene que dejar salas creadas.

Tres reglas para quien agregue un observador:

1. **Sincrónico, siempre.** `@Observes`, nunca `@ObservesAsync`: un observador asincrónico corre en otro hilo y fuera de la transacción, así que su falla ya no podría impedir la confirmación.
2. **`MANDATORY`.** El observador se declara `@Transactional(TxType.MANDATORY)` (`@TransactionAttribute` sólo rige en los EJB, y un observador CDI no lo es): su paso solo tiene sentido dentro de la transacción del turno, y si alguien disparara el evento fuera de una, el contenedor lo rechaza en vez de ejecutarlo suelto.
3. **Errores con rollback.** Para cortar el flujo se lanza una excepción `@ApplicationException(rollback = true)`, que atraviesa `ServicioDeTurnos` sin envolverse y llega intacta a presentación.

Un límite a tener presente: el rollback JTA solo alcanza a los recursos transaccionales (base de datos, JMS). Un pedido REST a un sistema externo, como la pasarela de pago o el proveedor de video, no se deshace si un paso posterior falla. Cada card que llame a un sistema externo tiene que decidir cómo compensarlo.

Esto no reemplaza al tópico `TurnoConfirmado` (sección 9.1): los eventos CDI son para los pasos que deciden si el turno se confirma; el tópico JMS, para lo que reacciona después, fuera de la transacción del paciente.

### 9.3 Facturación: por qué un segundo suscriptor del tópico, y no `PuntosDeExtension.RECLAMO`

El encolado del reclamo a la obra social aparece en la tabla de la sección 9.2 como el tercer paso de `TurnoEnConfirmacion` (`PuntosDeExtension.RECLAMO`, prioridad 300), pero `ServicioDeFacturacion` finalmente **no** se implementó como observador de ese evento. Se lo conectó como un segundo consumidor independiente del tópico `TurnoConfirmado` (`TurnoConfirmadoFacturacionMDB`; `NotificacionMDB`, sección 4, es el primero), publicado por `ServicioDeTurnos.confirmarTurno` dentro de su propia transacción (sección 9.1).

Dos razones, ambas de aislamiento entre componentes:

1. **`facturacion` no toca código de `turnos`.** Un observador CDI vive en el componente que lo declara, pero igual exige que `ServicioDeTurnos` dispare el evento `TurnoEnConfirmacion` en el punto correcto y que `PuntosDeExtension` reserve el número de prioridad; nada de eso cambia con el tópico, pero evita que esta tarjeta edite o revise el flujo transaccional de confirmación mientras otras tarjetas (SCRUM-91, 93, 95) hacen lo mismo en paralelo sobre el mismo evento.
2. **El reclamo solo tiene sentido para una confirmación que ya es un hecho.** Un observador `@Observes TurnoEnConfirmacion` corre *antes* de que `confirmarTurno` marque el turno `CONFIRMADO` (sección 9.2): si la transacción hace rollback por otro motivo posterior, un observador ya habría encolado un reclamo para un turno que nunca se confirmó. El tópico, en cambio, se publica después de que la transacción de confirmación cerró con éxito (sección 9.1): no hay forma de recibir `TurnoConfirmado` para un turno que no esté `CONFIRMADO`.

`PuntosDeExtension.RECLAMO` queda sin usar, a propósito: no se borra la constante, porque documenta la prioridad relativa que se había previsto para este paso frente a los otros tres, aunque la implementación final haya tomado un camino distinto.

**Reintentos, cola muerta y revisión manual.** `ReclamoMDB` consume `java:/jms/queue/ReclamosFacturacion` (cola punto a punto, a diferencia del tópico: el reclamo lo tiene que procesar exactamente un consumidor una sola vez). El puerto `CanalDeReclamos` distingue dos tipos de falla: `CanalDeReclamosNoDisponibleException` es transitoria (el canal no respondió) y `ReclamoRechazadoException` es un rechazo determinístico de la obra social. Una falla transitoria con intentos disponibles registra el intento en una transacción `REQUIRES_NEW` propia (mismo patrón que `PagoDAO.guardarEnNuevaTransaccion`, para que sobreviva el rollback) y relanza, dejando que el contenedor no confirme el mensaje y Artemis lo reentregue según la política de `mediconecta-setup.cli` (hasta 5 intentos, 2s→30s de backoff, después `ReclamosFacturacionDLQ`). En el intento número 5 (`ServicioDeFacturacion.MAX_INTENTOS`, que tiene que coincidir con `max-delivery-attempts` del script), o ante un rechazo definitivo desde el primer intento, el reclamo pasa a `EN_REVISION_MANUAL` y el método ya no relanza: no tiene sentido pedirle a Artemis una reentrega que la cola ya no va a dar, y reintentar un rechazo determinístico tampoco cambiaría el resultado. Un reclamo ya `ENVIADO` o `EN_REVISION_MANUAL` se ignora, para que una reentrega (por ejemplo, si el contenedor se reinicia antes de confirmar un mensaje ya procesado) no lo reabra.

**`@RunAs`.** `ServicioDeFacturacion.registrarReclamo` necesita leer el turno con `ServicioDeTurnos.obtenerTurno`, que hereda el `@RolesAllowed({PACIENTE, PROFESIONAL, ADMINISTRADOR})` de clase (sección 7). Un `@MessageDriven` no tiene un `Principal` autenticado: sin nada más, esa llamada falla con `EJBAccessException`. `TurnoConfirmadoFacturacionMDB` se anota `@RunAs(ServicioDeUsuarios.ROL_ADMINISTRADOR)`, que le da al bean una identidad propagada con ese rol para las llamadas que hace a otros EJB.

Verificado en este WildFly 41 (sección 10), `@RunAs` solo no alcanzó: Artemis entregaba el mensaje y el propio `onMessage` quedaba rechazado con `EJBAccessException` ("... is not allowed"), *antes* de llegar siquiera a `obtenerTurno`. La causa es la misma que ya documenta la sección 6 para `ServicioDeUsuarios`: con `default-missing-method-permissions-deny-access` en `true` (el valor por defecto del subsistema `ejb3` de WildFly), un bean que lleva **cualquier** anotación de `jakarta.annotation.security` (acá, `@RunAs`) pero ningún `@RolesAllowed`/`@PermitAll` explícito sobre el método invocado queda denegado por defecto. Agregar `@PermitAll` junto a `@RunAs` lo resolvió, sin tocar ninguna configuración de Elytron ni del script `mediconecta-setup.cli`: es una anotación de la aplicación, no una configuración del servidor.

**El canal real.** `CanalDeReclamos` tiene una única implementación, `CanalDeReclamosSoap` (bean CDI simple, `@ApplicationScoped`, no EJB: no necesita ninguna transacción propia porque `ServicioDeObrasSociales.presentarReclamo` ya es `NOT_SUPPORTED`), que presenta el reclamo llamando a esa fachada (sección 6.3) y traduce sus dos excepciones de negocio a las del puerto de facturación: `DatosInvalidosException` (la autorización no corresponde al afiliado, o es desconocida) se vuelve `ReclamoRechazadoException` (rechazo definitivo, directo a `EN_REVISION_MANUAL`), y `ObraSocialNoDisponibleException` (el legado no respondió) se vuelve `CanalDeReclamosNoDisponibleException` (transitorio, con reintentos). Reemplaza a `CanalDeReclamosPendiente`, que se eliminó: con una sola implementación del puerto no hay ambigüedad de CDI que resolver.

## 10. Verificación en ejecución

El sistema se desplegó en WildFly 41 con PostgreSQL 18 y se verificó endpoint por endpoint, con el usuario autenticado que corresponde a cada caso.

| Prueba | Resultado observado |
|---|---|
| Acceso anónimo a una historia clínica | `401` |
| Credencial incorrecta | `401` |
| ADMINISTRADOR contra `GET /api/usuarios` | `200` |
| PROFESIONAL o PACIENTE contra `GET /api/usuarios` | `403` |
| PACIENTE intenta abrir disponibilidad | `403` |
| PROFESIONAL intenta confirmar el hold de un paciente | `403` |
| Reservar un turno | pasa a `EN_HOLD`, con `inicioHold` seteado |
| Confirmar el turno reservado | pasa a `CONFIRMADO` |
| Reservar un turno presencial como el paciente de prueba (OS-2002) | `201`, `EN_HOLD`, `coberturaPorcentaje` 70, `copago` 6000.00 y `numeroAutorizacion` `AUT-OS-2002-CONSULTA` |
| Reservar una franja de telemedicina como el mismo paciente | `copago` 4500.00 y `AUT-OS-2002-TELECONSULTA` |
| Reservar con un paciente recién registrado, sin obra social | `EN_HOLD` con `coberturaAutorizada` `false`, porcentaje 0 y `copago` 20000.00 (el valor total); sin llamada al legado (esto último, cubierto por el test de unidad de `cotizarReserva`) |
| Reservar con la URL del legado apuntando a un puerto cerrado | `503` "El sistema de la obra social no respondió. Intentá de nuevo en unos minutos." a los 79 ms; el turno sigue `DISPONIBLE` y vuelve a figurar en la disponibilidad |
| `GET /mediconecta/legado/obrasocial?wsdl` desde el navegador | `200`, WSDL con `validarCobertura` y `autorizarPrestacion` |
| `autorizarPrestacion` para `CONSULTA` con los cuatro afiliados de prueba | `100 %`/`0.00`, `70 %`/`6000.00`, `40 %`/`12000.00` autorizados; `0 %`/`20000.00` no autorizado |
| `validarCobertura` con un afiliado inexistente, un DNI que no corresponde o una prestación desconocida | `500` HTTP con un SOAP Fault de código `Client`; el WSDL ya no declara `wsdl:fault` |
| `ServicioDeObrasSociales.validarCobertura` del paciente de prueba (OS-2002) | `Cobertura` autorizada, 70 %, copago 6000.00, sin número; nada persistido |
| `ServicioDeObrasSociales.autorizarPrestacion` del mismo paciente | número `AUT-OS-2002-CONSULTA` y una fila en `autorizaciones_prestacion` con fecha, 70 % y 6000.00 |
| `autorizarPrestacion` con afiliación OS-4004 / OS-9999 | no autorizada y sin fila persistida / `400` "La obra social rechazó el pedido: No existe el afiliado OS-9999" |
| `autorizarPrestacion` con afiliación OS-5005 (legado lento) | `503` "El sistema de la obra social no respondió. Intentá de nuevo en unos minutos." a los 5,02 s, sin fila persistida |
| URL del legado apuntando a un puerto cerrado | el mismo `503`, a los 21 ms |
| Hold sin confirmar, transcurrido el tiempo de expiración | a los 303 segundos, el turno volvió a `DISPONIBLE`, con `paciente` e `inicioHold` en `null`, y el log del contenedor registró "Hold vencido: el turno 2 vuelve a estar disponible" |

Como `ServicioDeObrasSociales` no se expone por HTTP, esas filas (y los `400` y `503`) se observaron a través de un recurso JAX-RS provisorio que invocaba la fachada sin capturar excepciones, para que las tradujeran los mappers reales. Se retiró del código una vez hecha la verificación, y las tablas se comprobaron directamente en PostgreSQL.

Este último resultado es, junto con la corrección de la sección 6.1, la evidencia más fuerte de que el mecanismo de timers funciona como se lo diseñó: nadie liberó el turno manualmente, lo hizo el `@Timeout` de `ExpiradorDeHolds` por su cuenta, sin ninguna solicitud HTTP en curso.

El repositorio incluye dos scripts que automatizan esta verificación: `deploy/mediconecta-setup.cli` (instala el driver JDBC de PostgreSQL como módulo de WildFly y crea el datasource) y `deploy/smoke-test.sh` (automatiza buena parte de la tabla anterior, incluidos los casos SOAP y la espera para confirmar la expiración real del hold).

Un hallazgo de configuración que costó diagnosticar: la integración de Jakarta Security (Soteria) con Elytron en WildFly requiere fijar `integrated-jaspi=false` en el `application-security-domain` de Undertow. Sin ese ajuste, Elytron intenta reautorizar una identidad que Soteria ya estableció, y **todos** los endpoints protegidos responden `500` con `"ELY01177: Authorization failed"`, incluso con credenciales y rol correctos. La pista que distingue esto de un error de credenciales real: una contraseña incorrecta sigue devolviendo `401` con normalidad, mientras que una credencial correcta con permisos correctos falla igual con `500`.

### 10.1 Facturación: verificación funcional del reclamo y sus reintentos

Desplegado el WAR con `ServicioDeFacturacion` (sección 9.3), con un turno confirmado con cobertura autorizada (seteada directamente en la base, porque la validación real de cobertura es SCRUM-91, todavía no implementada):

| Escenario | Esperado | Observado |
|---|---|---|
| Confirmar un turno con cobertura autorizada | se crea un `Reclamo` `PENDIENTE` | `Reclamo` creado, `PENDIENTE`, una sola vez |
| Reintentos contra el canal stub (siempre indisponible) | 5 intentos con backoff creciente (2s, 4s, 8s, 16s/30s) | intentos y `actualizadoEn` en `15:40:58` (intento 2, +2.0s), `15:41:02` (intento 3, +4.0s), `15:41:10` (intento 4, +8.0s), `15:41:26` (intento 5, +16.0s) — coincide con el backoff configurado en `mediconecta-setup.cli` |
| Estado final tras el intento 5 | `EN_REVISION_MANUAL`, `intentos=5`, `ultimoError` seteado | `EN_REVISION_MANUAL`, `intentos=5`, `ultimoError="El canal de reclamos a la obra social todavia no esta disponible (pendiente SCRUM-90)."` |
| `ReclamosFacturacionQueue` / `ReclamosFacturacionDLQ` tras el intento 5 | ambas en 0 mensajes (el intento 5 no relanza, se confirma el mensaje) | `count-messages` → `0` y `0` |
| Confirmar el mismo turno una segunda vez | `409`, sin crear un segundo reclamo | `409`, sigue existiendo un solo `Reclamo` para ese turno |
| Confirmar un turno sin cobertura autorizada | no se crea ningún reclamo | 0 filas en `reclamos` para ese turno |
| `GET /api/reclamos` como ADMINISTRADOR | `200`, lista el reclamo con su estado | `200`, el reclamo `EN_REVISION_MANUAL` aparece con intentos y último error |
| `GET /api/reclamos` como PACIENTE | `403` | `403` |
| `GET /api/reclamos` como anónimo | `401` | `401` |
| `bash deploy/smoke-test.sh --rapido` | todo en verde | 15 correctas, 0 fallidas |

**Hallazgo:** la primera corrida (antes de agregar `@PermitAll`, ver sección 9.3) falló de otra forma de la prevista: no fue `obtenerTurno` el que rechazó por falta de rol, sino el propio `onMessage` del MDB, denegado por el contenedor antes de llegar al cuerpo del método. El mensaje `TurnoConfirmado` de esa corrida agotó los reintentos de la política por defecto del tópico (10 intentos, sin backoff) y terminó en la `DLQ` general de Artemis (`count-messages` → `1`), no en `ReclamosFacturacionDLQ` (esa es la de la cola de reclamos, que nunca llegó a recibir el mensaje porque `registrarReclamo` nunca se ejecutó). Con `@PermitAll` agregado, se repitió el turno completo desde cero y dio la tabla de arriba.

## 11. Estado actual y trabajo pendiente

Las seis brechas que este documento listaba como abiertas están resueltas, y cada una se cerró dentro de la capa a la que pertenecía.

**Reserva concurrente.** La capa de datos expone una búsqueda que toma un lock pesimista de escritura sobre la fila del turno, y la usan las tres operaciones que mutan estado; las consultas de sólo lectura siguen sin lock, porque no deciden nada. Dos pacientes que reserven el mismo turno en el mismo instante quedan serializados: el segundo lee el estado ya actualizado y su validación falla como corresponde. Dónde ponerlo importa tanto como el lock en sí: el bloqueo es una propiedad del acceso a datos, así que vive en el DAO y el negocio lo pide por intención, no por mecanismo.

**Expiración de holds tras un reinicio.** El temporizador por turno sigue existiendo, porque es el que demuestra el ciclo de vida gestionado por el contenedor y libera con precisión al vencimiento, pero no es persistente: un reinicio se lleva la tarea programada aunque la fila sobreviva. Se le sumó un barrido periódico que cada minuto recorre la base buscando holds vencidos. Los dos conviven a propósito y cubren riesgos distintos: el temporizador da precisión, el barrido da durabilidad.

**Errores de negocio traducidos a HTTP.** El componente de turnos define dos excepciones propias, una para datos inválidos y otra para conflictos de estado, ambas anotadas para que el contenedor revierta la transacción; la capa de presentación las traduce a `400` y `409`. La traducción vive en presentación de forma deliberada: el código HTTP es un detalle del transporte y el componente de negocio no tiene por qué saber que lo invocan por REST. Es el criterio que ya regía en historia clínica, así que ahora los dos componentes responden igual ante el mismo tipo de error. Se agregó además la validación que faltaba al abrir una franja: sin horario, o con horario en el pasado, ya no se crea.

**Contraseñas.** El esquema pasó de un resumen SHA-256 sin salt a una derivación PBKDF2 con HMAC-SHA256, salt aleatorio por usuario y ciento veinte mil iteraciones, con comparación en tiempo constante. Ataca dos problemas distintos: sin salt, dos usuarios con la misma contraseña producían el mismo resumen y una tabla precomputada los revertía sin esfuerzo; y SHA-256 está diseñado para ser rápido, lo contrario de lo que conviene acá. El formato guardado incluye el número de iteraciones, de modo que subir el costo más adelante no invalide lo existente. La superficie pública de la clase no cambió, así que el adaptador hacia el contrato de Jakarta Security siguió funcionando sin tocarse: es la ventaja concreta de haber encapsulado la decisión en un solo lugar.

**Nombre del artefacto.** El `artifactId` pasó de `mediconecta-usuarios` a `mediconecta`. El nombre viejo describía el proyecto cuando tenía un solo componente y, con tres implementados, decía algo falso sobre el alcance.

**Contraseñas del sembrado inicial.** Ninguna queda escrita en el código: cada usuario inicial toma la suya de una variable de entorno y, si no está definida, el arranque genera una al azar y la registra una sola vez. Una contraseña fija en el fuente es idéntica en todas las instalaciones y queda publicada en el repositorio.

Lo que sigue abierto es alcance, no deuda: no hay interfaz de usuario, la verificación de integración contra el sistema desplegado sigue siendo la principal (las pruebas de unidad, con JUnit y Mockito, cubren por ahora el flujo de turnos), y los tres componentes conviven en un único módulo Maven, que es lo correcto mientras se desplieguen juntos.

## 12. Uso de inteligencia artificial generativa

En cumplimiento de lo solicitado por la cátedra, se deja constancia del uso de herramientas de inteligencia artificial generativa durante el desarrollo de este trabajo. Se utilizó asistencia de IA para tareas de documentación (incluyendo la redacción de este documento técnico a partir de las decisiones y el código provistos por el equipo, y su actualización posterior contra el estado real del repositorio), revisión de código y generación de boilerplate repetitivo (por ejemplo, getters/setters, estructuras de DTO). Las decisiones de arquitectura, la elección del stack tecnológico y la implementación del código fueron desarrolladas por el equipo, y cada integrante puede defender individualmente cualquier parte del trabajo entregado.
