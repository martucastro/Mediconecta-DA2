# Guía del código de MediConecta

Este documento explica el sistema pieza por pieza, en palabras simples. La idea es que puedas leerlo de corrido y después responder cualquier pregunta sobre cualquier parte, sin importar quién escribió qué.

Está escrito para leerse en orden, pero cada sección se entiende sola.

---

## 1. La idea de fondo, en una frase

MediConecta es un sistema donde **pacientes reservan turnos con profesionales de la salud** y donde **los profesionales registran lo que pasó en esa consulta** en una historia clínica.

Todo el sistema vive dentro de un servidor de aplicaciones llamado WildFly. Eso importa porque muchas cosas que parecen magia — que un turno se libere solo a los cinco minutos, que un paciente no pueda ver la historia de otro, que una operación se deshaga entera si falla la mitad — **no las programamos nosotros**: las declaramos con anotaciones y las ejecuta el servidor.

Esa es la decisión más importante de todo el proyecto. Si entendés eso, el resto es detalle.

---

## 2. El mapa: qué hay y dónde

El código son 45 archivos Java. Se agrupan en **tres componentes** más un puñado de piezas transversales.

```
ar.edu.uade.da2.mediconecta
│
├── usuarios/              ← quién es cada uno y qué puede hacer
│   ├── presentacion/      (5 archivos)
│   ├── negocio/           (6 archivos)
│   └── datos/             (2 archivos)
│
├── turnos/                ← reservar, confirmar, cancelar
│   ├── presentacion/      (4 archivos)
│   ├── negocio/           (4 archivos)
│   └── datos/             (3 archivos)
│
├── historiaclinica/       ← diagnósticos, recetas, antecedentes
│   ├── presentacion/      (3 archivos)
│   ├── negocio/           (7 archivos)
│   └── datos/             (7 archivos)
│
└── (raíz)                 ← 3 archivos que valen para todo el sistema
    ├── ApiActivator.java
    ├── AccesoDenegadoMapper.java
    └── ErrorInesperadoMapper.java
```

Fijate en el patrón: **cada componente tiene sus propias tres carpetas**. No hay una carpeta `controllers/` global con todos los controladores juntos y otra `services/` con todos los servicios. Eso es deliberado y lo explico en la sección siguiente.

---

## 3. Las tres capas y por qué están así

### Qué hace cada capa

**`presentacion`** — habla HTTP. Recibe el pedido que llega por la red, lo traduce a una llamada Java común, y traduce la respuesta de vuelta a HTTP. **No tiene ni una regla de negocio.**

**`negocio`** — acá viven las reglas. "Un turno solo se puede reservar si está disponible". "Un paciente solo puede ver su propia historia". "Una receta necesita un medicamento". Esta capa no sabe que existe HTTP.

**`datos`** — habla con PostgreSQL. Guarda, busca, actualiza. No decide nada.

### El criterio: por componente primero, por capa después

Hay dos formas de organizar un proyecto en capas:

```
OPCIÓN A (por capa)              OPCIÓN B (por componente)  ← la que usamos
presentacion/                    usuarios/
  UsuariosResource                 presentacion/
  TurnosResource                   negocio/
  HistoriaClinicaResource          datos/
negocio/                         turnos/
  ServicioDeUsuarios               presentacion/
  ServicioDeTurnos                 negocio/
  ...                              datos/
```

Elegimos la B por una razón concreta: **la consigna pide componentes, no una aplicación monolítica**. Un componente tiene que poder entenderse, tocarse y eventualmente desplegarse solo. Con la opción A, "el componente de turnos" no existe como cosa: está desparramado en tres carpetas junto a todo lo demás.

Con la opción B, si mañana hay que separar turnos en su propio despliegue, te llevás la carpeta `turnos/` entera y listo.

### La regla de oro: las flechas van en una sola dirección

```
presentacion  →  negocio  →  datos
```

Presentación llama a negocio. Negocio llama a datos. **Nunca al revés.** Un DAO jamás llama a un servicio, y un servicio jamás devuelve un objeto `Response` de HTTP.

Por qué importa: si negocio no sabe nada de HTTP, mañana podés invocar al mismo componente desde una cola de mensajes, desde un SOAP, o desde otro componente Java, y las reglas siguen valiendo igual. Si mezclás, tenés que reimplementar las reglas en cada canal nuevo.

### Cómo se hablan los componentes entre sí

Esta es la parte que más se suele romper en un TP. La regla que seguimos:

> **Un componente nunca toca la base de datos de otro componente. Le pide los datos a su fachada.**

Ejemplo real. `ServicioDeHistoriaClinica` necesita saber si el usuario 3 es realmente un paciente. Podría hacer un `SELECT rol FROM usuarios WHERE id = 3`. **No lo hace.** Le pregunta a `ServicioDeUsuarios`:

```java
@Inject
private ServicioDeUsuarios servicioDeUsuarios;   // la fachada del otro componente
```

Y en la entidad `HistoriaClinica` vas a ver esto, que a primera vista parece un error:

```java
// Se referencia al paciente por id y no con @ManyToOne: la tabla de usuarios
// pertenece al componente ServicioDeUsuarios, no a este.
@Column(unique = true, nullable = false)
private Long pacienteId;
```

Un `Long pacienteId` suelto, en vez de una relación `@ManyToOne Usuario paciente`. Es a propósito: una relación JPA ataría la tabla `historias_clinicas` a la tabla `usuarios` a nivel de base, y el componente de historia clínica dejaría de poder existir sin el de usuarios.

Ojo que **`Turno` sí usa `@ManyToOne Usuario`**. Esa es una inconsistencia real entre componentes, la anoto al final en la revisión.

---

## 4. Componente Usuarios

Es el más simple y el que sostiene a los otros dos, porque es el que sabe **quién es cada uno**.

### `datos/Usuario.java` — la entidad

Una clase con cuatro campos que se mapea a la tabla `usuarios`:

