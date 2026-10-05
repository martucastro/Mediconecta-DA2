# MediConecta

Plataforma que conecta pacientes con profesionales de salud independientes y
clínicas para turnos presenciales y telemedicina.

Trabajo Práctico Integrador de Desarrollo de Aplicaciones II, comisión Lunes TM.

---

## Qué hay implementado

Siete componentes de negocio, cada uno con su arquitectura en capas:

| Componente | Tipo | Responsabilidad |
|---|---|---|
| `ServicioDeUsuarios` | `@Stateless` | Registro, autenticación, perfiles y credenciales |
| `ServicioDeTurnos` | `@Stateful` | Disponibilidad, reserva, hold de 5 minutos, confirmación |
| `ServicioDeHistoriaClinica` | `@Stateless` | Antecedentes, diagnósticos y recetas |
| `ServicioDeObrasSociales` | `@Stateless`, Adapter SOAP | Cobertura y autorización contra el legado de la obra social |
| `ServicioDePagos` | `@Stateless`, Adapter REST | Cobro de copagos y reembolsos contra la pasarela de pago externa |
| `ServicioDeFacturacion` | `@Stateless` | Reclamo de facturación a la obra social por turnos con cobertura autorizada |
| `ServicioDeTelemedicina` | `@Stateless`, Adapter REST | Sala de video de los turnos de telemedicina contra el proveedor de video externo |

```
ar.edu.uade.da2.mediconecta
  usuarios/{presentacion, negocio, datos}
  turnos/{presentacion, negocio, datos}
  historiaclinica/{presentacion, negocio, datos}
  obrassociales/{negocio, datos}       sin HTTP: lo invocan otros componentes
  pagos/{presentacion, negocio, datos}
  facturacion/{presentacion, negocio, datos}
  telemedicina/{presentacion, negocio, datos}
  externos/obrasocial                  el legado SOAP simulado, un tercero
  externos/pasarela                    la pasarela de pago REST simulada, otro tercero
  externos/video                       el proveedor de video REST simulado, otro tercero
```

---

## Requisitos

| | Versión usada | De dónde |
|---|---|---|
| JDK | 21 | cualquier distribución. El código compila con `release 17`, pero las pruebas usan Mockito 5.7, que no funciona con JDK 25 |
| Maven | 3.9+ | para `mvn package` |
| WildFly | 41.0.0.Final | https://www.wildfly.org/downloads/ |
| PostgreSQL | 18.x | https://www.postgresql.org/download/ |
| Driver JDBC | postgresql 42.7.4 | ver paso 2 |

---

## Puesta en marcha

### 1. Base de datos

```bash
createdb -h localhost -U postgres mediconecta
```

Las tablas las crea Hibernate al desplegar (`hbm2ddl.auto=update`), no hace
falta correr ningún script.

Si tu instalación de PostgreSQL pide contraseña, ajustá el usuario y la clave en
`deploy/mediconecta-setup.cli` antes del paso 3.

### 2. Driver JDBC

Descargalo dentro de `deploy/`, que es donde el script de configuración lo busca:

```bash
curl -L -o deploy/postgresql.jar \
  https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar
```

### 3. Configurar WildFly

Descargá el ZIP desde https://www.wildfly.org/downloads/ y descomprimilo donde
quieras (no va dentro del repo). Definí `WILDFLY_HOME` apuntando a esa carpeta:

```bash
export WILDFLY_HOME=/ruta/a/wildfly-41.0.0.Final     # Linux y macOS
set WILDFLY_HOME=C:\ruta\a\wildfly-41.0.0.Final       # Windows
```

Creá el usuario de administración de la consola de gestión (una sola vez).
Con `-s` corre en modo silencioso, sin las preguntas interactivas:

```bash
$WILDFLY_HOME/bin/add-user.sh -u admin -p 'Admin123!' -s     # Linux y macOS
%WILDFLY_HOME%\bin\add-user.bat -u admin -p Admin123! -s     # Windows
```

