---
title: "MediConecta - Documento Técnico"
---

# MediConecta

**Documento técnico - Entrega Obligatoria N.º 1**

**Materia:** Desarrollo de Aplicaciones II
**Comisión:** Lunes TM
**Entrega:** Entrega Obligatoria N.º 1
**Fecha:** 14/09/2026

**Integrantes:** [completar]

---

## 1. Introducción

MediConecta es una plataforma que conecta pacientes con profesionales de la salud independientes y con clínicas, para la gestión de turnos presenciales y de telemedicina, incluyendo la facturación a obras sociales y prepagas. Este documento describe la arquitectura elegida para el sistema, el stack tecnológico seleccionado, los componentes que lo conforman, los patrones de diseño aplicados y el fundamento de cada decisión relevante.

El sistema se divide en ocho componentes de negocio. Al momento de esta entrega, tres de ellos ya están implementados e integrados en el repositorio: `ServicioDeUsuarios`, `ServicioDeTurnos` y `ServicioDeHistoriaClinica`. Los cinco restantes están definidos a nivel de responsabilidad y de tipo de integración, pero todavía no tienen código. La sección 11 detalla honestamente qué falta y qué está pendiente dentro de lo ya implementado.

Respecto de una entrega anterior, hay dos cambios que atraviesan buena parte del documento: la separación en capas dejó de ser una convención de nombres y pasó a ser una estructura de paquetes real, y la autenticación, antes pendiente, ya está conectada de punta a punta y fue verificada en un despliegue real. También se corrigió un error de diseño en el manejo del temporizador de expiración de turnos (sección 6.2), que es en sí mismo evidencia de comprensión del modelo de componentes de Jakarta EE.

## 2. Elección del stack tecnológico

El criterio del grupo para elegir el stack fue explícito: priorizar la cobertura **nativa** de los requisitos de la cátedra por sobre la afinidad personal con un lenguaje o framework. Se evaluaron alternativas permitidas como Spring Boot, .NET, Quarkus, Micronaut y Node con NestJS.

El stack elegido es:

- **Jakarta EE 11** sobre **WildFly** como contenedor de aplicaciones.
- **PostgreSQL** como motor de base de datos.
- **Maven** como herramienta de build.
- **IntelliJ IDEA Ultimate** como entorno de desarrollo.

### 2.1 El criterio de desempate: el componente stateful

La consigna exige evidencia concreta de que el contenedor gestiona el ciclo de vida de al menos un componente con estado conversacional (en este caso, `ServicioDeTurnos`, que sostiene el hold de un turno reservado). Ese requisito, y no SOAP, fue el que decidió el stack.

Ninguna de las otras alternativas permitidas tiene un equivalente nativo al `@Stateful` de Enterprise JavaBeans. En Spring Boot, .NET, Quarkus, Micronaut o Node/NestJS, un componente con estado por conversación de cliente tiene que programarse a mano: un scheduler propio, una entrada en Redis con TTL, un `HostedService` con timers, etc. Jakarta EE ofrece esto como parte de la especificación del contenedor: el propio contenedor instancia, destruye y expira estas instancias sin código de infraestructura adicional. La sección 6.2 muestra que aprovechar este mecanismo correctamente también exige entender sus límites: no todos los tipos de bean pueden combinarse con todas las facilidades del contenedor.

### 2.2 Argumentos secundarios

Una vez resuelto el desempate por el componente stateful, hay razones adicionales que refuerzan la elección de WildFly como contenedor:

- **ActiveMQ Artemis embebido**: WildFly incluye un broker JMS listo para usar, lo que cubre el requisito de mensajería asincrónica (pensado para `ServicioDeNotificaciones` y `ServicioDeFacturacion`) sin necesidad de levantar infraestructura adicional (no hace falta un Kafka o RabbitMQ externos para este alcance).
- **Apache CXF embebido**: cubre JAX-WS para el caso en que se necesite exponer o consumir servicios SOAP, como la integración con el sistema legado de obras sociales.

### 2.3 Un caveat honesto sobre SOAP

Vale aclarar algo que no conviene ocultar: SOAP (JAX-WS) es **opcional** en la especificación de Jakarta EE desde la versión 9, no un componente obligatorio de todo servidor compatible. Por eso la elección de WildFly como servidor concreto no es un detalle secundario: es parte de la decisión de diseño, porque es WildFly (y no la especificación de Jakarta EE en abstracto) quien efectivamente empaqueta CXF y lo deja disponible sin configuración extra. Si el grupo hubiera elegido un servidor Jakarta EE que no incluyera JAX-WS por defecto, la integración SOAP con la obra social habría requerido agregar esa dependencia manualmente.

