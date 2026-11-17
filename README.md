# MediConecta

Plataforma que conecta pacientes con profesionales de salud independientes y
clínicas para turnos presenciales y telemedicina.

Trabajo Práctico Integrador de Desarrollo de Aplicaciones II, comisión Lunes TM.

---

## Qué hay implementado

Ocho componentes de negocio, cada uno con su arquitectura en capas:

| Componente | Tipo | Responsabilidad |
|---|---|---|
| `ServicioDeUsuarios` | `@Stateless` | Registro, autenticación, perfiles y credenciales |
| `ServicioDeTurnos` | `@Stateful` | Disponibilidad, reserva, hold de 5 minutos, confirmación y cancelación |
| `ServicioDeHistoriaClinica` | `@Stateless` | Antecedentes, diagnósticos y recetas |
| `ServicioDeNotificaciones` | `@Stateless` (con `NotificacionMDB` como `@MessageDriven` colaborador) | Recordatorio de turno confirmado, consumido del tópico JMS |
| `ServicioDeObrasSociales` | `@Stateless`, Adapter SOAP | Cobertura y autorización contra el legado de la obra social |
| `ServicioDePagos` | `@Stateless`, Adapter REST | Cobro de copagos y reembolsos contra la pasarela de pago externa |
| `ServicioDeFacturacion` | `@Stateless` | Reclamo de facturación a la obra social por turnos con cobertura autorizada |
| `ServicioDeTelemedicina` | `@Stateless`, Adapter REST | Sala de video de los turnos de telemedicina contra el proveedor de video externo |

```
ar.edu.uade.da2.mediconecta
  usuarios/{presentacion, negocio, datos}
  turnos/{presentacion, negocio, datos}
  historiaclinica/{presentacion, negocio, datos}
  notificaciones/{presentacion, negocio, datos}
  obrassociales/{negocio, datos}       sin HTTP: lo invocan otros componentes
  pagos/{presentacion, negocio, datos}
  facturacion/{presentacion, negocio, datos}
  telemedicina/{presentacion, negocio, datos}
  externos/obrasocial                  el legado SOAP simulado, un tercero
  externos/pasarela                    la pasarela de pago REST simulada, otro tercero
  externos/video                       el proveedor de video REST simulado, otro tercero
```

La documentación técnica está en [`docs/`](docs/). Las decisiones de
arquitectura, con sus alternativas descartadas, están en
[`docs/adr/`](docs/adr/README.md).

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