Arrancá el servidor **con el perfil full (`standalone-full.xml`), es un
requisito**: es el que incluye el subsistema de mensajería (`messaging-activemq`)
donde el script de configuración crea el tópico y la cola JMS. Con
`standalone.xml` no se pueden crear:

```bash
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml     # Linux y macOS
%WILDFLY_HOME%\bin\standalone.bat -c standalone-full.xml   # Windows
```

Esperá a ver `WFLYSRV0025: WildFly ... started` en la consola, y verificá:

| Qué | URL | Qué deberías ver |
|---|---|---|
| Servidor | http://localhost:8080 | Página de bienvenida de WildFly |
| Consola de administración | http://localhost:9990 | Login; entrá con `admin` / `Admin123!` |

Con el servidor arriba, desde la raíz del proyecto:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=deploy/mediconecta-setup.cli
```

El script instala el driver, crea el datasource `java:/MediConectaDS`, ajusta
la integración de Jakarta Security y crea los destinos JMS:

| Destino | Tipo | JNDI | Para qué |
|---|---|---|---|
| `TurnoConfirmadoTopic` | Tópico | `java:/jms/topic/TurnoConfirmado` | Evento de turno confirmado |
| `ReclamosFacturacionQueue` | Cola | `java:/jms/queue/ReclamosFacturacion` | Reclamos de facturación a la obra social |
| `ReclamosFacturacionDLQ` | Cola | `java:/jms/queue/ReclamosFacturacionDLQ` | Reclamos que fallaron en todos los reintentos |

Es idempotente: se puede correr de nuevo sin romper nada.

**Por qué uno es un tópico y el otro una cola.** El turno confirmado es un
evento: le puede interesar a más de un componente a la vez (notificaciones hoy,
otros mañana), y cada suscriptor recibe su propia copia. El reclamo de
facturación es un trabajo: lo tiene que procesar exactamente un consumidor, una
sola vez; si dos lo tomaran, la obra social recibiría el reclamo duplicado. Eso
es una cola punto a punto.

**Reintentos de la cola de reclamos.** Si el consumidor falla, Artemis
reintenta la entrega hasta 5 veces, esperando 2 s, 4 s, 8 s… (máximo 30 s)
entre intentos, para dar tiempo a que la obra social vuelva si estaba caída.
Después del quinto fallo el reclamo pasa a `ReclamosFacturacionDLQ`, donde
queda para revisarlo o reenviarlo a mano en vez de reintentarse para siempre.

#### Verificar los destinos JMS

Por CLI:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  "/subsystem=messaging-activemq/server=default/jms-topic=TurnoConfirmadoTopic:read-resource"
```