```java
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String email;
    private String rol;              // "PACIENTE", "PROFESIONAL" o "ADMINISTRADOR"
    private String contrasenaHash;   // nunca la contraseña real
}
```

- `@Entity` le dice a Hibernate "esta clase es una tabla".
- `@Id` marca la clave primaria.
- `@GeneratedValue(IDENTITY)` significa que el id lo pone PostgreSQL, no nosotros.
- El constructor vacío `public Usuario() {}` parece inútil pero **es obligatorio**: Hibernate crea el objeto vacío y después le rellena los campos.

El campo clave es `rol`. Ese string no es decorativo: **es literalmente el rol de seguridad** que después usa el servidor para decidir quién puede hacer qué. Por eso el registro público está limitado a crear pacientes, como vas a ver en un momento.

### `datos/UsuarioDAO.java` — el acceso a la base

DAO significa *Data Access Object*: un objeto cuyo único trabajo es hablar con la base.

```java
@Stateless
public class UsuarioDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Usuario usuario) {
        em.persist(usuario);
    }

    public Usuario buscarPorEmail(String email) {
        List<Usuario> resultado = em.createQuery(
                "SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email)
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }
}
```

Tres cosas que vale la pena que entiendas:

**`@PersistenceContext` no es una variable normal.** Nadie hace `em = new EntityManager()`. El servidor ve esa anotación y mete ahí una conexión viva a la base, ya atada a la transacción en curso. Se llama *inyección de dependencias*: vos declarás qué necesitás, el contenedor te lo da.

**El query no es SQL, es JPQL.** `SELECT u FROM Usuario u` habla de la **clase** `Usuario`, no de la tabla `usuarios`. Hibernate lo traduce a SQL real.

**`:email` es un parámetro, y eso evita SQL injection.** Si concatenáramos el string a mano, alguien podría mandar un email con un `'; DROP TABLE usuarios; --` adentro.

### `negocio/ServicioDeUsuarios.java` — la fachada

Acá están las reglas. La más importante es la del registro:

```java
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public Usuario registrarUsuario(String nombre, String email, String rol, String contrasenaPlana) {

    if (nombre == null || nombre.isBlank() || ... ) {
        throw new DatosInvalidosException("Todos los datos son obligatorios.");
    }

    if (!ROLES_VALIDOS.contains(rol)) {
        throw new DatosInvalidosException("Rol invalido: " + rol + ...);
    }

    if (!ROL_PACIENTE.equals(rol) && !contexto.isCallerInRole(ROL_ADMINISTRADOR)) {
        throw new EJBAccessException(
                "Solo un administrador puede crear usuarios con rol " + rol + ".");
    }

    Usuario existente = usuarioDAO.buscarPorEmail(email);
    if (existente != null) {
        throw new ConflictoDeNegocioException("Ya existe un usuario registrado con ese email.");
    }

    String hash = PasswordUtil.hash(contrasenaPlana);
    Usuario nuevoUsuario = new Usuario(nombre, email, rol, hash);
    usuarioDAO.guardar(nuevoUsuario);
    return nuevoUsuario;
}
```

Leelo de arriba abajo, son cuatro controles en orden de costo creciente:

1. **¿Vinieron todos los datos?** Lo más barato primero.
2. **¿El rol existe?** Se compara contra un `Set` de tres valores.
3. **¿Tenés permiso para pedir ese rol?** Cualquiera puede registrarse como PACIENTE. Para crear un PROFESIONAL o un ADMINISTRADOR hay que estar autenticado como administrador. **Sin esta línea, cualquiera se autoasigna el rol más alto del sistema** y se acabó la seguridad.
4. **¿El email ya está usado?** Este es el único que va a la base, por eso va último.

Recién después se hashea la contraseña y se guarda.

Hay un comentario arriba de la clase que explica algo que nos costó descubrir:

```java
// @PermitAll a nivel de clase: WildFly deniega por defecto todo metodo sin
// permiso declarado en cuanto el bean tiene alguna anotacion de seguridad
// (default-missing-method-permissions-deny-access). Sin esto, anotar solo
// listarUsuarios rompe el registro y el login.
@PermitAll
@Stateless
public class ServicioDeUsuarios {
```

Traducido: en cuanto una clase tiene **una sola** anotación de seguridad, WildFly bloquea todos los métodos que no declaren permiso explícito. Poner `@RolesAllowed("ADMINISTRADOR")` solo en `listarUsuarios` rompía el registro y el login sin ningún error claro. `@PermitAll` a nivel de clase abre todo, y los métodos que necesitan restricción la declaran individualmente.

### `negocio/PasswordUtil.java` — el hash de contraseñas

Esta clase se reescribió entera hace poco. Antes usaba SHA-256 pelado. Ahora usa PBKDF2.

```java
private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
private static final int ITERACIONES = 120_000;
private static final int BYTES_SALT = 16;
private static final int BITS_CLAVE = 256;

public static String hash(String contrasenaPlana) {
    byte[] salt = new byte[BYTES_SALT];
    ALEATORIO.nextBytes(salt);
    byte[] derivada = derivar(contrasenaPlana, salt, ITERACIONES);
    return ITERACIONES + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(derivada);
}
```

Por qué el cambio, en criollo:

**Problema 1: sin salt, dos contraseñas iguales dan el mismo resultado.** Cuando la base tenía SHA-256, los tres usuarios de prueba tenían la contraseña `cambiar123` y los tres hashes eran **idénticos**: `m4QZaPlSrNiYB8Ip...`. Alguien que robe la base ve tres filas iguales y sabe que las tres contraseñas son la misma. Peor: existen tablas precalculadas (*rainbow tables*) con el SHA-256 de millones de contraseñas comunes. Buscás el hash en la tabla y tenés la contraseña, sin calcular nada.

El salt son 16 bytes al azar distintos **para cada usuario**, que se mezclan con la contraseña antes de hashear. Ahora los mismos tres usuarios tienen hashes completamente distintos.