Antes de arrancarlo, definí las contraseñas de los usuarios de prueba en la
consola desde la que lo vas a arrancar: el seed (`SeedDeUsuariosIniciales`) las
lee del entorno del proceso de WildFly, y **solo cuando la tabla de usuarios
está vacía**, es decir al desplegar por primera vez contra una base nueva
(paso 4). Sin las variables genera contraseñas al azar (ver
[Usuarios de prueba](#usuarios-de-prueba)). El smoke test y las colecciones de
Postman esperan `cambiar123`:

```bash
# Linux y macOS
export MEDICONECTA_ADMIN_PASSWORD=cambiar123
export MEDICONECTA_PROFESIONAL_PASSWORD=cambiar123
export MEDICONECTA_PACIENTE_PASSWORD=cambiar123
```

```bat
:: Windows (cmd)
set MEDICONECTA_ADMIN_PASSWORD=cambiar123
set MEDICONECTA_PROFESIONAL_PASSWORD=cambiar123
set MEDICONECTA_PACIENTE_PASSWORD=cambiar123
```

En la misma consola, fijá la zona horaria de la JVM. Las fechas viajan como
`LocalDateTime`, sin zona: el backend decide qué es "ahora" (el vencimiento del
hold, una franja en el pasado) con la zona de la JVM, y el navegador muestra las
horas en la suya. Si WildFly corre en UTC (un contenedor, una VM en la nube),
todo se corre tres horas. Va en `JAVA_TOOL_OPTIONS` y no en `JAVA_OPTS`: si
`JAVA_OPTS` está definida, `standalone.conf` deja de aplicar sus valores de
memoria por defecto.

```bash
export JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires   # Linux y macOS
set JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires      # Windows (cmd)
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

Es idempotente: se puede correr de nuevo sin romper nada. Pero si el
datasource ya existe, volver a correrlo **no** actualiza su usuario ni su
clave: para cambiarlos, borralo primero y después corré el script de nuevo.

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --commands="/subsystem=datasources/data-source=MediConectaDS:remove,reload"
```

**Por qué uno es un tópico y el otro una cola.** El turno confirmado es un
evento: le puede interesar a más de un componente a la vez (notificaciones hoy,
otros mañana), y cada suscriptor recibe su propia copia. El reclamo de
facturación es un trabajo: lo tiene que procesar exactamente un consumidor, una
sola vez; si dos lo tomaran, la obra social recibiría el reclamo duplicado. Eso
es una cola punto a punto.

**Reintentos de la cola de reclamos.** Si el consumidor falla, Artemis
entrega el mensaje hasta 5 veces, esperando 2 s, 4 s, 8 s… (máximo 30 s)
entre intentos, para dar tiempo a que la obra social vuelva si estaba caída.
Si la obra social sigue sin responder en el quinto intento, el reclamo pasa a
`EN_REVISION_MANUAL` y el mensaje se confirma. A `ReclamosFacturacionDLQ`
llegan solo los mensajes cuyas cinco entregas fallan por un error no previsto
(por ejemplo, la base caída), para revisarlos o reenviarlos a mano.

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

Recorre el flujo completo y comprueba la seguridad, el copago y su registro de
pagos, la sala de video, las notificaciones y los reclamos (que los consumen los
MDB después del commit, por eso el script reintenta unos segundos antes de dar
algo por perdido) y la falla de la pasarela de pago (502, 400 y recuperación).
El último caso espera cinco minutos a propósito, porque verifica que el
contenedor libere el hold vencido. Toma las contraseñas de las mismas variables
`MEDICONECTA_*_PASSWORD` (por defecto `cambiar123`).

| Opción | Qué hace |
|---|---|
| `--rapido` | Saltea la espera de cinco minutos del hold. |
| `--resiliencia` | Agrega la demo de fallas del legado de la obra social (reserva 503 y reintentos del reclamo) y del proveedor de video (503). Las provoca con system properties de WildFly, así que necesita `JBOSS_CLI` con la ruta a `jboss-cli.sh` o `jboss-cli.bat`; las quita siempre al terminar, incluso si el script se interrumpe. |

```bash
JBOSS_CLI=$WILDFLY_HOME/bin/jboss-cli.sh bash deploy/smoke-test.sh --rapido --resiliencia
```

Abre franjas en horarios fijos, así que **una segunda corrida contra la misma
base falla** con `409` al abrir disponibilidad (el profesional ya tiene esa
franja). Para repetirlo, recreá la base (paso 1) y redesplegá. Las colecciones
de Postman, en cambio, generan fechas al azar y se pueden correr varias veces.

**Postman.** Importá la colección `postman/MediConecta-Demo.postman_collection.json`
**y** el entorno `postman/MediConecta-Demo.postman_environment.json`, y
**seleccioná ese entorno** antes de correr nada: de ahí salen las credenciales
y ahí se guardan los ids que la colección va creando.
`deploy/MediConecta.postman_collection.json` es una colección anterior y más
corta; también toma los ids de las respuestas, pero la de `postman/` cubre más
casos: preferí esa.

Las pruebas de unidad corren sin servidor: 256 del backend (JUnit y Mockito) y
28 del frontend (Vitest):

```bash
mvn test
cd frontend && npm test
```

### 6. Frontend (React + Vite)

El frontend vive en `frontend/`, separado del backend Java, y compila hacia
`src/main/webapp` para que lo sirva el mismo WAR.

Para trabajar en las pantallas sin recompilar y redesplegar en WildFly cada vez:

```bash
cd frontend
npm install   # solo la primera vez, o si cambiaron las dependencias
npm run dev
```

Esto levanta un servidor en `http://localhost:5173/mediconecta/`, con recarga
automática al guardar cualquier archivo. Los pedidos a `/mediconecta/api/*` se
redirigen automáticamente hacia `http://localhost:8080` (donde tiene que estar
corriendo WildFly con el backend desplegado), así que no hay problemas de CORS
al probar el login u otras pantallas conectadas a la API real.

Para generar el WAR no hace falta compilar el frontend a mano: `mvn clean package`
lo hace solo. El `frontend-maven-plugin` instala una copia propia de Node en
`target/` (no depende del Node de la máquina), corre `npm ci` y `npm run build`
(chequeo de tipos con `tsc` y `vite build`), y deja el resultado en
`src/main/webapp`, de donde lo toma el empaquetado del `.war`. Así el WAR nunca
queda con un bundle desactualizado.

Lo que genera Vite (`index.html` y `assets/` dentro de `src/main/webapp`) no se
commitea: está en `.gitignore`. `WEB-INF` (`web.xml`, `beans.xml`) no lo toca el
build. Si querés ver el resultado sin pasar por Maven, `npm run build` dentro de
`frontend/` hace lo mismo.

En Windows, `npm ci` falla con `EPERM` si algún programa tiene abierto algo de
`frontend/node_modules`. Antes de `mvn package`, cerrá `npm run dev` y el editor
que lo esté indexando.

#### Sesión

El login (`POST /api/usuarios/login`) no crea sesión en el servidor: el frontend
guarda el usuario y el header `Authorization: Basic` (el correo y la contraseña
codificados en base64, que **no es un cifrado**) en `sessionStorage`, y lo manda
en cada pedido a la API.

- Dura lo que dura la pestaña: sobrevive a un refresco, pero no a una pestaña
  nueva ni a cerrar el navegador.
- Cada ruta exige un rol: un paciente que pide `/agenda` vuelve a `/home`, un
  profesional que pide `/home` vuelve a `/agenda`, y el administrador vuelve a
  `/admin`. Es solo para no mostrar
  pantallas que no le corresponden; la autorización real la hace el backend.
- Un 401 en medio de la sesión (la credencial ya no vale) cierra la sesión y
  vuelve al login. Esto depende de que el fetch pueda manejar la respuesta: la
  API omite `WWW-Authenticate` cuando el pedido viene marcado con
  `X-Requested-With: XMLHttpRequest` (lo manda el frontend en cada llamada),
  así el navegador no abre su popup nativo de credenciales antes de que el
  código de arriba se ejecute. Un curl o un Postman que no manden ese
  encabezado sí reciben `WWW-Authenticate`, como corresponde a HTTP Basic.
- El administrador entra a `/admin`, el panel de reclamos de facturación
  (`GET /api/reclamos`): turno, monto, estado, intentos y última actualización,
  con un filtro por estado. Un reclamo aparece al confirmar un turno con
  cobertura y pasa a "Enviado" cuando la obra social lo acepta.

Guardar la credencial en el navegador es lo que pide la card porque hoy el
backend solo habla HTTP Basic. Reemplazarlo por un token con vencimiento
requiere un cambio de backend y queda fuera de esta entrega.

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
las tres tienen que valer `cambiar123` en el primer despliegue (ver el paso 3).
El seed solo corre con la tabla de usuarios vacía: si la base ya tenía
usuarios, cambiar las variables no hace nada; hay que recrear la base.

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
| `PUT` | `/turnos/{id}/cancelar` | PACIENTE | Cancela su propio turno: un hold vuelve a `DISPONIBLE`; uno confirmado queda `CANCELADO`, con reembolso del copago y anulación del reclamo |
| `GET` | `/turnos/mios?fecha=YYYY-MM-DD` | PACIENTE, PROFESIONAL | Turnos del usuario autenticado: el paciente ve sus `EN_HOLD` y `CONFIRMADO`; el profesional, toda su agenda (`fecha` opcional filtra un día) |

`POST /turnos/disponibilidad` acepta `modalidad` (`PRESENCIAL` o
`TELEMEDICINA`, por defecto `PRESENCIAL`) y, solo para las presenciales,
`consultorio`. Los turnos que ya existían quedan como `PRESENCIAL` sin
migración: la columna se agrega con `DEFAULT 'PRESENCIAL'`.

`TurnoDTO` también expone los campos de cobertura (`coberturaAutorizada`,
`coberturaPorcentaje`, `copago`, `numeroAutorizacion`), que completa la
validación de cobertura al reservar (SCRUM-91).

### Pagos

No hay endpoint de cobro: el copago se cobra solo al confirmar el turno
(`CobroAlConfirmar`) y se devuelve solo al cancelar uno confirmado
(`ReembolsoAlCancelar`). Lo que queda en `/pagos`:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| `GET` | `/pagos/{id}` | paciente o profesional del turno, ADMINISTRADOR | Un pago; `404` si no existe |
| `GET` | `/pagos?turnoId=` | paciente o profesional del turno, ADMINISTRADOR | Los pagos de un turno |
| `PUT` | `/pagos/{id}/reembolso` | ADMINISTRADOR | Reembolsa por la pasarela un pago `APROBADO`; `409` si está en otro estado |

### Puntos de extensión del flujo de turnos

Obras sociales, pagos, telemedicina y el reclamo **no** se llaman desde
`ServicioDeTurnos`: se enganchan observando eventos CDI sincrónicos que
dispara el servicio, `TurnoEnReserva`, `TurnoEnConfirmacion` y, al cancelar un
turno confirmado, `TurnoEnCancelacion`. El orden lo fija
`turnos/negocio/PuntosDeExtension`:

| Evento | Prioridad | Qué | Card |
|---|---|---|---|
| `TurnoEnReserva` | `COBERTURA` (100) | Validar cobertura, completar copago | SCRUM-91 |
| `TurnoEnConfirmacion` | `COBRO_COPAGO` (100) | Cobrar el copago | SCRUM-93 |
| `TurnoEnConfirmacion` | `SALA_DE_VIDEO` (200) | Crear la sala si es telemedicina (`SalaAlConfirmar`) | SCRUM-95 |
| `TurnoEnConfirmacion` | `RECLAMO` (300) | Reservada: el reclamo lo encola un MDB suscripto al tópico (ver Facturación) | sin uso (MDB) |
| `TurnoEnCancelacion` | `ANULACION_RECLAMO` (100) | Anular el reclamo (`ReclamoAlCancelar`) | SCRUM-124 |
| `TurnoEnCancelacion` | `REEMBOLSO_COPAGO` (200) | Reembolsar el copago (`ReembolsoAlCancelar`); va último porque el rollback no lo deshace | SCRUM-124 |

Para sumar un paso:

```java
@Transactional(TxType.MANDATORY) // un observador CDI no es EJB: @TransactionAttribute no rige
public void alConfirmar(
        @Observes @Priority(PuntosDeExtension.COBRO_COPAGO) TurnoEnConfirmacion evento) {
    // ... si lanza una excepcion con rollback, la confirmacion entera se revierte
}
```

Siempre `@Observes`, nunca `@ObservesAsync`: un observador asincrónico corre
fuera de la transacción y su falla ya no podría frenar la confirmación. El
criterio completo está en `docs/documento-tecnico.md`, sección 9.2.

### Notificaciones

A diferencia de los puntos de extensión de arriba (sincrónicos, dentro de la
misma transacción), el recordatorio de turno confirmado es **asincrónico**:
`ServicioDeTurnos.confirmarTurno` publica un `MapMessage` en el tópico JMS
`java:/jms/topic/TurnoConfirmado` (campos `turnoId`, `pacienteId`,
`profesionalId`, `fechaHora`) dentro de la misma transacción que la
confirmación, pero **quien lo procesa no**: `NotificacionMDB`
(`notificaciones/presentacion`) lo consume en un hilo propio del contenedor,
después de que la transacción de `confirmarTurno` ya cerró. La respuesta HTTP
del `PUT /turnos/{id}/confirmar` no espera a que el mensaje se procese.

`NotificacionMDB` solo traduce el mensaje y delega en
`ServicioDeNotificaciones` (`@Stateless`), que arma el recordatorio, lo "envía"
(simulado por ahora: un log, porque no hay proveedor de email/SMS todavía — el
método `enviar()` es el punto de extensión pensado para un Strategy por canal
el día que lo haya) y persiste un registro de `Notificacion`, para que la demo
tenga evidencia de que el mensaje se consumió sin depender del log.

`GET /api/notificaciones/mias` (solo PACIENTE) devuelve las notificaciones del
paciente autenticado, para verlas desde Postman o el frontend sin entrar a la
base. El paciente sale del usuario logueado y no de un parámetro, así que nadie
puede pedir las de otro. Un profesional o un administrador reciben `403`: las
notificaciones se generan para el paciente del turno, y sin credenciales la
respuesta es `401`, como en el resto de la API.

#### Política de redelivery

`mediconecta-setup.cli` le da al tópico su propio address-setting
(`jms.topic.TurnoConfirmadoTopic`) en vez de dejarle el comodín (`#`) de
`standalone-full.xml`, que reentrega 10 veces sin demora y se agota con una
caída de la base de un segundo:

- **Hasta 10 entregas** (`max-delivery-attempts`), esperando 1 s, 2 s, 4 s…
  entre una y otra (`redelivery-delay=1000`, multiplicador 2, máximo 30 s).
- Agotados los intentos, el mensaje se mueve a la **cola de mensajes
  muertos** general (`jms.queue.DLQ`), en vez de perderse.

Si `NotificacionMDB.onMessage` lanza una excepción sin capturarla (por
ejemplo, porque la base no responde), el contenedor no confirma el mensaje:
Artemis lo reintenta solo, sin que el componente tenga que programar nada.
Verificar los mensajes en la DLQ, por CLI:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  "/subsystem=messaging-activemq/server=default/jms-queue=DLQ:count-messages"
```

O por la consola de administración: *Runtime → (tu servidor) → Messaging
(ActiveMQ) → default → Queue → DLQ*.

### Telemedicina

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| `POST` | `/telemedicina/turno/{turnoId}` | paciente o profesional del turno | Crea la sala del turno y responde con el enlace de quien la pidió: `201` si la creó, `200` si ya existía |
| `GET` | `/telemedicina/turno/{turnoId}` | paciente o profesional del turno | El enlace de quien pregunta: el profesional recibe el de anfitrión y el paciente el de invitado, nunca los dos. `404` si el turno no tiene una sala vigente |

Cualquier otro usuario, administrador incluido, recibe `403`, y también si el
turno no existe: así no se puede averiguar qué ids existen. Una sala deja de
ser vigente si el turno se cancela o pasa a otro paciente; desde ese momento
nadie recibe el enlace viejo. Solo un turno de
`TELEMEDICINA` tomado por un paciente (`EN_HOLD` o `CONFIRMADO`) puede tener
sala; uno presencial responde `409` sin llamar al proveedor. Si el proveedor no
responde, `503` con `Retry-After`.

**Al confirmar un turno de telemedicina la sala se crea sola** (SCRUM-95):
`SalaAlConfirmar` observa `TurnoEnConfirmacion` y llama a `crearSesion` dentro
de la misma transacción. La sala se crea al confirmar y nunca al reservar, así
que un hold que vence no deja salas colgadas; un turno presencial no llama al
proveedor. Si el proveedor de video no responde, **falla la confirmación
entera**: `PUT /turnos/{id}/confirmar` responde `503` con `Retry-After`, el
turno sigue `EN_HOLD` a nombre del paciente y puede reintentar mientras dure el
hold (ver `docs/documento-tecnico.md`, sección 9.4). El `POST` de arriba sigue
sirviendo para crearla a pedido, por ejemplo desde Postman.

**El proveedor de video simulado** (`externos/video`) es un tercero que vive en
el mismo WAR, como la pasarela de pago: `POST /api/externo/salas`, sin
autenticación, con `{"reference": "...", "scheduledAt": "..."}`. Devuelve
`roomId`, `hostUrl` (profesional) y `guestUrl` (paciente). Las salas son de
Jitsi Meet y los enlaces funcionan de verdad. En el simulador los dos enlaces
apuntan a la misma sala y solo cambian el nombre con el que entra cada uno: la
separación es de presentación, no de permisos. Un proveedor real daría enlaces
con permisos distintos (anfitrión e invitado); el componente ya trata los dos
como secretos separados. Para simular el proveedor caído,
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
| Reclamo ya `ENVIADO`, `EN_REVISION_MANUAL` o `ANULADO` | sin cambios | No: reentrega idempotente o turno cancelado |

Si el paciente cancela un turno confirmado, `ReclamoAlCancelar` pasa su
reclamo `PENDIENTE` a `ANULADO` y uno ya `ENVIADO` a `EN_REVISION_MANUAL`,
porque el legado no tiene una operación para anularlo.

**`@RunAs`.** `obtenerTurno` exige uno de los roles PACIENTE/PROFESIONAL/
ADMINISTRADOR y un MDB no tiene caller autenticado, así que
`TurnoConfirmadoFacturacionMDB` lleva `@RunAs(ServicioDeUsuarios.ROL_ADMINISTRADOR)`
para que el contenedor le propague esa identidad a la llamada. Funcionó sin
configuración adicional de Elytron contra este WildFly 41 (verificado en
ejecución).

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

# Y la confirma antes de que venza; si tiene copago, con una tarjeta de prueba
curl -u paciente@mediconecta.com:cambiar123 -X PUT \
     -H "Content-Type: application/json" \
     -d '{"tokenMedioDePago":"tok_visa_demo"}' \
     $BASE/turnos/1/confirmar
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
están en la carpeta `06` de `postman/MediConecta-Demo.postman_collection.json`;
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

## Componente en otra tecnología: `recordatorios/` (Node.js)

Además de los ocho componentes Java, `ServicioDeRecordatorios` está escrito en
TypeScript con NestJS y corre como un proceso aparte de WildFly. Se suscribe al
tópico `TurnoConfirmado` por AMQP 1.0 con una suscripción durable, programa un
recordatorio para 24 h antes de cada turno confirmado y lo expone en
`GET http://localhost:3000/recordatorios`.

El paso 6 de `deploy/mediconecta-setup.cli` abre el acceptor AMQP (puerto 5672)
y le da permisos al rol `recordatorios`. Falta crear el usuario de aplicación
(la clave es un ejemplo):

```bash
add-user.sh -a -u recordatorios -p recordatorios123 -g recordatorios    # Windows: add-user.bat
```

Cómo correrlo, el contrato del mensaje y la demo están en
[`recordatorios/README.md`](recordatorios/README.md). Por qué está en Node.js,
en [`docs/arquitectura.md`](docs/arquitectura.md).

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

- **Historia clínica sin pantalla.** El resto de las pantallas usa la API real,
  pero `HistoriaPage` todavía no está conectada: la ruta `/historia` queda
  deshabilitada hasta SCRUM-87.
- **Notificaciones sin canal real.** El envío de recordatorios es simulado (log);
  no hay proveedor de email/SMS integrado.
- **Pruebas de unidad sin contenedor.** Cubren las fachadas, los Adapter, los
  observadores y los simuladores con dobles; el cableado real (CDI, JMS, CXF,
  seguridad) se verifica por integración, con `deploy/smoke-test.sh` contra el
  sistema desplegado.
- **Un solo módulo Maven.** Los ocho componentes conviven en un WAR. Separarlos en
  módulos es lo que corresponde cuando se despliegan por separado, y todavía no es
  el caso.
- **Usuarios de prueba en el arranque.** `SeedDeUsuariosIniciales` crea un
  profesional y un paciente de ejemplo. Fuera de un entorno de desarrollo esos dos
  no deberían existir.

## Resiliencia ante sistemas externos

Los tres adaptadores externos (legado de obra social, pasarela de pago y proveedor de video) están protegidos con timeouts y un circuit breaker: tras 3 fallas seguidas se deja de llamar al sistema por 10 s y se responde al instante (`503` con `Retry-After` para el legado y el proveedor de video, `502` para la pasarela de pago); el resto de la plataforma sigue funcionando. Los reclamos usan reintentos y `ReclamosFacturacionDLQ`. Detalle en `docs/documento-tecnico.md` (sección 9.5). Para verlo en acción con el servidor desplegado: `bash deploy/demo-circuit-breaker.sh`.