Debe responder `"outcome" => "success"` y en `entries` el valor
`java:/jms/topic/TurnoConfirmado`. Para la cola y su política de reintentos:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect   "/subsystem=messaging-activemq/server=default/jms-queue=ReclamosFacturacionQueue:read-resource"
$WILDFLY_HOME/bin/jboss-cli.sh --connect   "/subsystem=messaging-activemq/server=default:resolve-address-setting(activemq-address=jms.queue.ReclamosFacturacionQueue)"
```

El segundo comando debe mostrar `max-delivery-attempts => 5` y
`dead-letter-address => "jms.queue.ReclamosFacturacionDLQ"`.

Por la consola de administración (http://localhost:9990):
*Configuration → Subsystems → Messaging (ActiveMQ) → default → Destinations →
View*, pestañas **Topic** y **Queue**. El estado en vivo está en *Runtime →
(tu servidor) → Messaging (ActiveMQ) → default*.

### 4. Compilar y desplegar

```bash
mvn clean package
cp target/mediconecta.war $WILDFLY_HOME/standalone/deployments/
```

Listo cuando el log dice:

```
WFLYSRV0010: Deployed "mediconecta.war"
```

### 5. Verificar

```bash
bash deploy/smoke-test.sh
```

Recorre el flujo completo y comprueba la seguridad. El último caso espera cinco
minutos a propósito, porque verifica que el contenedor libere el hold vencido.
Para saltearlo: `bash deploy/smoke-test.sh --rapido`.

Las pruebas de unidad de la capa de negocio corren sin servidor:

```bash
mvn test
```

---

## Usuarios de prueba

Se crean solos en el primer arranque, desde
`usuarios/negocio/SeedDeUsuariosIniciales`:

| Correo | Rol | Variable de entorno |
|---|---|---|
| `admin@mediconecta.com` | ADMINISTRADOR | `MEDICONECTA_ADMIN_PASSWORD` |
| `profesional@mediconecta.com` | PROFESIONAL | `MEDICONECTA_PROFESIONAL_PASSWORD` |
| `paciente@mediconecta.com` | PACIENTE | `MEDICONECTA_PACIENTE_PASSWORD` |

Ninguna contraseña está escrita en el código. Cada una se toma de su variable de
entorno y, si no está definida, el arranque genera una al azar y la escribe una
sola vez en el log del servidor, con un `WARNING` que las lista. Anotalas ahí: lo
que queda en la base es el hash y de ahí no se vuelve.

Para que las colecciones de Postman y el smoke test funcionen tal cual están,
levantá el servidor con las tres definidas:

```bash
export MEDICONECTA_ADMIN_PASSWORD=cambiar123
export MEDICONECTA_PROFESIONAL_PASSWORD=cambiar123
export MEDICONECTA_PACIENTE_PASSWORD=cambiar123
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml -b 0.0.0.0
```

El correo del administrador también se puede cambiar, con
`MEDICONECTA_ADMIN_EMAIL`.

---

## La API

Base: `http://localhost:8080/mediconecta/api`

Todo lo que no sea consultar disponibilidad o registrarse exige autenticación
HTTP Basic.

### Turnos

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| `GET` | `/turnos?profesionalId=` | cualquiera | Lista los turnos disponibles |
| `POST` | `/turnos/disponibilidad` | PROFESIONAL | Abre una franja en su agenda |
| `POST` | `/turnos` | PACIENTE | Reserva y retiene por 5 minutos |
| `PUT` | `/turnos/{id}/confirmar` | PACIENTE | Confirma su propio hold |
| `PUT` | `/turnos/{id}/cancelar` | PACIENTE | Libera su propio hold |

`POST /turnos/disponibilidad` acepta `modalidad` (`PRESENCIAL` o
`TELEMEDICINA`, por defecto `PRESENCIAL`) y, solo para las presenciales,
`consultorio`. Los turnos que ya existían quedan como `PRESENCIAL` sin
migración: la columna se agrega con `DEFAULT 'PRESENCIAL'`.

`TurnoDTO` también expone los campos de cobertura (`coberturaAutorizada`,
`coberturaPorcentaje`, `copago`, `numeroAutorizacion`). Hoy vuelven en `null`:
los completa la validación de cobertura (SCRUM-91).

### Puntos de extensión del flujo de turnos

Obras sociales, pagos, telemedicina y el reclamo **no** se llaman desde
`ServicioDeTurnos`: se enganchan observando dos eventos CDI sincrónicos que
dispara el servicio, `TurnoEnReserva` y `TurnoEnConfirmacion`. El orden lo fija
`turnos/negocio/PuntosDeExtension`:

| Evento | Prioridad | Qué | Card |
|---|---|---|---|
| `TurnoEnReserva` | `COBERTURA` (100) | Validar cobertura, completar copago | SCRUM-91 |
| `TurnoEnConfirmacion` | `COBRO_COPAGO` (100) | Cobrar el copago | SCRUM-93 |
| `TurnoEnConfirmacion` | `SALA_DE_VIDEO` (200) | Crear la sala si es telemedicina | SCRUM-95 |
| `TurnoEnConfirmacion` | `RECLAMO` (300) | Encolar el reclamo a la obra social | pendiente |

