# Diagrama de secuencia: reserva de turno con cobertura de obra social

Entrega 2 (16/11). Para integrar en `docs/documento-tecnico.md` (junto a la sección 9.2).

Cubre `reservarTurno` -> `CoberturaEnLaReserva` -> `ServicioDeObrasSociales` -> `SistemaDeObraSocialSoap` -> `ObraSocialLegado` (WSDL), con el copago resultante y los dos caminos de error: legado sin respuesta (503) y `soap:Client` (400).

Ejemplo numérico: afiliado `OS-2002`, plan `PLAN_MEDIO` (70 %), prestación `CONSULTA` (arancel 20000.00). Copago = 20000 x (100 - 70) / 100 = **6000.00**.

```mermaid
sequenceDiagram
    autonumber
    actor P as Paciente
    participant ST as ServicioDeTurnos
    participant CR as CoberturaEnLaReserva
    participant SO as ServicioDeObrasSociales
    participant AD as SistemaDeObraSocialSoap
    participant LG as ObraSocialLegado<br/>(SOAP / WSDL)

    P->>ST: reservarTurno(turnoId)
    Note over ST: @Transactional REQUIRED<br/>turno DISPONIBLE, setPaciente()
    ST->>CR: fire(TurnoEnReserva) [evento CDI sincrónico]
    CR->>SO: cotizarReserva(pacienteId, CONSULTA)
    Note over SO: valida paciente y busca afiliación (DNI, nro. afiliado)

    alt Paciente sin obra social registrada
        SO-->>CR: Cobertura(no autorizada, 0 %, copago = arancel, sin autorización)
    else Paciente con obra social
        SO->>AD: autorizar(dni, numeroAfiliado, CONSULTA)
        AD->>LG: SOAP autorizarPrestacion(dni, numeroAfiliado, "CONSULTA")<br/>timeouts: conexión 2 s, respuesta 5 s

        alt Camino feliz: el legado responde
            LG-->>AD: respuesta(autorizado=true, 70 %, copago=6000.00, "AUT-OS-2002-CONSULTA")
            AD-->>SO: Cobertura (traducida a dominio)
            SO->>SO: guardar AutorizacionDePrestacion
            SO-->>CR: Cobertura(autorizada, 70 %, copago 6000.00, nro. autorización)
        else Legado no responde (timeout o conexión rechazada)
            LG--xAD: sin respuesta (WebServiceException)
            AD->>AD: log WARNING con la causa técnica
            AD-->>SO: ObraSocialNoDisponibleException
            SO-->>CR: ObraSocialNoDisponibleException (rollback)
            CR-->>ST: ObraSocialNoDisponibleException (sin atrapar)
            ST-->>P: HTTP 503 {"error": "El sistema de la obra social no respondió..."}
            Note over ST,P: ObraSocialNoDisponibleMapper. Rollback de toda la reserva:<br/>el turno sigue DISPONIBLE y no queda hold
        else Legado rechaza el pedido: soap:Client
            LG-->>AD: SOAP Fault (faultcode soap:Client)<br/>p. ej. "No existe el afiliado" o DNI que no corresponde
            AD-->>SO: DatosInvalidosException("La obra social rechazó el pedido: ...")
            SO-->>CR: DatosInvalidosException
            CR-->>ST: DatosInvalidosException
            ST-->>P: HTTP 400 (DatosInvalidosMapper)
            Note over AD: Otros faults (soap:Server) se tratan como no disponible: ver el camino del 503
        end
    end

    Note over CR,ST: Solo si hubo respuesta
    CR->>CR: turno.setCoberturaAutorizada / setCoberturaPorcentaje / setCopago / setNumeroAutorizacion
    CR-->>ST: observer termina sin error
    ST->>ST: turno EN_HOLD, inicioHold = ahora, programar expiración (5 min)
    ST-->>P: 200 Turno (copago = 6000.00)
```

## Notas para el documento técnico

- `CoberturaEnLaReserva` observa `TurnoEnReserva` (prioridad `PuntosDeExtension.COBERTURA`); `ServicioDeTurnos` no la conoce.
- El observer corre dentro de la transacción de la reserva (`TxType.MANDATORY`) y no atrapa nada: cualquier excepción revierte la reserva entera.
- `SistemaDeObraSocialSoap` es el único que conoce JAX-WS. Clasifica los faults por código: `Client` (SOAP 1.1) o `Sender` (1.2) -> `DatosInvalidosException`; cualquier otro fault o `WebServiceException` -> `ObraSocialNoDisponibleException`.
- Para la demo del timeout: el afiliado `OS-5005` hace que el legado demore 30 s, más que el timeout de respuesta (5 s).
- Para la demo de `soap:Client`: un número de afiliado inexistente o un DNI que no corresponde.
- El legado vive en `/mediconecta/legado/obrasocial?wsdl`. Los timeouts y la URL se pueden cambiar con las propiedades `mediconecta.obrasocial.*`.