## 3. Arquitectura en capas

El sistema se organiza en tres capas, con una regla de dependencia estricta: cada capa solo puede depender de la capa inmediatamente inferior, y las reglas de negocio viven exclusivamente en la capa de negocio.

| Capa | Responsabilidad | Tecnologías | Qué NO hace |
|---|---|---|---|
| Presentación | Recibe la interacción externa, valida formato de entrada, serializa la respuesta | React (SPA), recursos JAX-RS, endpoints JAX-WS | No decide nada del dominio; no contiene reglas de negocio ni de autorización |
| Negocio | Reglas del dominio, límites transaccionales, seguridad por rol | EJB (`@Stateless`, `@Stateful`, `@Singleton`), CDI, JTA, Jakarta Security | No conoce detalles de SQL ni de la SPA |
| Datos | Traduce objetos de dominio a filas de base de datos | Patrón DAO, JPA/Hibernate, PostgreSQL | No contiene reglas de negocio |

A diferencia de una entrega anterior, esta separación ya no depende del sufijo del nombre de una clase (`*Resource`, `Servicio*`, `*DAO`) conviviendo todas en un mismo paquete: hoy es una estructura de paquetes real:

```
ar.edu.uade.da2.mediconecta.{usuarios,turnos,historiaclinica}.{presentacion,negocio,datos}
```

Es decir, nueve subpaquetes (tres componentes por tres capas), cada uno con las clases que le corresponden. Por ejemplo, `turnos.presentacion` contiene `TurnosResource`, `turnos.negocio` contiene `ServicioDeTurnos` y `ExpiradorDeHolds`, y `turnos.datos` contiene `Turno`, `TurnoDAO` y `EstadoTurno`.

En el paquete raíz (`ar.edu.uade.da2.mediconecta`) quedan tres clases fuera de esa estructura: `ApiActivator` (la clase `Application` de JAX-RS, con `@ApplicationPath("/api")`) y los dos `ExceptionMapper` transversales, `AccesoDenegadoMapper` y `ErrorInesperadoMapper`.

Es una excepción deliberada a la regla. Son infraestructura JAX-RS que atraviesa los tres componentes por igual: `AccesoDenegadoMapper` traduce a `403` cualquier `EJBAccessException`, venga del componente que venga, y no pertenece más a turnos que a historia clínica. Ubicarlos dentro de un componente sugeriría una pertenencia que no existe.

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

Que los tres componentes ya convivan integrados y desplegados juntos fue justamente lo que permitió detectar y corregir la incompatibilidad entre `@Stateful` y `TimerService` descrita en la sección 6.2: un problema que solo se manifiesta con el sistema desplegado, no en aislamiento.

## 5. Caso de uso representativo: reservar un turno con cobertura

Este flujo atraviesa las tres capas y varios de los ocho componentes, e ilustra cómo interactúan cuando el sistema esté completo:

1. La SPA envía `POST /api/turnos` con los datos de la reserva; el pedido llega a `TurnosResource` (recurso JAX-RS).
2. `ServicioDeTurnos` marca el turno como `EN_HOLD` y delega en `ExpiradorDeHolds`, un `@Singleton` con `TimerService`, la programación del temporizador de expiración.
3. `TurnoDAO` verifica la disponibilidad del turno vía JPA.
4. `ServicioDeObrasSociales` (Adapter) valida la cobertura del paciente.
5. Esa validación implica una llamada SOAP al sistema legado de la obra social.
6. Si la cobertura es parcial, `ServicioDePagos` calcula y cobra el copago correspondiente.
7. Ese cobro se resuelve con una llamada REST a la pasarela de pago externa.
8. Toda la secuencia reservar/cobrar/confirmar queda envuelta en una transacción declarativa; si cualquier paso falla, se hace rollback de los pasos anteriores.
9. Al confirmarse el turno, se publica un evento `TurnoConfirmado` en un tópico JMS. `ServicioDeNotificaciones` lo consume de forma asincrónica, desacoplado del tiempo de respuesta del `POST` original.
10. La SPA recibe `201 Created`.

Los pasos 1 a 3 y 8 (parcialmente, ver sección 11 sobre el mapeo de errores de negocio) están respaldados por código real, verificado en el despliegue descrito en la sección 10. Los pasos 4, 5, 6, 7 y la publicación JMS del paso 9 describen el diseño previsto para los componentes aún no implementados (secciones 4 y 11).

