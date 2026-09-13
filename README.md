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

Arrancá el servidor **con el perfil full**, que es el que incluye la mensajería
que vamos a necesitar en las próximas entregas:

```bash
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml     # Linux y macOS
%WILDFLY_HOME%\bin\standalone.bat -c standalone-full.xml   # Windows
```

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
cp target/mediconecta-usuarios.war $WILDFLY_HOME/standalone/deployments/
```

Listo cuando el log dice:

```
WFLYSRV0010: Deployed "mediconecta-usuarios.war"
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

| Correo | Rol | Contraseña |
|---|---|---|
| `admin@mediconecta.com` | ADMINISTRADOR | `cambiar123` |
| `profesional@mediconecta.com` | PROFESIONAL | `cambiar123` |
| `paciente@mediconecta.com` | PACIENTE | `cambiar123` |

La del administrador se puede sobreescribir con la variable de entorno
`MEDICONECTA_ADMIN_PASSWORD`. Las otras dos son fijas: son usuarios de prueba y
no deberían existir fuera de un entorno de desarrollo.

---

## La API

Base: `http://localhost:8080/mediconecta-usuarios/api`

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
BASE=http://localhost:8080/mediconecta-usuarios/api

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

- **Reserva concurrente.** Dos pacientes que reserven el mismo turno en el mismo
  instante pueden pisarse: la lectura y la escritura del estado no están bajo un
  lock pesimista ni hay `@Version`.
- **Holds tras un reinicio.** El timer del hold es no persistente. Si WildFly se
  reinicia con turnos retenidos, esos turnos quedan `EN_HOLD` hasta que alguien
  los toque. La fila sobrevive; la expiración programada no.
- **Errores de negocio.** Los conflictos del componente de turnos salen como 500
  en vez de 409, a diferencia de historia clínica, que sí los traduce.
- **Contraseñas.** SHA-256 sin salt. Alcanza para el alcance de la materia, pero
  no es lo que corresponde fuera de un TP.