Para sumar un paso:

```java
@TransactionAttribute(TransactionAttributeType.MANDATORY)
public void alConfirmar(
        @Observes @Priority(PuntosDeExtension.COBRO_COPAGO) TurnoEnConfirmacion evento) {
    // ... si lanza una excepcion con rollback, la confirmacion entera se revierte
}
```

Siempre `@Observes`, nunca `@ObservesAsync`: un observador asincrónico corre
fuera de la transacción y su falla ya no podría frenar la confirmación. El
criterio completo está en `docs/documento-tecnico.md`, sección 9.2.

### Telemedicina

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| `POST` | `/telemedicina/turno/{turnoId}` | paciente o profesional del turno | Crea la sala del turno (o devuelve la que ya tiene) y responde con el enlace de quien la pidió |
| `GET` | `/telemedicina/turno/{turnoId}` | paciente o profesional del turno | El enlace de quien pregunta: el profesional recibe el de anfitrión y el paciente el de invitado, nunca los dos. `404` si todavía no hay sala |

Cualquier otro usuario, administrador incluido, recibe `403`. Solo un turno de
`TELEMEDICINA` tomado por un paciente (`EN_HOLD` o `CONFIRMADO`) puede tener
sala; uno presencial responde `409` sin llamar al proveedor. Si el proveedor no
responde, `503` con `Retry-After`.

Hoy la sala se crea a pedido. El enganche con `confirmarTurno`, para que se
cree sola al confirmar, es SCRUM-95.

**El proveedor de video simulado** (`externos/video`) es un tercero que vive en
el mismo WAR, como la pasarela de pago: `POST /api/externo/salas`, sin
autenticación, con `{"reference": "...", "scheduledAt": "..."}`. Devuelve
`roomId`, `hostUrl` (profesional) y `guestUrl` (paciente). Las salas son de
Jitsi Meet y los enlaces funcionan de verdad. Para simular el proveedor caído,
una `reference` que empiece con `caer`, o levantar WildFly con
`-Dmediconecta.video.simular-caida=true`. `ServicioDeTelemedicina` lo llama por
HTTP con el Jakarta REST Client; la URL se cambia con `-Dmediconecta.video.url`.

### Facturación: reclamo a la obra social

`ServicioDeFacturacion` **no** es un observador de `PuntosDeExtension.RECLAMO`
ni se llama desde `ServicioDeTurnos.confirmarTurno`: es un segundo suscriptor
independiente del tópico `TurnoConfirmado` (`TurnoConfirmadoFacturacionMDB`,
el primero es `NotificacionMDB`). Así `facturacion` nunca toca código de
`turnos` y el reclamo solo se crea para confirmaciones que ya cerraron su
transacción. `PuntosDeExtension.RECLAMO` queda sin usar a propósito, no se
borra.

Flujo: `TurnoConfirmadoFacturacionMDB` lee el turno (`coberturaAutorizada`,
`coberturaPorcentaje`, `numeroAutorizacion`) con `ServicioDeTurnos.obtenerTurno`
y, si la cobertura está autorizada, `ServicioDeFacturacion.registrarReclamo`
persiste un `Reclamo` en `PENDIENTE` (idempotente por `turnoId`) y encola su id
en `ReclamosFacturacionQueue`. `ReclamoMDB` consume esa cola y llama al puerto
`CanalDeReclamos`:

| Resultado del canal | Reclamo queda | Reintenta |
|---|---|---|
| Éxito | `ENVIADO`, con `monto` y `numeroPresentacion` | — |
| `CanalDeReclamosNoDisponibleException` (transitorio), intento < 5 | `PENDIENTE`, con el intento y el error registrados | Sí: se relanza y Artemis reentrega |
| `CanalDeReclamosNoDisponibleException`, intento = 5 (ver tabla de reintentos más arriba) | `EN_REVISION_MANUAL` | No |
| `ReclamoRechazadoException` (rechazo definitivo de la obra social) | `EN_REVISION_MANUAL`, de inmediato | No: reintentar un rechazo determinístico no tiene sentido |
| Reclamo ya `ENVIADO` o `EN_REVISION_MANUAL` | sin cambios | No: reentrega idempotente |