## 6. Evidencia de implementación

Esta sección documenta lo que existe realmente en el código, con referencia a archivo y, cuando fue posible confirmarlo abriendo el archivo, a la ubicación exacta dentro de él. Cuando no se pudo verificar un número de línea puntual, se describe la ubicación en lugar de citarlo, para no repetir un error de una entrega anterior de este documento, en la que se citó una línea que no existía.

### 6.1 ServicioDeUsuarios

Componente `@Stateless`: no mantiene conversación entre llamadas, cada operación (registrar, autenticar, consultar) es autocontenida.

```java
// usuarios/negocio/ServicioDeUsuarios.java
@Stateless
@PermitAll
public class ServicioDeUsuarios {
```

El `@PermitAll` a nivel de clase no es un descuido de seguridad: el propio código lo explica en un comentario, porque WildFly deniega por defecto cualquier método sin permiso declarado en cuanto el bean tiene alguna anotación de seguridad. Sin ese `@PermitAll` general, anotar solamente `listarUsuarios` con `@RolesAllowed` rompería el registro y el login, que deben quedar abiertos a cualquiera.

- `usuarios/datos/UsuarioDAO.java`: `@Stateless`, con `@PersistenceContext(unitName = "mediconectaPU")`, expone `guardar`, `buscarPorId`, `buscarPorEmail` y `listarTodos`.
- `usuarios/datos/Usuario.java`: entidad JPA (`@Entity`, tabla `usuarios`), con los campos `id`, `nombre`, `email`, `rol` y `contrasenaHash`.
- `usuarios/presentacion/UsuariosResource.java`: recurso JAX-RS en `@Path("/usuarios")`, con `@RolesAllowed("ADMINISTRADOR")` sobre el método de listado.
- `usuarios/negocio/PasswordUtil.java`: hash de contraseña con `SHA-256` y codificación Base64, sin salt (ver la salvedad de la sección 11).

### 6.2 ServicioDeTurnos y la expiración del hold

Esta es la sección con el cambio más importante respecto de una entrega anterior. `ServicioDeTurnos` sigue siendo `@Stateful` y sigue sosteniendo el hold de un turno reservado, pero ya **no** aloja al `TimerService` que expira ese hold. El propio código documenta por qué:

```java
// turnos/negocio/ServicioDeTurnos.java
/**
 * Componente stateful: mantiene el hold de un turno durante la
 * conversacion reservar -> confirmar/cancelar.
 *
 * La expiracion del hold NO vive aca sino en ExpiradorDeHolds. La
 * especificacion de Jakarta Enterprise Beans no permite crear timers
 * sobre un stateful session bean; ver el javadoc de ese componente
 * para el detalle.
 */
@Stateful
public class ServicioDeTurnos {
```

Esto no es una opinión del equipo, es lo que dice la especificación. El proyecto compila contra `jakarta.platform:jakarta.jakartaee-api` versión `11.0.0`, que incluye Jakarta Enterprise Beans. El javadoc de `jakarta.ejb.TimerService`, incluido en esa API, enumera explícitamente qué tipos de bean pueden registrar timers:

> "The enterprise bean Timer Service allows stateless session beans, singleton session beans, message-driven beans, and enterprise bean 2.x entity beans to be registered for timer callback events."

Los *stateful* session beans no figuran en esa lista. En una iteración anterior del proyecto, cuando el temporizador vivía dentro de `ServicioDeTurnos`, esta restricción se manifestó de forma muy concreta: WildFly inyectaba un `TimerService` no funcional y `POST /api/turnos` devolvía `500` en la primera reserva. La solución no fue "sacar el timer", sino entender que hay dos responsabilidades distintas que necesitan dos tipos de bean distintos:

```java
// turnos/negocio/ExpiradorDeHolds.java
@Singleton
@PermitAll
public class ExpiradorDeHolds {

    @Resource
    private TimerService timerService;

    // ...

    @Timeout
    public void expirar(Timer timer) {
        // libera el turno vencido y lo vuelve a DISPONIBLE
    }
}
```

`ServicioDeTurnos` conserva el estado conversacional (un único campo, `turnoEnCursoId`, expuesto por su getter con el comentario "expone el estado conversacional que mantiene esta instancia stateful: qué turno está reteniendo esta conversación en este momento") y delega la programación y la cancelación del temporizador en `ExpiradorDeHolds` por inyección:

```java
@Inject
private ExpiradorDeHolds expirador;

// en reservarTurno:
expirador.programar(turnoId, DURACION_HOLD_MS);

// en confirmarTurno y en cancelarTurno:
expirador.cancelar(turnoId);
```

El diseño resultante es coherente con el modelo de componentes: el estado conversacional por cliente va en el `@Stateful`, y la tarea programada, que el contenedor debe poder disparar sin depender de qué instancia stateful la originó, va en un `@Singleton`, el tipo de bean pensado justamente para un recurso único compartido por toda la aplicación.

Corregir dónde vive el `TimerService` dejó a la vista un segundo problema, en la cancelación de timers. La versión anterior cancelaba así:

```java
for (Timer t : timerService.getTimers()) {
    t.cancel();
}
```

El javadoc de `TimerService.getTimers()` es explícito: devuelve "all active timers associated with this bean", es decir, todos los timers del bean, no de una instancia o de un paciente en particular. Con esa implementación, confirmar o cancelar el turno de un paciente cancelaba también los holds vigentes de todos los demás pacientes. La versión actual filtra por la información asociada a cada timer:

```java
// turnos/negocio/ExpiradorDeHolds.java
public void cancelar(Long turnoId) {
    for (Timer timer : timerService.getTimers()) {
        if (turnoId.equals(timer.getInfo())) {
            timer.cancel();
        }
    }
}
```

Cada timer se crea con `new TimerConfig(turnoId, false)`, de modo que `timer.getInfo()` devuelve el id del turno al que pertenece y permite distinguirlo del resto (el segundo argumento, `false`, indica que el timer no es persistente; ver la sección 11 sobre las consecuencias de esa elección).

Entidades y capa de datos:

- `turnos/datos/Turno.java`: entidad JPA (`@Entity`, tabla `turnos`), con `paciente` y `profesional` como `@ManyToOne`, `estado` (`@Enumerated(EnumType.STRING)`, tipo `EstadoTurno`) y `inicioHold` (`LocalDateTime`).
- `turnos/datos/EstadoTurno.java`: enum con los valores `DISPONIBLE`, `EN_HOLD`, `CONFIRMADO` y `CANCELADO`.
- `turnos/datos/TurnoDAO.java`: `@Stateless`, acceso a JPA.
- `turnos/presentacion/TurnosResource.java`: recurso JAX-RS en `@Path("/turnos")`, que ahora incluye un endpoint que antes no existía: `POST /api/turnos/disponibilidad`, para que un profesional publique un turno disponible. La restricción por rol no está en el recurso JAX-RS sino en el método de negocio que invoca, `ServicioDeTurnos.abrirDisponibilidad`, anotado `@RolesAllowed("PROFESIONAL")`. Esto es consistente con la regla de la sección 3: la capa de presentación no decide nada del dominio, ni siquiera quién puede hacer qué.

### 6.3 ServicioDeHistoriaClinica

```java
// historiaclinica/negocio/ServicioDeHistoriaClinica.java
/**
 * Patrón Facade: única puerta de entrada al componente de historia clínica.
 *
 * Es stateless porque cada operación es autocontenida — no hay conversación con
 * el cliente que haya que recordar entre llamadas. El componente que sí va a ser
 * stateful es ServicioDeTurnos, que mantiene el hold del turno mientras el
 * paciente confirma.
 */
@Stateless
public class ServicioDeHistoriaClinica implements ServicioDeHistoriaClinicaLocal {
```

- `historiaclinica/negocio/ServicioDeHistoriaClinicaLocal.java`: interfaz `@Local`, único contrato visible para los consumidores del componente, con cinco operaciones (`crearHistoria`, `obtenerHistoriaDePaciente`, `agregarEntrada`, `registrarConsulta`, `listarEntradas`).
- `historiaclinica/negocio/EntradaClinicaFactory.java`: javadoc que justifica el patrón Factory (ver sección 8.3).
- `historiaclinica/datos/HistoriaClinicaDAO.java` y `EntradaClinicaDAO.java`: ambos `@Stateless`, acceso a JPA.
- `historiaclinica/datos/HistoriaClinica.java`: entidad JPA, tabla `historias_clinicas`. Referencia al paciente por id (`pacienteId`) y no con `@ManyToOne`, porque la tabla de usuarios pertenece a otro componente.
- `historiaclinica/datos/EntradaClinica.java`: entidad abstracta, con herencia de tabla única (`@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`) y columna discriminadora `tipo`.
- Subclases de `EntradaClinica`: `Antecedente` (`@DiscriminatorValue("ANTECEDENTE")`), `Diagnostico` (`"DIAGNOSTICO"`) y `Receta` (`"RECETA"`).
- `historiaclinica/presentacion/HistoriaClinicaResource.java`: recurso JAX-RS en `@Path("/historias")`.

