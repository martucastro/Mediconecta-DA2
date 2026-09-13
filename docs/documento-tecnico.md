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

El sistema se organiza en tres capas, con una regla de dependencia estricta: cada capa solo puede depender de la capa inmediatamente inferior, y las reglas de negocio viven exclusivamente en la capa de negocio.

| Capa | Responsabilidad | Tecnologías | Qué NO hace |
|---|---|---|---|
| Presentación | Recibe la interacción externa, valida formato de entrada, serializa la respuesta | React (SPA), recursos JAX-RS, endpoints JAX-WS | No decide nada del dominio; no contiene reglas de negocio ni de autorización |
| Negocio | Reglas del dominio, límites transaccionales, seguridad por rol | EJB (`@Stateless`, `@Stateful`, `@Singleton`), CDI, JTA, Jakarta Security | No conoce detalles de SQL ni de la SPA |
| Datos | Traduce objetos de dominio a filas de base de datos | Patrón DAO, JPA/Hibernate, PostgreSQL | No contiene reglas de negocio |

A diferencia de una entrega anterior, esta separación ya no depende del sufijo de una clase (`*Resource`, `Servicio*`, `*DAO`) conviviendo en un mismo paquete: hoy es una estructura de paquetes real. Bajo el paquete raíz `ar.edu.uade.da2.mediconecta`, cada uno de los tres componentes (`usuarios`, `turnos`, `historiaclinica`) se divide en tres subpaquetes homónimos de las capas: `presentacion`, `negocio` y `datos`. Son nueve subpaquetes, tres componentes por tres capas.

La regla de dependencia queda así verificable con sólo mirar los imports: una clase de `datos` que importara algo de `presentacion` sería visible de inmediato como una violación de la arquitectura, cosa que la convención de nombres anterior no permitía detectar.

En el paquete raíz quedan tres clases fuera de esa estructura: `ApiActivator` (`Application` de JAX-RS) y los `ExceptionMapper` transversales `AccesoDenegadoMapper` y `ErrorInesperadoMapper`. Es deliberado: son infraestructura JAX-RS que atraviesa los tres componentes por igual (`AccesoDenegadoMapper` traduce a `403` cualquier `EJBAccessException`, venga del componente que venga), y ubicarlos dentro de un componente sugeriría una pertenencia que no existe.

## 4. Los ocho componentes del sistema

| # | Componente | Tipo de EJB / integración | Responsabilidad | Estado |
|---|---|---|---|---|
| 1 | ServicioDeUsuarios | `@Stateless` | Registro, autenticación, perfiles | Implementado |
| 2 | ServicioDeTurnos | `@Stateful` (con `ExpiradorDeHolds` como `@Singleton` colaborador) | Disponibilidad, reserva, cancelación, hold de aproximadamente 5 minutos | Implementado |
| 3 | ServicioDeHistoriaClinica | `@Stateless` | Antecedentes, diagnósticos, recetas | Implementado |
| 4 | ServicioDeObrasSociales | Adapter vía SOAP | Validación de cobertura contra sistema legado | No implementado |
| 5 | ServicioDePagos | REST | Cobro de copagos contra pasarela de pago | No implementado |
| 6 | ServicioDeTelemedicina | REST | Integración con proveedor de video | No implementado |
| 7 | ServicioDeNotificaciones | `@MessageDriven` (tópico JMS) | Notificación asincrónica de eventos | No implementado |
| 8 | ServicioDeFacturacion | `@MessageDriven` (cola JMS) | Facturación a obras sociales/prepagas | No implementado |

Que los tres componentes ya convivan integrados y desplegados juntos fue justamente lo que permitió detectar y corregir la incompatibilidad entre `@Stateful` y `TimerService` descrita en la sección 6.1: un problema que solo se manifiesta con el sistema desplegado, no en aislamiento.

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

Los pasos 1 a 3 y 6 (parcialmente, sección 11) están respaldados por código real, verificado en la sección 10. Los pasos 4, 5 y 7 describen el diseño previsto para los componentes aún no implementados (secciones 4 y 11).

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

Entidades: `Turno` (`paciente`/`profesional` `@ManyToOne`, `estado`, `inicioHold`), `EstadoTurno` (`DISPONIBLE`, `EN_HOLD`, `CONFIRMADO`, `CANCELADO`) y `TurnoDAO`. `TurnosResource` agrega `POST /api/turnos/disponibilidad`, con la restricción de rol en `ServicioDeTurnos.abrirDisponibilidad` (`@RolesAllowed("PROFESIONAL")`), no en el recurso JAX-RS, consistente con la sección 3.

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
| Hold sin confirmar, transcurrido el tiempo de expiración | a los 303 segundos, el turno volvió a `DISPONIBLE`, con `paciente` e `inicioHold` en `null`, y el log del contenedor registró "Hold vencido: el turno 2 vuelve a estar disponible" |

Este último resultado es, junto con la corrección de la sección 6.1, la evidencia más fuerte de que el mecanismo de timers funciona como se lo diseñó: nadie liberó el turno manualmente, lo hizo el `@Timeout` de `ExpiradorDeHolds` por su cuenta, sin ninguna solicitud HTTP en curso.

