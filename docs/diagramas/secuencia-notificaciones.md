# Diagrama de secuencia: notificacion de turno confirmado

Al confirmar un turno, `ServicioDeTurnos` publica un evento en el topico JMS `TurnoConfirmado` (Artemis). `NotificacionMDB` lo consume y delega en `ServicioDeNotificaciones`, que guarda el recordatorio en la tabla `notificaciones` y lo "envia" (hoy simulado con un log). El paciente despues consulta sus notificaciones por REST.

El publicador no conoce al consumidor: solo sabe el nombre JNDI del topico (`java:/jms/topic/TurnoConfirmado`).

```mermaid
sequenceDiagram
    autonumber
    participant ST as ServicioDeTurnos
    participant TP as Topico TurnoConfirmado (Artemis)
    participant MDB as NotificacionMDB
    participant SN as ServicioDeNotificaciones
    participant DAO as NotificacionDAO (tabla notificaciones)
    participant FE as Paciente / Frontend
    participant RS as NotificacionesResource

    Note over ST,TP: Publicacion, dentro de la transaccion de confirmarTurno
    ST->>ST: confirmarTurno(turnoId)<br/>estado = CONFIRMADO
    ST->>ST: publicarTurnoConfirmado(turno)
    ST->>TP: MapMessage<br/>turnoId, pacienteId, profesionalId, fechaHora
    Note right of ST: El mensaje solo sale si la transaccion hace commit

    Note over TP,DAO: Consumo
    TP->>MDB: onMessage(mensaje)
    alt mensaje mal formado (no es MapMessage, faltan claves o no se puede leer)
        MDB->>MDB: log WARNING
        Note right of MDB: Se descarta sin reintento,<br/>reintentar no lo arregla
    else mensaje valido
        MDB->>SN: notificarTurnoConfirmado(turnoId, pacienteId, fechaHora)
        SN->>DAO: existePorTurno(turnoId)
        alt ya existe (existePorTurno = true)
            DAO-->>SN: true
            SN->>SN: log INFO, se ignora el mensaje repetido
            Note right of SN: Idempotencia: JMS entrega al menos una vez
        else no existe todavia
            DAO-->>SN: false
            SN->>DAO: guardar(Notificacion turnoId, pacienteId, LOG, mensaje)
            SN->>SN: enviar(pacienteId, mensaje)<br/>simulado con log
        end
    end

    opt falla la base de datos
        DAO--xSN: excepcion
        SN--xMDB: la excepcion sube
        MDB--xTP: el mensaje no se confirma
        TP->>MDB: reintento (hasta 10, sin demora)
        Note right of TP: Agotados los reintentos,<br/>el mensaje va a la DLQ
    end

    Note over FE,DAO: Consulta posterior del paciente
    FE->>RS: GET /api/notificaciones/mias<br/>Authorization: Basic ...
    RS->>SN: ServicioDeUsuarios.obtenerPorEmail(caller)<br/>ServicioDeNotificaciones.listarMias(pacienteId)
    SN->>DAO: buscarPorPaciente(pacienteId)
    DAO-->>SN: lista de Notificacion (mas nuevas primero)
    SN-->>RS: lista
    RS-->>FE: 200 lista de NotificacionDTO
```

## Como leerlo

- **Publicacion atomica:** `publicarTurnoConfirmado` corre dentro de la misma transaccion JTA que `confirmarTurno`. Si hay rollback, el mensaje nunca sale: no se puede confirmar sin publicar ni publicar sin confirmar.
- **Formato del mensaje:** `MapMessage` con `turnoId`, `pacienteId` y `profesionalId` como long, y `fechaHora` como texto ISO-8601. El MDB solo usa `turnoId`, `pacienteId` y `fechaHora`.
- **El MDB solo traduce:** valida y lee el `MapMessage` y delega en el servicio, igual que un Resource traduce HTTP. La suscripcion al topico es no durable, porque publicador y consumidor viven en el mismo WAR.
- **Mensaje mal formado vs. falla de infraestructura:** un mensaje que no se puede leer se descarta con un WARNING. Una falla del servicio (por ejemplo la base caida) deja subir la excepcion: el contenedor no confirma el mensaje y Artemis lo reintenta hasta 10 veces, y despues lo manda a la DLQ.
- **Idempotencia:** como la entrega es "al menos una vez", `existePorTurno` evita notificar dos veces el mismo turno. El registro se guarda antes de enviar, asi una reentrega no vuelve a enviar.
- **Lectura:** `GET /api/notificaciones/mias` es solo para PACIENTE (ver `secuencia-login-roles.md`). El usuario sale del caller principal, nunca de un parametro.