## 7. Autenticación y autorización

La autenticación, pendiente en una entrega anterior, ya está implementada y fue verificada en ejecución (sección 10).

La configuración vive en `usuarios/presentacion/ConfiguracionDeSeguridad.java`:

```java
@ApplicationScoped
@BasicAuthenticationMechanismDefinition(realmName = "MediConecta")
@DatabaseIdentityStoreDefinition(
        dataSourceLookup = "java:/MediConectaDS",
        callerQuery = "SELECT contrasenaHash FROM usuarios WHERE email = ?",
        groupsQuery = "SELECT rol FROM usuarios WHERE email = ?",
        hashAlgorithm = HashDeContrasena.class,
        priority = 10)
public class ConfiguracionDeSeguridad {
}
```

Es Jakarta Security estándar: autenticación HTTP Basic contra un `IdentityStore` respaldado por la misma base de datos de usuarios, sin ningún componente adicional. El algoritmo de verificación de contraseña es un `PasswordHash` propio, `usuarios/negocio/HashDeContrasena.java`, que delega en `PasswordUtil` (SHA-256, sin salt; ver sección 11).

Cuando el contenedor rechaza una llamada por rol, la excepción que produce (`EJBAccessException`) se traduce a un código HTTP legible mediante un `ExceptionMapper` transversal:

```java
// AccesoDenegadoMapper.java
public Response toResponse(EJBAccessException excepcion) {
    return Response.status(Response.Status.FORBIDDEN)
            .entity("No tiene permisos para realizar esta operacion")
            // ...
}
```

`ErrorInesperadoMapper.java` cumple un rol complementario: intercepta cualquier otra excepción no controlada, la registra en el log del servidor, y devuelve un `500` genérico sin exponer el detalle interno (por ejemplo, el mensaje de una excepción de Hibernate), salvo que la excepción ya sea una `WebApplicationException`, en cuyo caso respeta su respuesta.

`web.xml` declara los tres roles del sistema (`ADMINISTRADOR`, `PACIENTE`, `PROFESIONAL`) y protege los tres componentes con `security-constraint`:

| Recurso | Métodos | Roles permitidos |
|---|---|---|
| `/api/historias/*` | todos | PROFESIONAL, PACIENTE, ADMINISTRADOR |
| `/api/usuarios` | GET | ADMINISTRADOR |
| `/api/turnos/*` | todos | PACIENTE, PROFESIONAL, ADMINISTRADOR |
| `/api/turnos` | POST | PACIENTE, ADMINISTRADOR |

Encima de esa capa gruesa de `web.xml`, cada operación de negocio tiene su propia restricción con `@RolesAllowed`, más fina:

| Componente | Método | Roles |
|---|---|---|
| ServicioDeUsuarios | `listarUsuarios` | ADMINISTRADOR |
| ServicioDeTurnos | `abrirDisponibilidad` | PROFESIONAL |
| ServicioDeTurnos | `reservarTurno`, `confirmarTurno`, `cancelarTurno` | PACIENTE |
| ServicioDeHistoriaClinica | `crearHistoria`, `agregarEntrada`, `registrarConsulta` | PROFESIONAL |
| ServicioDeHistoriaClinica | `obtenerHistoriaDePaciente`, `listarEntradas` | PROFESIONAL, PACIENTE, ADMINISTRADOR |

Esa tabla, sin embargo, no alcanza para expresar toda la regla de negocio que hace falta. `@RolesAllowed` solo puede decidir en función del rol del que llama: no ve los argumentos del método. Un paciente tiene rol PACIENTE para leer *cualquier* historia clínica, no solo la propia, porque `@RolesAllowed("PACIENTE")` no tiene forma de comparar el `pacienteId` que llega como parámetro contra la identidad de quien hace la llamada. Por eso, en los dos lugares donde la regla depende de un dato del método y no solo del rol, la autorización se resuelve programáticamente con `SessionContext`:

```java
// historiaclinica/negocio/ServicioDeHistoriaClinica.java
private void verificarQuePuedeLeerLaHistoria(Long pacienteId) {
    if (contexto.isCallerInRole(ROL_PROFESIONAL) || contexto.isCallerInRole(ROL_ADMINISTRADOR)) {
        return;
    }
    String emailDelCaller = contexto.getCallerPrincipal().getName();
    Usuario solicitante = servicioDeUsuarios.obtenerPorEmail(emailDelCaller);
    if (solicitante == null || !pacienteId.equals(solicitante.getId())) {
        throw new EJBAccessException("Un paciente solo puede consultar su propia historia clinica.");
    }
}
```