**`@RunAs`.** `obtenerTurno` exige uno de los roles PACIENTE/PROFESIONAL/
ADMINISTRADOR y un MDB no tiene caller autenticado, así que
`TurnoConfirmadoFacturacionMDB` lleva `@RunAs(ServicioDeUsuarios.ROL_ADMINISTRADOR)`
para que el contenedor le propague esa identidad a la llamada. Funcionó sin
configuración adicional de Elytron contra este WildFly 41 (ver evidencia de la
verificación funcional en `odd/tasks/scrum-97-facturacion.md`, sección T3).

**El envío real.** `CanalDeReclamos` tiene una única implementación,
`CanalDeReclamosSoap`, que presenta el reclamo a través de
`ServicioDeObrasSociales.presentarReclamo(pacienteId, numeroAutorizacion)`
(SCRUM-90) y traduce sus excepciones: `DatosInvalidosException` (la
autorización no corresponde al afiliado o es desconocida) se vuelve
`ReclamoRechazadoException`, y `ObraSocialNoDisponibleException` (el legado
no respondió) se vuelve `CanalDeReclamosNoDisponibleException`. Es un bean
CDI simple (`@ApplicationScoped`), no un EJB: la llamada remota ya corre
`NOT_SUPPORTED` del lado de la fachada, así que no hay ninguna transacción
propia que proteger acá. Ver la sección del sistema legado más abajo para el
formato de la operación `presentarReclamo`.

**Visibilidad.** `GET /api/reclamos` (solo ADMINISTRADOR, `web.xml` +
`@RolesAllowed`) lista los reclamos con su estado, intentos y último error.

### Ejemplo del flujo completo

```bash
BASE=http://localhost:8080/mediconecta/api

# El profesional abre una franja (presencial, o "modalidad":"TELEMEDICINA")
curl -u profesional@mediconecta.com:cambiar123 \
     -H "Content-Type: application/json" \
     -d '{"fechaHora":"2027-03-15T14:30:00","modalidad":"PRESENCIAL","consultorio":"Consultorio 3"}' \
     $BASE/turnos/disponibilidad

# El paciente la reserva: queda EN_HOLD por 5 minutos
curl -u paciente@mediconecta.com:cambiar123 \
     -H "Content-Type: application/json" \
     -d '{"turnoId":1}' \
     $BASE/turnos

# Y la confirma antes de que venza
curl -u paciente@mediconecta.com:cambiar123 -X PUT $BASE/turnos/1/confirmar
```

Si pasan los cinco minutos sin confirmar, el turno vuelve solo a `DISPONIBLE`.
No hay ningún proceso de la aplicación vigilándolo: lo hace el `TimerService`
del contenedor a través de `turnos/negocio/ExpiradorDeHolds`.

---

## Sistema legado de obra social (SOAP)

Simulación de un tercero, no un componente nuestro: vive en
`externos/obrasocial` y se publica con el CXF de WildFly, sin autenticación.

WSDL: http://localhost:8080/mediconecta/legado/obrasocial?wsdl

Operaciones `validarCobertura` y `autorizarPrestacion`, ambas con `dni`,
`numeroAfiliado` y `codigoPrestacion` (`CONSULTA` o `TELECONSULTA`):

| DNI | Afiliado | Plan | Cobertura |
|---|---|---|---|
| 30111222 | OS-1001 | `PLAN_ALTO` | 100 % |
| 30333444 | OS-2002 | `PLAN_MEDIO` | 70 % |
| 30444555 | OS-3003 | `PLAN_BASICO` | 40 % |
| 30555666 | OS-4004 | `SIN_COBERTURA` | 0 % (no autoriza) |
| 30777888 | OS-5005 | `PLAN_ALTO` | 100 %, pero tarda 30 s en responder |