El repositorio incluye dos scripts que automatizan esta verificación: `deploy/mediconecta-setup.cli` (instala el driver JDBC de PostgreSQL como módulo de WildFly y crea el datasource) y `deploy/smoke-test.sh` (automatiza buena parte de la tabla anterior, incluyendo la espera para confirmar la expiración real del hold).

Un hallazgo de configuración que costó diagnosticar: la integración de Jakarta Security (Soteria) con Elytron en WildFly requiere fijar `integrated-jaspi=false` en el `application-security-domain` de Undertow. Sin ese ajuste, Elytron intenta reautorizar una identidad que Soteria ya estableció, y **todos** los endpoints protegidos responden `500` con `"ELY01177: Authorization failed"`, incluso con credenciales y rol correctos. La pista que distingue esto de un error de credenciales real: una contraseña incorrecta sigue devolviendo `401` con normalidad, mientras que una credencial correcta con permisos correctos falla igual con `500`.

## 11. Estado actual y trabajo pendiente

Las seis brechas que este documento listaba como abiertas están resueltas, y cada una se cerró dentro de la capa a la que pertenecía.

**Reserva concurrente.** La capa de datos expone una búsqueda que toma un lock pesimista de escritura sobre la fila del turno, y la usan las tres operaciones que mutan estado; las consultas de sólo lectura siguen sin lock, porque no deciden nada. Dos pacientes que reserven el mismo turno en el mismo instante quedan serializados: el segundo lee el estado ya actualizado y su validación falla como corresponde. Dónde ponerlo importa tanto como el lock en sí: el bloqueo es una propiedad del acceso a datos, así que vive en el DAO y el negocio lo pide por intención, no por mecanismo.

**Expiración de holds tras un reinicio.** El temporizador por turno sigue existiendo, porque es el que demuestra el ciclo de vida gestionado por el contenedor y libera con precisión al vencimiento, pero no es persistente: un reinicio se lleva la tarea programada aunque la fila sobreviva. Se le sumó un barrido periódico que cada minuto recorre la base buscando holds vencidos. Los dos conviven a propósito y cubren riesgos distintos: el temporizador da precisión, el barrido da durabilidad.

**Errores de negocio traducidos a HTTP.** El componente de turnos define dos excepciones propias, una para datos inválidos y otra para conflictos de estado, ambas anotadas para que el contenedor revierta la transacción; la capa de presentación las traduce a `400` y `409`. La traducción vive en presentación de forma deliberada: el código HTTP es un detalle del transporte y el componente de negocio no tiene por qué saber que lo invocan por REST. Es el criterio que ya regía en historia clínica, así que ahora los dos componentes responden igual ante el mismo tipo de error. Se agregó además la validación que faltaba al abrir una franja: sin horario, o con horario en el pasado, ya no se crea.

**Contraseñas.** El esquema pasó de un resumen SHA-256 sin salt a una derivación PBKDF2 con HMAC-SHA256, salt aleatorio por usuario y ciento veinte mil iteraciones, con comparación en tiempo constante. Ataca dos problemas distintos: sin salt, dos usuarios con la misma contraseña producían el mismo resumen y una tabla precomputada los revertía sin esfuerzo; y SHA-256 está diseñado para ser rápido, lo contrario de lo que conviene acá. El formato guardado incluye el número de iteraciones, de modo que subir el costo más adelante no invalide lo existente. La superficie pública de la clase no cambió, así que el adaptador hacia el contrato de Jakarta Security siguió funcionando sin tocarse: es la ventaja concreta de haber encapsulado la decisión en un solo lugar.

**Nombre del artefacto.** El `artifactId` pasó de `mediconecta-usuarios` a `mediconecta`. El nombre viejo describía el proyecto cuando tenía un solo componente y, con tres implementados, decía algo falso sobre el alcance.

**Contraseñas del sembrado inicial.** Ninguna queda escrita en el código: cada usuario inicial toma la suya de una variable de entorno y, si no está definida, el arranque genera una al azar y la registra una sola vez. Una contraseña fija en el fuente es idéntica en todas las instalaciones y queda publicada en el repositorio.

Lo que sigue abierto es alcance, no deuda: no hay interfaz de usuario, la verificación es de integración contra el sistema desplegado y no de unidad, y los tres componentes conviven en un único módulo Maven, que es lo correcto mientras se desplieguen juntos.

## 12. Uso de inteligencia artificial generativa

En cumplimiento de lo solicitado por la cátedra, se deja constancia del uso de herramientas de inteligencia artificial generativa durante el desarrollo de este trabajo. Se utilizó asistencia de IA para tareas de documentación (incluyendo la redacción de este documento técnico a partir de las decisiones y el código provistos por el equipo, y su actualización posterior contra el estado real del repositorio), revisión de código y generación de boilerplate repetitivo (por ejemplo, getters/setters, estructuras de DTO). Las decisiones de arquitectura, la elección del stack tecnológico y la implementación del código fueron desarrolladas por el equipo, y cada integrante puede defender individualmente cualquier parte del trabajo entregado.