```java
// turnos/negocio/ServicioDeTurnos.java
private void verificarQueElHoldEsDelCaller(Turno turno) {
    if (contexto.isCallerInRole(ServicioDeUsuarios.ROL_ADMINISTRADOR)) {
        return;
    }
    Usuario caller = usuarioAutenticado();
    if (turno.getPaciente() == null || !caller.getId().equals(turno.getPaciente().getId())) {
        throw new EJBAccessException("El turno fue reservado por otro paciente.");
    }
}
```

En ambos casos, un rol con visibilidad total (PROFESIONAL o ADMINISTRADOR en historias clínicas, ADMINISTRADOR en turnos) pasa sin más chequeo; para el resto se compara la identidad del `Principal` autenticado contra el dueño del recurso. Al lanzar la misma `EJBAccessException` del rechazo declarativo, ambos caminos terminan en el mismo `AccesoDenegadoMapper` y el cliente recibe el mismo `403`, sin distinguir si lo bloqueó una anotación o una regla escrita a mano.

## 8. Patrones de diseño aplicados

### 8.1 DAO (Data Access Object)

**Dónde:** `UsuarioDAO`, `TurnoDAO`, `HistoriaClinicaDAO`, `EntradaClinicaDAO`.

**Problema que resuelve:** separar la lógica de acceso a datos (uso del `EntityManager`, construcción de queries) de la lógica de negocio. Sin este patrón, cada `Servicio*` tendría el `EntityManager` inyectado directamente y mezclaría reglas de negocio con detalles de persistencia.

**Alternativa descartada:** inyectar `EntityManager` directamente en el EJB de negocio y ejecutar ahí las operaciones de persistencia. Se descartó porque acopla el componente de negocio a la forma concreta de acceder a los datos, dificulta testear la lógica de negocio de forma aislada (no hay un punto único donde reemplazar el acceso a datos por un doble de prueba), y porque distintos componentes necesitan datos de otro sin acceder a su tabla directamente (por ejemplo, `ServicioDeHistoriaClinica` resuelve si un id es un paciente siempre a través de `ServicioDeUsuarios`, nunca contra la tabla `usuarios`).

Cada DAO en el código actual sigue el mismo patrón mínimo: `@Stateless`, un `@PersistenceContext(unitName = "mediconectaPU")` inyectado, y métodos de persistencia (`guardar`, `buscarPor...`) que son la única superficie de acceso a la tabla correspondiente.

### 8.2 Facade

**Dónde:** `ServicioDeHistoriaClinicaLocal` (interfaz `@Local`) como única puerta de entrada al componente de historia clínica.

**Problema que resuelve:** el componente de historia clínica tiene varias piezas internas colaborando (`ServicioDeHistoriaClinica`, `HistoriaClinicaDAO`, `EntradaClinicaDAO`, `EntradaClinicaFactory`). El propio código lo documenta explícitamente en el javadoc de la interfaz:

> "Interfaz de negocio del componente ServicioDeHistoriaClinica. Es el único contrato que los consumidores conocen: la implementación, los DAO y el factory quedan ocultos detrás de estas cinco operaciones."

Cualquier otro componente que necesite datos de la historia clínica (por ejemplo, `ServicioDeTelemedicina` en el futuro) depende únicamente de esa interfaz de cinco operaciones, no de sus colaboradores internos.

**Alternativa descartada:** exponer directamente `HistoriaClinicaDAO` y `EntradaClinicaDAO` (o incluso la propia clase `ServicioDeHistoriaClinica` sin interfaz) a otros componentes. Se descartó porque rompería el encapsulamiento del componente (otro componente podría saltarse validaciones como `validarPaciente` o `validarProfesional`), y porque el flujo transaccional de `registrarConsulta`, que persiste un diagnóstico y una o más recetas de forma atómica dentro de un mismo `@TransactionAttribute(TransactionAttributeType.REQUIRED)` explícito, dejaría de estar garantizado si un consumidor externo pudiera invocar los DAO por separado. Si el factory rechaza una receta por datos inválidos, el rollback deshace también el diagnóstico y las recetas ya persistidas en esa misma llamada: la historia nunca queda con una consulta a medio registrar.

### 8.3 Factory