**Problema 2: SHA-256 es rápido, y eso acá es malo.** SHA-256 está diseñado para ser veloz. Una placa de video prueba miles de millones por segundo. PBKDF2 repite la operación 120.000 veces a propósito: para vos, iniciar sesión tarda unas décimas de segundo y no lo notás; para alguien probando millones de contraseñas, el ataque pasa a ser 120.000 veces más caro.

El formato guardado es `iteraciones:salt:hash`. Guardar el número de iteraciones **junto al hash** permite subirlo en el futuro sin invalidar las contraseñas viejas: cada una se verifica con el número con el que se creó.

Y la verificación:

```java
return MessageDigest.isEqual(esperada, calculada);
```

No es un `equals()` común, y no es capricho. Un `equals()` normal corta apenas encuentra el primer byte distinto. Eso significa que comparar un hash que falla en el byte 1 tarda menos que uno que falla en el byte 20 — y midiendo esa diferencia de tiempo, con suficientes intentos, se puede adivinar el hash byte por byte. `MessageDigest.isEqual` compara siempre todos los bytes, tarde lo que tarde.

### `negocio/HashDeContrasena.java` — el adaptador

Once líneas útiles, pero sin esto no funciona nada:

```java
@ApplicationScoped
public class HashDeContrasena implements PasswordHash {

    @Override
    public String generate(char[] contrasena) {
        return PasswordUtil.hash(new String(contrasena));
    }

    @Override
    public boolean verify(char[] contrasena, String hashGuardado) {
        return PasswordUtil.verificar(new String(contrasena), hashGuardado);
    }
}
```

El servidor tiene su propia idea de cómo verificar contraseñas. Nosotros tenemos la nuestra, en `PasswordUtil`. `PasswordHash` es la interfaz estándar que el servidor consulta; esta clase le dice "cuando quieras verificar, llamame a mí". Es el patrón **Adapter**: dos partes que no se entienden, y una pieza en el medio que traduce.

### `negocio/SeedDeUsuariosIniciales.java` — los usuarios del arranque

Resuelve un problema de huevo y gallina: el registro solo permite crear profesionales si ya estás autenticado como administrador, **y en una instalación nueva no hay ningún administrador**. Sin esta clase, una base limpia deja el sistema inutilizable.

```java
@Singleton
@Startup
public class SeedDeUsuariosIniciales {

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sembrar() {
        if (!usuarioDAO.listarTodos().isEmpty()) {
            LOGGER.info("Seed inicial: ya hay usuarios cargados, no se siembra nada.");
            return;
        }
        ...
    }
}
```

- `@Singleton` — una sola instancia en todo el sistema.
- `@Startup` — el servidor la crea al desplegar, sin esperar a que alguien la llame.
- `@PostConstruct` — el método corre apenas se crea la instancia.
- El `if` del principio la hace **idempotente**: si ya hay usuarios, no hace nada. Podés reiniciar mil veces sin duplicar nada.

Puede saltearse la regla de permisos porque corre **antes de que exista cualquier pedido HTTP**: no hay un usuario invocando a quien validarle permisos.

Las contraseñas ya no están escritas en el código. Salen de variables de entorno y, si faltan, se generan al azar:

```java
private String contrasenaDe(String variable, List<String> generadas) {
    String configurada = variableDeEntorno(variable);
    if (configurada != null) {
        return configurada;
    }
    byte[] material = new byte[12];
    ALEATORIO.nextBytes(material);
    String generada = Base64.getUrlEncoder().withoutPadding().encodeToString(material);
    generadas.add(variable + " = " + generada);
    return generada;
}
```

Una contraseña fija en el código fuente es la misma en todas las instalaciones del mundo **y está publicada en GitHub**. Una generada existe solo en esa instalación, y solo en el log de ese arranque.

### `presentacion/ConfiguracionDeSeguridad.java` — toda la autenticación

Esta clase **no tiene ni una línea de código**. Es solo anotaciones, y es toda la autenticación del sistema:

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

Lo que le estamos diciendo al servidor:

- **`@BasicAuthenticationMechanismDefinition`** — "pedí usuario y contraseña con HTTP Basic".
- **`callerQuery`** — "para saber la contraseña de alguien, corré este SELECT".
- **`groupsQuery`** — "para saber su rol, corré este otro".
- **`hashAlgorithm`** — "para comparar la contraseña, usá nuestra clase".

Y con eso el contenedor intercepta cada pedido protegido, pide credenciales, las valida y **publica el rol del usuario**. Ese rol publicado es lo que después habilita a `@RolesAllowed` a decidir.

Esto es exactamente lo que significó elegir Jakarta EE "por funcionalidad nativa": el login completo son cinco líneas declarativas, no un módulo de autenticación escrito a mano.

### `presentacion/UsuariosResource.java` — la puerta HTTP

```java
@Path("/usuarios")
@ApplicationScoped
public class UsuariosResource {

    @Inject
    private ServicioDeUsuarios servicio;

    @RolesAllowed("ADMINISTRADOR")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<UsuarioDTO> listar() {
        return servicio.listarUsuarios()
                .stream()
                .map(UsuarioDTO::new)
                .collect(Collectors.toList());
    }
```

- `@Path("/usuarios")` — esta clase atiende `/api/usuarios`.
- `@GET`, `@POST` — qué verbo HTTP atiende cada método.
- `@Produces(APPLICATION_JSON)` — "devolvé JSON".
- `@Inject` — otra vez inyección: nadie hace `new ServicioDeUsuarios()`.

Mirá el `.map(UsuarioDTO::new)`. Eso **no es decorativo**. Es lo que impide una filtración de seguridad.

### Los DTO y por qué existen

DTO es *Data Transfer Object*: un objeto que existe solo para viajar.

`Usuario` tiene el campo `contrasenaHash`. Si el resource devolviera el `Usuario` directo, la librería que convierte a JSON serializa **todos los getters públicos** — incluido `getContrasenaHash()` — y el hash de la contraseña de cada usuario viajaría por la red en la respuesta.

