# Diagrama de secuencia: login y autorizacion por rol

El login valida email y contrasena contra la tabla `usuarios` y devuelve los datos del usuario. Desde ahi el frontend manda sus credenciales (HTTP Basic) en cada pedido, y el contenedor decide el acceso en dos capas: el `security-constraint` de `web.xml` (por URL) y `@RolesAllowed` sobre los servicios (por metodo).

Ejemplos usados: `GET /api/notificaciones/mias` (solo PACIENTE segun `web.xml`) y `PUT /api/turnos/{id}/confirmar` (`web.xml` deja pasar a cualquier rol autenticado, pero `ServicioDeTurnos.confirmarTurno` exige PACIENTE).

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend (SPA)
    participant AB as AutenticacionBasica
    participant IS as IdentityStoreHandler + ConfiguracionDeSeguridad
    participant WX as web.xml (security-constraint)
    participant RS as Resource (JAX-RS)
    participant SV as Servicio (@RolesAllowed)
    participant MP as AccesoDenegadoMapper

    Note over FE,SV: 1. Login (/api/usuarios/login no esta protegido)
    FE->>AB: POST /api/usuarios/login<br/>body: email y contrasena
    AB->>WX: el recurso esta protegido?
    WX-->>AB: no, se deja pasar (doNothing)
    AB->>RS: UsuariosResource.login(LoginDTO)
    RS->>SV: ServicioDeUsuarios.autenticar(email, contrasena)
    SV->>SV: UsuarioDAO.buscarPorEmail<br/>PasswordUtil.verificar(contrasena, hash)
    alt credenciales validas
        SV-->>RS: Usuario
        RS-->>FE: 200 UsuarioDTO
        FE->>FE: guardarSesion(sesion, Authorization Basic)<br/>en sessionStorage
    else usuario inexistente o contrasena incorrecta
        SV-->>RS: null
        RS-->>FE: 401 Email o contrasena incorrectos
    end

    Note over FE,SV: 2. Operacion protegida: GET /api/notificaciones/mias
    FE->>AB: GET /api/notificaciones/mias<br/>Authorization: Basic base64(email:contrasena)<br/>X-Requested-With: XMLHttpRequest
    AB->>IS: validate(UsernamePasswordCredential)
    IS->>IS: callerQuery (hash en tabla usuarios)<br/>groupsQuery (rol en tabla usuarios)
    alt sin credenciales o credenciales invalidas
        IS-->>AB: INVALID o no hay credencial
        AB-->>FE: 401 (sin WWW-Authenticate porque es pedido AJAX)
        Note right of AB: Un curl o Postman sin X-Requested-With<br/>si recibe WWW-Authenticate: Basic realm=MediConecta
        FE->>FE: cierra la sesion y vuelve al login
    else credenciales validas
        IS-->>AB: VALID con el rol como grupo
        AB->>WX: notifyContainerAboutLogin, se evalua el constraint
        alt el rol no esta en el security-constraint (PROFESIONAL o ADMINISTRADOR)
            WX-->>FE: 403 del contenedor
        else el rol esta habilitado (PACIENTE)
            WX->>RS: NotificacionesResource.misNotificaciones()
            RS->>SV: ServicioDeUsuarios.obtenerPorEmail<br/>ServicioDeNotificaciones.listarMias
            SV-->>RS: lista de Notificacion
            RS-->>FE: 200 lista de NotificacionDTO
        end
    end

    Note over FE,MP: 3. El 403 lo produce @RolesAllowed: PUT /api/turnos/{id}/confirmar con un PROFESIONAL
    FE->>AB: PUT /api/turnos/7/confirmar<br/>Authorization: Basic ...<br/>X-Requested-With: XMLHttpRequest
    AB->>WX: login valido (rol PROFESIONAL), /api/turnos/* admite los tres roles
    WX->>RS: TurnosResource.confirmar(7)
    RS->>SV: ServicioDeTurnos.confirmarTurno(7)
    alt el rol no coincide con @RolesAllowed(PACIENTE)
        SV--xRS: EJBAccessException
        RS->>MP: toResponse(EJBAccessException)
        MP-->>FE: 403 No tiene permisos para realizar esta operacion
    else el rol es PACIENTE
        SV-->>RS: Turno confirmado
        RS-->>FE: 200 TurnoDTO
    end
```

## Como leerlo

- **Login (pasos 1 a 8):** `UsuariosResource.login` delega en `ServicioDeUsuarios.autenticar`, que busca por email y compara con `PasswordUtil.verificar` (PBKDF2). Si algo no coincide devuelve `null` y el Resource responde 401. No hay token: el frontend guarda el encabezado `Authorization: Basic` en `sessionStorage` y lo reenvia.
- **Encabezados del frontend:** `api()` en `frontend/src/api.ts` manda siempre `X-Requested-With: XMLHttpRequest` y, si hay sesion, `Authorization`. El primero le avisa a `AutenticacionBasica` que es un pedido del SPA.
- **401 y el popup del navegador:** `AutenticacionBasica` omite `WWW-Authenticate` cuando llega `X-Requested-With: XMLHttpRequest`. Asi el navegador no muestra su popup nativo y el fetch maneja el 401. Si el recurso no esta protegido y las credenciales faltan o son invalidas, deja pasar el pedido (`doNothing`).
- **Quien valida:** `ConfiguracionDeSeguridad` declara el `DatabaseIdentityStore`: `callerQuery` trae `contrasenaHash` y `groupsQuery` trae `rol` de la tabla `usuarios`. El rol es el grupo que despues usan `web.xml` y `@RolesAllowed`.
- **Primera capa, por URL:** `web.xml` define quien entra a cada URL. `/api/notificaciones/*` solo admite PACIENTE: otro rol autenticado recibe 403 del contenedor, sin llegar al Resource.
- **Segunda capa, por metodo:** cuando el `web.xml` deja pasar a un rol pero el servicio no (por ejemplo PROFESIONAL en `confirmarTurno`), `@RolesAllowed` lanza `EJBAccessException` y `AccesoDenegadoMapper` la traduce a 403 en vez de dejar que salga como 500.