**Dónde:** `EntradaClinicaFactory`, que decide qué subclase de `EntradaClinica` instanciar (`Antecedente`, `Diagnostico` o `Receta`) según el tipo recibido en el DTO de entrada.

```java
// historiaclinica/negocio/EntradaClinicaFactory.java, líneas 12 a 22
/**
 * Patrón Factory.
 *
 * Concentra dos responsabilidades que, de otra forma, quedarían mezcladas en la
 * fachada como un switch sobre el tipo: decidir qué subclase de EntradaClinica
 * instanciar, y validar los campos obligatorios *de ese tipo en particular*
 * (una receta sin medicamento es inválida, pero un diagnóstico sin medicamento
 * es perfectamente normal).
 *
 * Agregar un cuarto tipo de entrada se resuelve acá, sin tocar el servicio.
 */
```

**Por qué no un switch disperso:** la alternativa evidente sería resolver la creación con un `switch` sobre el tipo de entrada directamente dentro de `ServicioDeHistoriaClinica`, repetido en `agregarEntrada` y en `registrarConsulta`. Se descartó porque cada tipo de entrada tiene validaciones propias e incompatibles entre sí: una receta exige `medicamento`, `dosis` y un `diasTratamiento` mayor a cero; un diagnóstico exige `codigoCIE10` y `descripcion`; un antecedente exige `tipoAntecedente` y `detalle`. Concentradas en el Factory, agregar un cuarto tipo de entrada en el futuro se resuelve modificando un solo archivo, sin tocar `ServicioDeHistoriaClinica`. El propio método auxiliar de validación de largo de texto documenta además por qué esa validación importa a nivel de negocio y no solo de base de datos: si el texto demasiado largo llegara sin control hasta el `INSERT`, el error saldría como un `500` de base de datos, cuando en realidad es un dato inválido del cliente y corresponde un `400`.

## 9. Decisión de diseño destacada: dónde vive el estado del hold

Una decisión que merece justificación explícita, porque a primera vista podría parecer un descuido: el estado del hold de un turno **no** se guarda únicamente en memoria del bean stateful `ServicioDeTurnos`. Se guarda en la propia entidad `Turno`, en los campos `estado` (valor `EN_HOLD`) e `inicioHold`.

El bean stateful sí mantiene un campo en memoria, `turnoEnCursoId`, pero ese campo es solo una referencia de conveniencia a qué turno está reteniendo esta conversación en este momento, no la fuente de verdad del hold. La fuente de verdad es la fila de `Turno` en PostgreSQL, y el comportamiento del código lo confirma: tanto `confirmarTurno` como `cancelarTurno` vuelven a buscar el `Turno` en la base de datos a partir del id recibido como parámetro, y no leen `turnoEnCursoId` en ningún punto del flujo de negocio (ese campo solo se lee desde su propio getter).

Esto es deliberado, por dos razones concretas:

1. **El hold debe sobrevivir a un reinicio del contenedor.** Si el estado `EN_HOLD` viviera solo en memoria del bean, un reinicio de WildFly perdería silenciosamente todos los holds activos, dejando turnos marcados como reservados en la base de datos pero sin ningún temporizador real corriendo para liberarlos.
2. **El paciente puede confirmar desde otra solicitud HTTP.** Un bean `@Stateful` está atado a una instancia concreta del contenedor. Si la confirmación del turno llega en una solicitud distinta de la que generó el hold, como ocurre naturalmente cuando el contenedor inyecta una nueva instancia de `ServicioDeTurnos` por request, ningún estado en memoria del bean anterior estaría disponible para esa segunda solicitud.

En otras palabras: el `@Stateful` se limita a sostener la referencia conversacional y a delegar en el `@Singleton` que efectivamente expira el hold (sección 6.2), pero la información de negocio que debe sobrevivir entre llamadas está en la base de datos, no en la memoria del bean. Esto también explica por qué el timer, aunque hoy vive en `ExpiradorDeHolds`, sigue sin resolver por completo un reinicio del contenedor (sección 11).

## 10. Verificación en ejecución

Esta sección es la que más distingue a esta entrega de una versión únicamente basada en lectura de código: el sistema se desplegó en WildFly 41 con PostgreSQL 18 y se verificó de punta a punta, endpoint por endpoint, con el usuario autenticado que corresponde a cada caso.

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

Este último resultado es, junto con la corrección descrita en la sección 6.2, la evidencia más fuerte de que el mecanismo de timers del contenedor funciona como se lo diseñó: nadie liberó el turno manualmente, lo hizo el `@Timeout` de `ExpiradorDeHolds` por su cuenta, sin que ninguna solicitud HTTP estuviera en curso en ese momento.