Cualquier otro afiliado devuelve un SOAP Fault. OS-5005 existe para mostrar que
MediConecta no se queda colgado: el Adapter corta a los 5 segundos. Los pedidos listos para usar
están en la carpeta `05` de `postman/MediConecta-Demo.postman_collection.json`;
también se puede importar el WSDL en SoapUI.

Una tercera operación, `presentarReclamo` (`dni`, `numeroAfiliado`,
`numeroAutorizacion`), presenta una prestación ya autorizada: no vuelve a
evaluar el plan, solo valida que la autorización corresponda al afiliado
(mismo formato determinista `AUT-<afiliado>-<prestación>` que emite
`autorizarPrestacion`) y devuelve `montoReconocido` (`arancel x porcentaje`
del plan, lo que paga la obra social) y `numeroPresentacion`. Una
autorización que no corresponda al afiliado, o que no exista, también
vuelve como SOAP Fault de código `Client`.

### El cliente: `ServicioDeObrasSociales`

El paciente de prueba (`paciente@mediconecta.com`) queda afiliado como OS-2002
en el primer arranque. Dónde está el legado y cuánto se lo espera se configura
con propiedades de sistema, que se leen en cada llamada:

| Propiedad | Por defecto |
|---|---|
| `mediconecta.obrasocial.url` | `http://localhost:8080/mediconecta/legado/obrasocial` |
| `mediconecta.obrasocial.timeoutConexionMs` | `2000` |
| `mediconecta.obrasocial.timeoutRespuestaMs` | `5000` |

Para simular el legado caído sin bajar nada, apuntá la URL a un puerto cerrado:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:add(value="http://127.0.0.1:1/x")'
```

Y para volver al comportamiento normal:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect '/system-property=mediconecta.obrasocial.url:remove'
```

---

## Si algo no arranca

**Todos los endpoints devuelven 500, pero una credencial incorrecta devuelve 401.**
Esa asimetría significa que la autenticación funciona y falla la autorización
posterior. Falta el paso 3: sin `integrated-jaspi=false`, Elytron intenta
autorizar contra su propio realm la identidad que estableció Soteria y responde
`ELY01177: Authorization failed`.

**El despliegue falla con `WFLYJCA0041` o no encuentra `java:/MediConectaDS`.**
El datasource no está creado o el nombre JNDI no coincide con el
`<jta-data-source>` de `src/main/resources/META-INF/persistence.xml`.

**El servidor arranca pero la mensajería no existe.**
Se arrancó con `standalone.xml` en vez de `standalone-full.xml`. El paso del
tópico en el script de configuración falla con `WFLYCTL0030` (no hay definición
registrada para `messaging-activemq`): reiniciá con `-c standalone-full.xml` y
volvé a correrlo.

---

## Limitaciones conocidas

Están acá a propósito: son decisiones de alcance de esta entrega, no descuidos.

- **Sin frontend.** El sistema se ejerce por HTTP, con las colecciones de Postman
  de `deploy/` y `postman/`. La primera entrega evalúa la arquitectura de capas y
  los componentes de negocio, no la interfaz.
- **Pruebas de unidad solo en el flujo de turnos.** El resto se verifica por
  integración, con `deploy/smoke-test.sh` contra el sistema desplegado.
- **Un solo módulo Maven.** Los seis componentes conviven en un WAR. Separarlos en
  módulos es lo que corresponde cuando se despliegan por separado, y todavía no es
  el caso.
- **Usuarios de prueba en el arranque.** `SeedDeUsuariosIniciales` crea un
  profesional y un paciente de ejemplo. Fuera de un entorno de desarrollo esos dos
  no deberían existir.
