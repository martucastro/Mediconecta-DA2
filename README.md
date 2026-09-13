# MediConecta

Plataforma que conecta pacientes con profesionales de salud independientes y
clínicas para turnos presenciales y telemedicina.

Trabajo Práctico Integrador de Desarrollo de Aplicaciones II, comisión Lunes TM.

---

## Qué hay implementado

Tres componentes de negocio, cada uno con su arquitectura en capas:

| Componente | Tipo | Responsabilidad |
|---|---|---|
| `ServicioDeUsuarios` | `@Stateless` | Registro, autenticación, perfiles y credenciales |
| `ServicioDeTurnos` | `@Stateful` | Disponibilidad, reserva, hold de 5 minutos, confirmación |
| `ServicioDeHistoriaClinica` | `@Stateless` | Antecedentes, diagnósticos y recetas |

```
ar.edu.uade.da2.mediconecta
  usuarios/{presentacion, negocio, datos}
  turnos/{presentacion, negocio, datos}
  historiaclinica/{presentacion, negocio, datos}
```

---

## Requisitos

| | Versión usada | De dónde |
|---|---|---|
| JDK | 17 o superior | cualquier distribución |
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

Arrancá el servidor **con el perfil full**, que es el que incluye la mensajería
que vamos a necesitar en las próximas entregas:

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

El script instala el driver, crea el datasource `java:/MediConectaDS` y ajusta
la integración de Jakarta Security. Es idempotente: se puede correr de nuevo sin
romper nada.

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

### Ejemplo del flujo completo

```bash
BASE=http://localhost:8080/mediconecta/api

# El profesional abre una franja
curl -u profesional@mediconecta.com:cambiar123 \
     -H "Content-Type: application/json" \
     -d '{"fechaHora":"2026-09-18T14:30:00"}' \
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
Se arrancó con `standalone.xml` en vez de `standalone-full.xml`.

---

## Limitaciones conocidas

Están acá a propósito: son decisiones de alcance de esta entrega, no descuidos.

- **Sin frontend.** El sistema se ejerce por HTTP, con las colecciones de Postman
  de `deploy/` y `postman/`. La primera entrega evalúa la arquitectura de capas y
  los componentes de negocio, no la interfaz.
- **Sin pruebas automatizadas de unidad.** La verificación es de integración, con
  `deploy/smoke-test.sh` contra el sistema desplegado.
- **Un solo módulo Maven.** Los tres componentes conviven en un WAR. Separarlos en
  módulos es lo que corresponde cuando se despliegan por separado, y todavía no es
  el caso.
- **Usuarios de prueba en el arranque.** `SeedDeUsuariosIniciales` crea un
  profesional y un paciente de ejemplo. Fuera de un entorno de desarrollo esos dos
  no deberían existir.