`UsuarioDTO` es una copia deliberadamente incompleta: id, nombre, email, rol. **El hash no está.** Esto ya pasó de verdad en el proyecto y por eso el smoke test tiene un caso que se llama "la respuesta NO filtra el hash".

El segundo motivo es más aburrido pero igual de importante: si mañana renombrás un campo de la entidad, la API no cambia. El DTO te desacopla la forma interna de la forma pública.

---

## 5. Componente Turnos

El más interesante de los tres, porque es el que tiene **estado conversacional**: una reserva que empieza, dura cinco minutos y termina.

### El ciclo de vida de un turno

```
DISPONIBLE ──reservar──► EN_HOLD ──confirmar──► CONFIRMADO
     ▲                      │
     │                      ├──cancelar──► CANCELADO
     └──────────────────────┘
        pasan 5 minutos sin confirmar
```

`EN_HOLD` significa "reservado provisoriamente". El paciente tiene cinco minutos para confirmar. Si no confirma, el turno **vuelve solo** a DISPONIBLE, sin que nadie toque nada.

### `datos/Turno.java` y `datos/EstadoTurno.java`

`EstadoTurno` es un enum con los cuatro valores de arriba. En la entidad:

```java
@Enumerated(EnumType.STRING)
private EstadoTurno estado;
```

`EnumType.STRING` guarda el texto `"EN_HOLD"` en la base. La alternativa, `EnumType.ORDINAL`, guardaría el número de posición (0, 1, 2, 3) — y si mañana alguien agrega un estado en el medio del enum, **todos los turnos de la base cambian de significado en silencio**. Nunca uses ORDINAL.

### `datos/TurnoDAO.java` — el acceso a datos, con un detalle importante

```java
public Turno buscarPorId(Long id) {
    return em.find(Turno.class, id);
}

/**
 * Busca el turno tomando un lock de escritura sobre la fila.
 */
public Turno buscarParaActualizar(Long id) {
    return em.find(Turno.class, id, LockModeType.PESSIMISTIC_WRITE);
}
```

Dos métodos casi iguales, y la diferencia es central.

Imaginá dos pacientes que tocan "reservar" sobre el mismo turno en el mismo instante:

```
Paciente A                          Paciente B
──────────                          ──────────
lee turno → DISPONIBLE
                                    lee turno → DISPONIBLE     ← ¡también!
valida: ok
                                    valida: ok                 ← ¡también!
escribe EN_HOLD (paciente A)
                                    escribe EN_HOLD (paciente B) ← pisa al primero
```

Los dos creen que tienen el turno. `PESSIMISTIC_WRITE` le dice a PostgreSQL "trabá esta fila hasta que yo termine". El paciente B queda esperando, y cuando por fin lee, lee `EN_HOLD` y su validación falla como corresponde.

**El lock vive en el DAO, no en el servicio.** Es una propiedad del acceso a datos: el componente de negocio pide "dame este turno para modificarlo" y no se entera de cómo se implementa. Y solo lo usan `reservar`, `confirmar` y `cancelar`. Las consultas de lectura siguen usando `buscarPorId` sin lock, porque no deciden nada y trabar filas de más frena el sistema al pedo.

### `negocio/ServicioDeTurnos.java` — la fachada stateful

```java
@Stateful
@RolesAllowed({ ROL_PACIENTE, ROL_PROFESIONAL, ROL_ADMINISTRADOR })
public class ServicioDeTurnos {
```

`@Stateful` significa que la instancia **recuerda cosas entre llamadas**. Es el requisito de la consigna que definió el stack entero: ni Spring ni Node tienen un equivalente nativo.

El método central:

```java
@RolesAllowed(ServicioDeUsuarios.ROL_PACIENTE)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public Turno reservarTurno(Long turnoId) {
    Usuario paciente = usuarioAutenticado();

    Turno turno = turnoDAO.buscarParaActualizar(turnoId);
    if (turno == null) {
        throw new DatosInvalidosException("No existe el turno " + turnoId);
    }
    if (turno.getEstado() != EstadoTurno.DISPONIBLE) {
        throw new ConflictoDeNegocioException(
                "El turno ya no esta disponible: esta " + turno.getEstado());
    }

    turno.setPaciente(paciente);
    turno.setEstado(EstadoTurno.EN_HOLD);
    turno.setInicioHold(LocalDateTime.now());
    turnoDAO.actualizar(turno);

    this.turnoEnCursoId = turnoId;
    expirador.programar(turnoId, ExpiradorDeHolds.DURACION_HOLD_MS);

    return turno;
}
```

Línea por línea:

**`usuarioAutenticado()`** — el paciente sale de quién está autenticado, **nunca de un parámetro**. Si el id del paciente viniera en el cuerpo del pedido, cualquiera podría reservar a nombre de otro cambiando un número.

**`buscarParaActualizar`** — el lock del que hablamos.

**Dos excepciones distintas.** "No existe el turno" es un dato malo del cliente → 400. "El turno ya está reservado" es un conflicto con el estado actual → 409. Son situaciones distintas y el cliente necesita distinguirlas.

**`this.turnoEnCursoId = turnoId`** — acá se ve el stateful: el bean recuerda qué turno está reteniendo.

**`expirador.programar(...)`** — le pide al contenedor que programe la liberación automática.

Y la regla que no se puede declarar con una anotación:

```java
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

`@RolesAllowed` sabe responder "este usuario es PACIENTE". **No sabe responder "este turno es suyo"**, porque la anotación no ve los argumentos del método. Las reglas que dependen del dato concreto hay que escribirlas a mano. Es exactamente el mismo criterio que usa historia clínica.

### `negocio/ExpiradorDeHolds.java` — la pieza más interesante del sistema

Primero, por qué existe separado:

```java
/**
 * La especificacion de Jakarta Enterprise Beans NO permite crear timers sobre un
 * stateful session bean. El javadoc de jakarta.ejb.TimerService lo dice de forma
 * explicita: el servicio de timers habilita a "stateless session beans, singleton
 * session beans, message-driven beans y entity beans 2.x". Los stateful quedan
 * afuera. Tener el TimerService adentro de ServicioDeTurnos compilaba, pero
 * fallaba en tiempo de ejecucion al crear el primer timer.
 */