El repositorio incluye dos scripts que automatizan y documentan esta verificación:

- `deploy/mediconecta-setup.cli`: script de JBoss CLI que instala el driver JDBC de PostgreSQL como módulo de WildFly y crea el datasource `java:/MediConectaDS`.
- `deploy/smoke-test.sh`: automatiza contra un servidor ya desplegado buena parte de la tabla anterior, incluyendo la espera de varios minutos necesaria para confirmar la expiración real del hold.

Vale la pena dejar registrado un hallazgo de configuración que costó diagnosticar, porque puede ahorrarle tiempo a cualquiera que reproduzca el despliegue: la integración de Jakarta Security (Soteria) con Elytron en WildFly requiere fijar `integrated-jaspi=false` en el `application-security-domain` de Undertow. El propio `mediconecta-setup.cli` lo hace explícitamente y explica por qué: sin ese ajuste, Elytron intenta reautorizar una identidad que Soteria ya estableció, y **todos** los endpoints protegidos responden `500` con el mensaje `"ELY01177: Authorization failed"`, incluso para un usuario con las credenciales y el rol correctos. La pista que permite distinguir este problema de configuración de un error de credenciales real es justamente esa asimetría: una contraseña incorrecta sigue devolviendo `401` con total normalidad (falla la autenticación, no la reautorización), mientras que una credencial correcta con permisos correctos falla igual con `500`.

## 11. Estado actual y trabajo pendiente

Este documento se entrega junto con código que tiene huecos conocidos. Ya no incluye la autenticación, la autorización de historia clínica ni la separación en capas, porque esos tres puntos, pendientes en una entrega anterior, ya están resueltos (secciones 3 y 7). Lo que queda abierto son decisiones de alcance, no vacíos de diseño:

- **Reserva concurrente sin control de concurrencia.** Ni la entidad `Turno` ni `ServicioDeTurnos` usan lock pesimista ni un campo `@Version` de bloqueo optimista. Dos pacientes reservando el mismo turno al mismo tiempo pueden pisarse.
- **El timer del hold no es persistente.** `ExpiradorDeHolds` crea cada timer con `new TimerConfig(turnoId, false)`, es decir, explícitamente no persistente. Si WildFly se reinicia mientras hay turnos `EN_HOLD`, la fila en PostgreSQL sobrevive al reinicio, pero la tarea programada que iba a expirarla no: esos turnos quedan en `EN_HOLD` indefinidamente hasta que se los toque manualmente.
- **Los errores de negocio de turnos salen como `500`.** A diferencia de `HistoriaClinicaResource`, que traduce explícitamente sus excepciones de negocio a `400` o `409`, `TurnosResource` no tiene ese mapeo todavía: una excepción de negocio en `ServicioDeTurnos` (por ejemplo, intentar confirmar un turno que ya no está en hold) llega al cliente como un `500` genérico en lugar de un código que refleje que el problema es del pedido, no del servidor.
- **Contraseñas con SHA-256 sin salt.** `PasswordUtil` calcula el hash sin agregar salt, lo que deja a las contraseñas expuestas a ataques de diccionario precomputado (rainbow tables) si la base de datos se filtrara.
- **El artifactId del proyecto sigue siendo `mediconecta-usuarios`.** El único `pom.xml` del repositorio, de módulo único, conserva ese nombre aunque el sistema ya tiene tres componentes implementados.
- **`SeedDeUsuariosIniciales` deja contraseñas fijas para los usuarios de prueba.** El profesional y el paciente de prueba se crean con una contraseña fija hardcodeada; el administrador puede sobrescribirse por variable de entorno, pero si no se define, cae en la misma contraseña por defecto.

## 12. Uso de inteligencia artificial generativa

En cumplimiento de lo solicitado por la cátedra, se deja constancia del uso de herramientas de inteligencia artificial generativa durante el desarrollo de este trabajo. Se utilizó asistencia de IA para tareas de documentación (incluyendo la redacción de este documento técnico a partir de las decisiones y el código provistos por el equipo, y su actualización posterior contra el estado real del repositorio), revisión de código y generación de boilerplate repetitivo (por ejemplo, getters/setters, estructuras de DTO). Las decisiones de arquitectura, la elección del stack tecnológico y la implementación del código fueron desarrolladas por el equipo, y cada integrante puede defender individualmente cualquier parte del trabajo entregado.
