# Interfaces de los componentes de negocio

Cada componente de negocio publica una interfaz `@Local` llamada `<Bean>Local`, en su paquete `negocio`. Es el único contrato que conocen sus consumidores: recursos REST, MDB, observadores CDI y las fachadas de otros componentes. Todos inyectan la interfaz, nunca la clase: un bean con una interfaz `@Local` pierde su vista sin interfaz.

- **Roles:** se declaran sobre el bean con `@RolesAllowed` o `@PermitAll`. Un caller sin el rol recibe `EJBAccessException`, que `AccesoDenegadoMapper` traduce a HTTP 403.
- **Transacciones:** se declaran sobre el bean con `@TransactionAttribute`. Sin anotación, el atributo es `REQUIRED`.
- **Detalle:** cada operación documenta en el Javadoc de su interfaz qué hace, quién la invoca, su transacción y sus excepciones.

| Componente | Interfaz | Tipo de bean | Consumidores |
|---|---|---|---|
| Usuarios | `ServicioDeUsuariosLocal` | `@Stateless` | `UsuariosResource` y las fachadas de los demás componentes |
| Turnos | `ServicioDeTurnosLocal` | `@Stateful` | `TurnosResource`, Pagos, Telemedicina, Facturación |
| Obras sociales | `ServicioDeObrasSocialesLocal` | `@Stateless` | `AfiliacionesResource`, `CoberturaEnLaReserva`, `CanalDeReclamosSoap` |
| Pagos | `ServicioDePagosLocal` | `@Stateless` | `PagosResource`, `CobroAlConfirmar`, `ReembolsoAlCancelar` |
| Telemedicina | `ServicioDeTelemedicinaLocal` | `@Stateless` | `TelemedicinaResource`, `SalaAlConfirmar` |
| Historia clínica | `ServicioDeHistoriaClinicaLocal` | `@Stateless` | `HistoriaClinicaResource` |
| Notificaciones | `ServicioDeNotificacionesLocal` | `@Stateless` | `NotificacionMDB`, `NotificacionesResource` |
| Facturación | `ServicioDeFacturacionLocal` | `@Stateless` | `TurnoConfirmadoFacturacionMDB`, `ReclamoMDB`, `ReclamoAlCancelar`, `ReclamosResource` |

"Autenticado" significa cualquiera de los tres roles: PACIENTE, PROFESIONAL o ADMINISTRADOR.

## Usuarios: `ServicioDeUsuariosLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `registrarUsuario` | Público para PACIENTE; otros roles, solo ADMINISTRADOR | REQUIRED |
| `autenticar` | Público | REQUIRED |
| `obtenerUsuario` | Público (uso interno entre componentes) | REQUIRED |
| `consultarUsuario` | Autenticado; un no administrador, solo su propio id | REQUIRED |
| `obtenerPorEmail` | Público (uso interno entre componentes) | REQUIRED |
| `listarUsuarios` | ADMINISTRADOR | REQUIRED |
| `listarProfesionales` | Público | REQUIRED |

## Turnos: `ServicioDeTurnosLocal` (stateful)

Mantiene el turno en curso de la conversación reservar → confirmar/cancelar. El hold es durable en la fila del turno, y su expiración la programa `ExpiradorDeHolds`.

| Operación | Roles | Transacción |
|---|---|---|
| `consultarDisponibilidad` | Sin restricción en el componente | REQUIRED |
| `obtenerTurno` | Autenticado | REQUIRED |
| `obtenerTurnoParaActualizar` | Autenticado | MANDATORY (lock de escritura) |
| `misTurnos` | PACIENTE, PROFESIONAL | REQUIRED |
| `abrirDisponibilidad` | PROFESIONAL | REQUIRED |
| `reservarTurno` | PACIENTE | REQUIRED (incluye la cotización de la cobertura) |
| `confirmarTurno` | PACIENTE | REQUIRED (cobro, sala y mensaje JMS en la misma transacción) |
| `cancelarTurno` | PACIENTE | REQUIRED (reembolso y anulación del reclamo en la misma transacción) |

## Obras sociales: `ServicioDeObrasSocialesLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `cotizarReserva` | Sin restricción (la invoca la reserva) | REQUIRED |
| `presentarReclamo` | Sin restricción (la invoca facturación) | NOT_SUPPORTED (llamada SOAP) |
| `registrarAfiliacion` | ADMINISTRADOR | REQUIRED |
| `obtenerMiAfiliacion` | PACIENTE | SUPPORTS |

## Pagos: `ServicioDePagosLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `cobrar` | PACIENTE, ADMINISTRADOR | REQUIRED |
| `consultarPago` | Autenticado; salvo ADMINISTRADOR, solo el paciente o el profesional del turno | REQUIRED |
| `listarPorTurno` | Autenticado; salvo ADMINISTRADOR, solo el paciente o el profesional del turno | REQUIRED |
| `reembolsar` | ADMINISTRADOR | REQUIRED |
| `reembolsarCopagoDelTurno` | PACIENTE, ADMINISTRADOR | REQUIRED (se une a `cancelarTurno`) |

## Telemedicina: `ServicioDeTelemedicinaLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `crearSesion` | PACIENTE o PROFESIONAL del turno | REQUIRED (se une a `confirmarTurno`) |
| `obtenerEnlace` | PACIENTE o PROFESIONAL del turno | SUPPORTS |

## Historia clínica: `ServicioDeHistoriaClinicaLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `crearHistoria` | PROFESIONAL | REQUIRED |
| `obtenerHistoriaDePaciente` | Autenticado; un paciente, solo la suya | REQUIRED |
| `agregarEntrada` | PROFESIONAL | REQUIRED |
| `registrarConsulta` | PROFESIONAL | REQUIRED (diagnóstico y recetas, atómico) |
| `listarEntradas` | Autenticado; un paciente, solo las suyas | REQUIRED |

## Notificaciones: `ServicioDeNotificacionesLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `notificarTurnoConfirmado` | Sin restricción (la invoca un MDB) | REQUIRED (se une a la entrega del mensaje) |
| `listarMias` | PACIENTE | REQUIRED |

## Facturación: `ServicioDeFacturacionLocal` (stateless)

| Operación | Roles | Transacción |
|---|---|---|
| `registrarReclamo` | Sin restricción (la invoca un MDB) | REQUIRED (se une a la entrega del mensaje) |
| `procesarReclamo` | Sin restricción (la invoca un MDB) | REQUIRED (se une a la entrega del mensaje) |
| `anularReclamo` | Sin restricción (la invoca el observador de la cancelación) | REQUIRED (se une a `cancelarTurno`) |
| `listarReclamos` | ADMINISTRADOR | REQUIRED |

## Operaciones públicas fuera de la interfaz

Algunos métodos públicos del bean no forman parte del contrato, porque ningún consumidor los invoca:

- **`ServicioDeTurnos`:** `confirmarTurno(Long)`, la variante sin token, y `getTurnoEnCursoId`.
- **`ServicioDeObrasSociales`:** `validarCobertura` y `autorizarPrestacion`, que solo usa el propio bean.

Sin vista sin interfaz, nadie fuera del bean puede invocarlos.