@Singleton
@PermitAll
public class ExpiradorDeHolds {
```

Esto vale la pena que lo entiendas bien porque es una pregunta de defensa oral casi segura: el temporizador **no puede** estar adentro de `ServicioDeTurnos`. La especificación no lo permite. Compilaba perfecto y explotaba al ejecutar.

Y usa **dos mecanismos** que se complementan:

**Mecanismo 1 — el temporizador por turno (preciso):**

```java
public void programar(Long turnoId, long duracionMs) {
    cancelar(turnoId);
    timerService.createSingleActionTimer(duracionMs, new TimerConfig(turnoId, false));
}

@Timeout
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public void expirar(Timer timer) {
    liberar((Long) timer.getInfo(), "temporizador");
}
```

Se crea un temporizador de 5 minutos y el id del turno viaja como "info" del temporizador. Cuando vence, el contenedor llama solo a `expirar`. **Nadie hace un pedido HTTP. Nadie corre un hilo.** El servidor lo dispara.

Acá hay una trampa que casi nos come:

```java
public void cancelar(Long turnoId) {
    for (Timer timer : timerService.getTimers()) {
        if (turnoId.equals(timer.getInfo())) {
            timer.cancel();
        }
    }
}
```

`getTimers()` devuelve **todos los temporizadores de este bean**, no los de una conversación ni los de un turno. Si canceláramos todos, al confirmar un turno liberaríamos los holds de todos los demás pacientes. Por eso hay que filtrar por el `info` que guardamos al programarlos.

**Mecanismo 2 — el barrido periódico (durable):**

```java
@Schedule(hour = "*", minute = "*", second = "0", persistent = false)
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public void barrerHoldsVencidos() {
    LocalDateTime limite = LocalDateTime.now().minusNanos(DURACION_HOLD_MS * 1_000_000);
    List<Turno> vencidos = turnoDAO.listarHoldsVencidos(limite);
    for (Turno turno : vencidos) {
        liberar(turno.getId(), "barrido");
    }
}
```

`@Schedule(second = "0")` significa "corré cada minuto, en el segundo 0".

Por qué hace falta si ya está el temporizador: **el temporizador no es persistente**. Si WildFly se reinicia con turnos retenidos, la fila de la base sobrevive pero la tarea programada no. Sin el barrido, esos turnos quedaban `EN_HOLD` para siempre.

Los dos juntos cubren riesgos distintos: el temporizador da **precisión** (libera exactamente a los 5 minutos), el barrido da **durabilidad** (sobrevive a un reinicio, con hasta un minuto de retraso).

`REQUIRES_NEW` en ambos: son tareas del contenedor, no vienen de una transacción previa, así que abren la suya propia.

### `presentacion/TurnosResource.java` — HTTP con traducción de errores

Lo distintivo de este resource es el helper `responder`:

```java
@POST
public Response reservar(ReservaTurnoRequest reserva) {
    return responder(() -> Response.ok(
            new TurnoDTO(servicio.reservarTurno(reserva.getTurnoId()))).build());
}

private Response responder(Operacion operacion) {
    try {
        return operacion.ejecutar();
    } catch (DatosInvalidosException e) {
        return error(Response.Status.BAD_REQUEST, e.getMessage());
    } catch (ConflictoDeNegocioException e) {
        return error(Response.Status.CONFLICT, e.getMessage());
    }
}

@FunctionalInterface
private interface Operacion {
    Response ejecutar();
}
```

La sintaxis `() -> ...` es una lambda: se le pasa el código a ejecutar **como si fuera un dato**, y `responder` lo corre envuelto en el try/catch. Así el try/catch se escribe una sola vez y no cuatro.

Y hay un comentario largo arriba de la inyección que explica una decisión deliberada:

```java
// Decisión de diseño (no un descuido): sin scope explícito, CDI inyecta este
// stateful bean como @Dependent (...) el hold es durable en la fila de Turno
// (estado EN_HOLD + inicioHold en la DB), no conversacional en el bean.
```

Traducido: el hold **no vive en la memoria del bean**, vive en la base de datos. Por eso un turno se puede confirmar desde otra pestaña, desde otro dispositivo, o incluso después de reiniciar el servidor. Si el hold viviera solo en memoria, se perdería.

---

## 6. Componente Historia Clínica

El más grande, y el único que expone una **interfaz explícita**.

### `negocio/ServicioDeHistoriaClinicaLocal.java` — el contrato

```java
@Local
public interface ServicioDeHistoriaClinicaLocal {
    HistoriaClinica crearHistoria(Long pacienteId);
    HistoriaClinica obtenerHistoriaDePaciente(Long pacienteId);
    EntradaClinica agregarEntrada(Long pacienteId, NuevaEntradaDTO datos);
    HistoriaClinica registrarConsulta(Long pacienteId, RegistrarConsultaDTO datos);
    List<EntradaClinica> listarEntradas(Long pacienteId);
}
```

Es la única cara visible del componente. Los DAO, el factory y la implementación quedan escondidos detrás de estas cinco operaciones. Esto es **exactamente** lo que la consigna pide con "componentes con su interfaz explícita y documentada".

Vale la pena notar: los otros dos componentes **no tienen interfaz**, se inyecta la clase concreta. Lo anoto en la revisión.

### `datos/` — herencia en una sola tabla

Hay una entidad abstracta y tres hijas:

```java
@Entity
@Table(name = "entradas_clinicas")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class EntradaClinica {
```

Antecedente, Diagnóstico y Receta comparten **una sola tabla**, y una columna `tipo` dice cuál es cuál. La alternativa sería tres tablas separadas con un JOIN. Para tres tipos que se leen siempre juntos, una tabla es más simple y más rápida.

Y en `HistoriaClinica`:

```java
@OneToMany(mappedBy = "historia", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.EAGER)
private List<EntradaClinica> entradas = new ArrayList<>();
```

- **`cascade = ALL`** — si guardás la historia, se guardan sus entradas.
- **`orphanRemoval`** — si sacás una entrada de la lista, se borra de la base.
- **`fetch = EAGER`** — trae las entradas junto con la historia, siempre.

`EAGER` es discutible: si una historia tuviera miles de entradas, traerlas todas siempre sería un problema. Para el alcance actual está bien, pero es el tipo de cosa que se revisa cuando el volumen crece.

### `negocio/EntradaClinicaFactory.java` — el patrón Factory

```java
public EntradaClinica crear(NuevaEntradaDTO datos, HistoriaClinica historia, Long profesionalId) {
    if (datos == null) { throw new DatosInvalidosException(...); }
    if (datos.getTipo() == null) { throw new DatosInvalidosException(...); }

    return switch (datos.getTipo()) {
        case ANTECEDENTE -> crearAntecedente(datos, historia, profesionalId);
        case DIAGNOSTICO -> crearDiagnostico(datos, historia, profesionalId);
        case RECETA      -> crearReceta(datos, historia, profesionalId);
    };
}
```

El factory hace **dos cosas** que de otro modo quedarían mezcladas en la fachada:

1. Decide qué subclase instanciar.
2. Valida los campos obligatorios **de ese tipo en particular**.

El punto 2 es la clave. Una receta sin medicamento es inválida; un diagnóstico sin medicamento es perfectamente normal. Sin el factory, eso sería un `if` gigante lleno de casos dentro del servicio.

Y el helper de validación:

```java
private String exigir(String valor, String campo, TipoEntrada tipo, int largoMaximo) {
    if (valor == null || valor.isBlank()) {
        throw new DatosInvalidosException(
                "Una entrada de tipo " + tipo + " requiere el campo '" + campo + "'.");
    }
    String normalizado = valor.trim();
    if (normalizado.length() > largoMaximo) {
        throw new DatosInvalidosException("El campo '" + campo + "' admite hasta "
                + largoMaximo + " caracteres y recibio " + normalizado.length() + ".");
    }
    return normalizado;
}
```

El control de largo se valida contra **la misma constante que declara la columna**. Si faltara, un texto muy largo llegaría al INSERT, PostgreSQL lo rechazaría, y el cliente recibiría un 500 — cuando en realidad es un dato inválido suyo y corresponde un 400.

### `negocio/ServicioDeHistoriaClinica.java` — transacción y autorización

El método más representativo:

```java
@Override
@RolesAllowed("PROFESIONAL")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public HistoriaClinica registrarConsulta(Long pacienteId, RegistrarConsultaDTO datos) {
    if (datos == null) { throw new DatosInvalidosException(...); }
    if (datos.getDiagnostico() == null) { throw new DatosInvalidosException(...); }
    validarPaciente(pacienteId);
    validarProfesional(datos.getProfesionalId());

    HistoriaClinica historia = obtenerOCrearHistoria(pacienteId);

    datos.getDiagnostico().setTipo(TipoEntrada.DIAGNOSTICO);
    EntradaClinica diagnostico = factory.crear(datos.getDiagnostico(), historia, datos.getProfesionalId());
    entradaDAO.guardar(diagnostico);
    historia.agregarEntrada(diagnostico);

    if (datos.getRecetas() != null) {
        for (NuevaEntradaDTO datosReceta : datos.getRecetas()) {
            datosReceta.setTipo(TipoEntrada.RECETA);
            EntradaClinica receta = factory.crear(datosReceta, historia, datos.getProfesionalId());
            entradaDAO.guardar(receta);
            historia.agregarEntrada(receta);
        }
    }
    return historia;
}
```

**Acá está la transacción declarativa**, que es un requisito de la consigna.

Una consulta guarda un diagnóstico y varias recetas. Supongamos un diagnóstico y tres recetas, y que la tercera receta no tiene medicamento. El factory tira `DatosInvalidosException` en la tercera. ¿Qué pasa con el diagnóstico y las dos recetas que ya se guardaron?

**Se deshacen.** Las tres. Automáticamente. Porque `@TransactionAttribute(REQUIRED)` abrió una transacción al entrar al método, y la excepción la marca para rollback. **Nosotros no escribimos ni un `begin`, ni un `commit`, ni un `rollback`.**

La historia nunca queda con una consulta a medio registrar. Eso, en un sistema médico, es la diferencia entre un registro confiable y basura.

Y la autorización que depende del dato:

```java
private void verificarQuePuedeLeerLaHistoria(Long pacienteId) {
    if (contexto.isCallerInRole(ROL_PROFESIONAL)
            || contexto.isCallerInRole(ROL_ADMINISTRADOR)) {
        return;
    }
    String emailDelCaller = contexto.getCallerPrincipal().getName();
    Usuario solicitante = servicioDeUsuarios.obtenerPorEmail(emailDelCaller);

    if (solicitante == null || !pacienteId.equals(solicitante.getId())) {
        throw new EJBAccessException("Un paciente solo puede consultar su propia historia clinica.");
    }
}
```

Un profesional o un administrador pueden leer cualquier historia: lo necesitan para atender. Un paciente **solo la propia**. Mismo criterio que en turnos, misma razón: `@RolesAllowed` no ve los argumentos.

---

## 7. Las piezas transversales

### `ApiActivator.java`

```java
@ApplicationPath("/api")
public class ApiActivator extends Application {
}
```

Tres líneas. Le dice al servidor "todo lo REST cuelga de `/api`". Por eso las URLs son `/mediconecta/api/turnos` y no `/mediconecta/turnos`.

### `AccesoDenegadoMapper.java`

```java
@Provider
public class AccesoDenegadoMapper implements ExceptionMapper<EJBAccessException> {
    @Override
    public Response toResponse(EJBAccessException excepcion) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity("No tiene permisos para realizar esta operacion")
                .type("text/plain")
                .build();
    }
}
```

Cuando `@RolesAllowed` rechaza una llamada, el contenedor tira `EJBAccessException`. Sin este mapper esa excepción llega cruda a la capa web y el cliente recibe un **500** — o sea, "el servidor se rompió" — cuando en realidad **la seguridad funcionó perfecto** y corresponde un 403.

### `ErrorInesperadoMapper.java`

```java
@Provider
public class ErrorInesperadoMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable excepcion) {
        if (excepcion instanceof WebApplicationException webException) {
            return webException.getResponse();
        }
        LOGGER.log(Level.SEVERE, "Error no controlado procesando la peticion", excepcion);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error interno del servidor. Consulte el log para el detalle.")
                .build();
    }
}
```

La red de contención. Sin esto, el mensaje crudo de la excepción viaja al cliente: en un error de Hibernate eso incluye **el nombre de la tabla, la definición de las columnas y el SQL del INSERT**. Es información que le sirve a un atacante. El detalle queda en el log del servidor, que es donde tiene que estar.

### `WEB-INF/web.xml` — la seguridad de transporte

```xml
<security-constraint>
    <web-resource-collection>
        <web-resource-name>Historia clinica</web-resource-name>
        <url-pattern>/api/historias/*</url-pattern>
    </web-resource-collection>
    <auth-constraint>
        <role-name>PROFESIONAL</role-name>
        <role-name>PACIENTE</role-name>
        <role-name>ADMINISTRADOR</role-name>
    </auth-constraint>
</security-constraint>
```

**Hay dos niveles de seguridad y hacen cosas distintas:**

| Nivel | Dónde | Qué decide |
|---|---|---|
| Transporte | `web.xml` | ¿Estás autenticado? ¿Tenés *alguno* de estos roles? |
| Componente | `@RolesAllowed` | ¿Podés hacer *esta operación puntual*? |

`web.xml` es el portón: filtra por URL antes de que el pedido llegue al código. `@RolesAllowed` es la puerta de cada oficina.

Fijate el detalle del listado de usuarios:

```xml
<url-pattern>/api/usuarios</url-pattern>
<http-method>GET</http-method>
```

Solo se restringe el **GET**. El POST sobre la misma URL es el registro público: si lo cerráramos, no habría forma de crear el primer usuario.

### `META-INF/persistence.xml`

```xml
<persistence-unit name="mediconectaPU" transaction-type="JTA">
    <jta-data-source>java:/MediConectaDS</jta-data-source>
    <properties>
        <property name="hibernate.hbm2ddl.auto" value="update"/>
        <property name="hibernate.show_sql" value="true"/>
    </properties>
</persistence-unit>
```

- **`mediconectaPU`** — el nombre que aparece en cada `@PersistenceContext(unitName = "mediconectaPU")`.
- **`JTA`** — las transacciones las maneja el servidor, no el código.
- **`java:/MediConectaDS`** — el pool de conexiones configurado en WildFly.
- **`hbm2ddl.auto = update`** — Hibernate crea y actualiza las tablas solo, a partir de las entidades. Cómodo para desarrollo; en producción se usan migraciones versionadas.

---

## 8. Cómo se comunican: dos recorridos completos

### Recorrido 1: un paciente reserva un turno

```
POST /mediconecta/api/turnos
Authorization: Basic cGFjaWVudGVAbWVkaWNvbmVjdGEuY29tOmNhbWJpYXIxMjM=
{ "turnoId": 3 }
```

1. **WildFly** ve que la URL entra en el `security-constraint` de `/api/turnos`. Pide autenticación.
2. **El contenedor** decodifica el header Basic, saca `paciente@mediconecta.com` y la contraseña.
3. **`ConfiguracionDeSeguridad`** le dice qué SELECT correr: busca el `contrasenaHash` de ese email.
4. **`HashDeContrasena.verify()`** delega en `PasswordUtil.verificar()`, que corre PBKDF2 120.000 veces con el salt guardado y compara en tiempo constante.
5. Coincide. El contenedor corre `groupsQuery`, obtiene el rol `PACIENTE`, y lo **publica** en el contexto de seguridad.
6. **`TurnosResource.reservar()`** recibe el pedido. JSON-B convirtió el cuerpo en un `ReservaTurnoRequest`.
7. Llama a `servicio.reservarTurno(3)` envuelto en `responder(...)`.
8. **El contenedor intercepta** la llamada al EJB. Ve `@RolesAllowed(PACIENTE)`, compara con el rol publicado en el paso 5. Pasa.
9. **Abre una transacción** por `@TransactionAttribute(REQUIRED)`.
10. **`ServicioDeTurnos`** resuelve quién es el paciente vía `contexto.getCallerPrincipal()` → le pregunta a `ServicioDeUsuarios` (fachada del otro componente) → obtiene el `Usuario`.
11. **`TurnoDAO.buscarParaActualizar(3)`** → PostgreSQL **traba la fila 3**.
12. Valida: existe, está DISPONIBLE. Si no, tira la excepción que corresponda.
13. Cambia estado a EN_HOLD, guarda el paciente y la hora del hold.
14. **`ExpiradorDeHolds.programar(3, 300000)`** → el contenedor agenda un temporizador.
15. El método termina. **El contenedor hace commit** y libera el lock de la fila.
16. **`new TurnoDTO(turno)`** copia solo los campos públicos.
17. JSON-B lo convierte a JSON. HTTP 200.

**Cinco minutos después, sin ningún pedido HTTP:**

18. El contenedor dispara `ExpiradorDeHolds.expirar(timer)`.
19. Abre una transacción nueva (`REQUIRES_NEW`).
20. Lee el turno con lock, verifica que sigue EN_HOLD, lo vuelve a DISPONIBLE.
21. Commit. El turno está libre otra vez.

**Y si el servidor se reinició en el medio:** el temporizador se perdió, pero `barrerHoldsVencidos()` corre cada minuto, encuentra el turno vencido en la base y lo libera igual.

### Recorrido 2: un profesional registra una consulta

```
POST /mediconecta/api/historias/paciente/3/consultas
{ "profesionalId": 2, "diagnostico": {...}, "recetas": [{...}, {...}] }
```

1–5. Igual que antes, pero el rol publicado es `PROFESIONAL`.
6. **`HistoriaClinicaResource.registrarConsulta()`** recibe el pedido.
7. Llama a la **interfaz** `ServicioDeHistoriaClinicaLocal`, no a la clase.
8. El contenedor valida `@RolesAllowed("PROFESIONAL")` y **abre la transacción**.
9. Valida que el paciente 3 exista y tenga rol PACIENTE — preguntándole a `ServicioDeUsuarios`, nunca consultando la tabla directo.
10. Valida lo mismo del profesional 2.
11. `obtenerOCrearHistoria(3)` — si no tiene historia, la crea.
12. **`factory.crear(diagnostico, ...)`** → valida campos y devuelve un `Diagnostico`.
13. Lo guarda y lo agrega a la historia.
14. Para cada receta, lo mismo.
15. **Si alguna receta es inválida**, el factory tira `DatosInvalidosException` → `@ApplicationException(rollback = true)` → **el contenedor deshace todo lo de los pasos 12 a 14**.
16. Si todo salió bien, commit. HTTP 201 con la historia completa.
17. Si hubo excepción, el `catch` la traduce a 400 o 409.

---

## 9. Los tres patrones, y por qué cada uno

### DAO — Data Access Object

**Dónde:** `UsuarioDAO`, `TurnoDAO`, `HistoriaClinicaDAO`, `EntradaClinicaDAO`.

**Qué resuelve:** que el `EntityManager` no se desparrame por toda la aplicación. Toda consulta vive en un DAO.

**Qué te compra concretamente:** cuando hubo que agregar el lock pesimista, se agregó **un método en `TurnoDAO`**. Los tres lugares que reservan, confirman y cancelan cambiaron una línea cada uno. Sin DAO, el `em.find(...)` estaría repetido en cada servicio y habría que cambiarlo en todos lados.

### Facade — Fachada

**Dónde:** `ServicioDeUsuarios`, `ServicioDeTurnos`, `ServicioDeHistoriaClinica`.

**Qué resuelve:** una sola puerta de entrada por componente. Los DAO, el factory y el expirador quedan detrás.

**Qué te compra:** `ServicioDeHistoriaClinica` necesita datos de usuarios. Sin fachada, haría un SELECT sobre `usuarios` y ahí se acabaría la separación entre componentes — mañana cambiás algo en la tabla de usuarios y rompés historia clínica sin enterarte. Con fachada, el contrato es explícito.

### Factory — Fábrica

**Dónde:** `EntradaClinicaFactory`.

**Qué resuelve:** decidir qué subclase crear y validar lo específico de cada tipo.

**Qué te compra:** agregar un cuarto tipo de entrada (una derivación, un estudio) se resuelve **en el factory**, sin tocar la fachada. Sin el factory, sería un `switch` gigante metido en el medio del servicio, mezclando "orquestar la consulta" con "saber qué campos necesita una receta".

---

## 10. Glosario

| Término | En criollo |
|---|---|
| **Contenedor** | El servidor de aplicaciones (WildFly) visto como "el que corre y administra tu código" |
| **EJB** | Una clase que el contenedor administra: le da transacciones, seguridad y ciclo de vida |
| **`@Stateless`** | Cada llamada es independiente; el contenedor reusa instancias de un pool |
| **`@Stateful`** | La instancia recuerda cosas entre llamadas |
| **`@Singleton`** | Una sola instancia en todo el sistema |
| **Inyección de dependencias** | Vos declarás qué necesitás (`@Inject`), el contenedor te lo da armado |
| **JPA / Hibernate** | JPA es el estándar para mapear clases a tablas; Hibernate es la implementación que usamos |
| **JPQL** | Como SQL pero hablando de clases Java en vez de tablas |
| **JAX-RS / RESTEasy** | JAX-RS es el estándar REST de Jakarta; RESTEasy es la implementación de WildFly |
| **DTO** | Objeto que existe solo para viajar por la red, con los campos que se pueden mostrar |
| **Transacción** | Un bloque de operaciones que se aplican todas o ninguna |
| **Rollback** | Deshacer una transacción |
| **Lock pesimista** | Trabar una fila para que nadie más la toque hasta terminar |
| **Hash** | Transformación irreversible: de la contraseña sale el hash, del hash no sale la contraseña |
| **Salt** | Dato aleatorio por usuario que se mezcla con la contraseña antes de hashear |
| **Caller principal** | Quién está autenticado haciendo este pedido |

---

## 11. Si te preguntan en la defensa

Las cinco respuestas que conviene tener a mano:

**¿Por qué Jakarta EE?** Por el componente stateful. La consigna pide una conversación con estado entre llamadas, y `@Stateful` lo resuelve de forma nativa. En Spring o Node habría que armarlo a mano con sesiones o una caché externa.

**¿Por qué el temporizador está en `ExpiradorDeHolds` y no en `ServicioDeTurnos`?** Porque la especificación de Jakarta Enterprise Beans no permite timers sobre un stateful session bean. Está en el javadoc de `TimerService`. Compilaba, pero fallaba al ejecutar.

**¿Dónde está la transacción declarativa?** En `registrarConsulta`. Guarda un diagnóstico y N recetas; si una receta es inválida, el contenedor deshace todo. No hay un solo `commit` escrito a mano.

**¿Cómo se separan las capas?** Cada componente tiene sus tres carpetas. Las flechas van en una dirección: presentación → negocio → datos. Negocio no sabe que existe HTTP; datos no decide nada.

**¿Cómo se comunican los componentes?** Siempre por la fachada del otro, nunca tocando su tabla. Por eso `HistoriaClinica` guarda un `Long pacienteId` en vez de una relación JPA a `Usuario`.
