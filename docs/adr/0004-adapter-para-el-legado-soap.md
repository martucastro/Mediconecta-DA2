# ADR 0004: Adapter propio para el legado SOAP de la obra social

- **Estado:** Aceptada
- **Fecha:** 2026-09-28 (decisión); registrada como ADR el 2026-11-17

> **En una línea:** el negocio habla con la obra social a través del port `SistemaDeObraSocial`, que solo conoce tipos de dominio. La única implementación, `SistemaDeObraSocialSoap`, encierra JAX-WS, fija timeouts de 2 s y 5 s y clasifica lo que vuelve: un fault `Client` se traduce en `400` y cualquier otra falla en `503`.

## Contexto

- La obra social es un sistema legado de un tercero con un contrato SOAP impuesto. El WSDL se publica en `/mediconecta/legado/obrasocial?wsdl` y hay una copia en `docs/obrasocial.wsdl`; el legado está simulado en `externos/obrasocial/ObraSocialLegado`. Tiene tres operaciones (`validarCobertura`, `autorizarPrestacion` y `presentarReclamo`) y su vocabulario es el de la obra social: DNI, número de afiliado y códigos de prestación.
- Lo usan dos componentes:
  - la reserva, de forma síncrona, porque la cobertura define el copago (ADR 0003);
  - facturación, de forma asincrónica, al presentar el reclamo (ADR 0002).
- Lo que vuelve del legado tiene tres desenlaces que el negocio tiene que distinguir:
  1. "Sin cobertura" es una respuesta válida: el afiliado existe y su plan no cubre.
  2. Un pedido inválido (afiliado inexistente, un DNI que no corresponde al afiliado o una prestación desconocida) vuelve como SOAP Fault de código `Client`. Reintentarlo no sirve.
  3. Un legado caído, lento o con una falla interna no dice nada sobre el pedido. Se puede reintentar.
- Un legado que acepta la conexión y no contesta retiene el hilo del pedido y, con él, la transacción y la fila del turno.

## Decisión

1. **Port de dominio.** `SistemaDeObraSocial` (`obrassociales.negocio`) solo recibe y devuelve tipos de dominio (`Prestacion`, `Cobertura`, `ResultadoPresentacion`) y falla con excepciones de negocio. `ServicioDeObrasSociales` (`@Stateless`) lo inyecta y no sabe que del otro lado hay SOAP.
2. **Una sola implementación, encerrada en un subpaquete.** `SistemaDeObraSocialSoap` (`@ApplicationScoped`) vive en `obrassociales.negocio.soap`, junto con el SEI escrito a mano `ObraSocialLegadoPort` (mismos nombres y namespace que el WSDL) y los espejos JAXB `RespuestaCoberturaXml` y `RespuestaReclamoXml`. Ningún tipo SOAP sale de ese paquete.
3. **El cliente no depende del legado para existir.** `Service.create` arma el cliente sin descargar el WSDL; el contrato sale de las anotaciones del SEI. Se crea un puerto por llamada (`nuevoPuerto`), porque el request context de un proxy JAX-WS no es seguro entre hilos.
4. **Timeouts explícitos.** El request context de cada llamada lleva 2 s de conexión y 5 s de respuesta. La URL y los timeouts son propiedades de sistema (`mediconecta.obrasocial.url`, `mediconecta.obrasocial.timeoutConexionMs` y `mediconecta.obrasocial.timeoutRespuestaMs`) que se leen en cada llamada, así que se cambian con `jboss-cli` sin redesplegar.
5. **Clasificación de lo que vuelve:**

| Lo que vuelve del legado | Qué produce el Adapter | Respuesta HTTP |
|---|---|---|
| Una respuesta, con o sin cobertura | `Cobertura`, con `autorizada = false` si no cubre | La del caso de uso |
| SOAP Fault `Client` (`Sender` en SOAP 1.2) | `DatosInvalidosException` | `400` (`DatosInvalidosMapper`) |
| Fault `Server` o cualquier otro código, conexión rechazada, timeout o respuesta vacía | `ObraSocialNoDisponibleException`, con un mensaje apto para mostrar; el detalle técnico queda en el log | `503` (`ObraSocialNoDisponibleMapper`) |

   Las dos excepciones son `@ApplicationException(rollback = true)`: si el legado no responde al reservar, la reserva entera se revierte y el turno sigue `DISPONIBLE`.
6. **Transacciones según lo que persiste cada operación.** `validarCobertura` y `presentarReclamo` son `NOT_SUPPORTED`: no persisten nada local y no retienen una transacción durante una llamada remota. `autorizarPrestacion` y `cotizarReserva` son `REQUIRED`: guardan la autorización en `autorizaciones_prestacion`, dentro de la transacción de la reserva.

## Alternativas consideradas

### A. Llamar a los stubs del WSDL directamente desde el código de negocio

Usar el cliente generado (o el SEI) desde `ServicioDeObrasSociales`, `CoberturaEnLaReserva` o facturación. Se descartó por cuatro motivos:

- **Los tipos SOAP se filtran.** `SOAPFaultException`, `WebServiceException` y la respuesta del contrato llegarían al negocio y a sus llamadores. Cada llamador tendría que clasificar los faults por su cuenta, y dos clasificaciones distintas le dirían al usuario cosas distintas ante la misma falla.
- **Mezcla dos motivos de cambio.** La regla de negocio (cuándo autorizar, qué guardar) y la forma de hablar con este proveedor quedarían en la misma clase.
- **Un cambio de protocolo se propaga.** Si el legado migrara a REST, el cambio llegaría a la fachada y a sus consumidores, en lugar de quedar en una clase.
- **Las pruebas necesitarían JAX-WS.** Hoy solo `nuevoPuerto` lo necesita, y `SistemaDeObraSocialSoapTest` lo reemplaza con un spy y un `SOAPFault` simulado.

### B. Construir el cliente descargando el WSDL del legado

Se descartó porque crear el cliente ya dependería de que el legado esté vivo, y esa descarga ocurriría fuera de los timeouts configurados.

### C. Usar los timeouts por defecto de CXF

Se descartó porque un legado que acepta la conexión y no contesta retendría el hilo, la transacción y la fila del turno mucho más allá de lo que un paciente espera.

### D. Tratar todos los faults igual

Se descartó porque cualquiera de las dos versiones engaña:

- Si todo fault fuera "pedido inválido", una falla interna del legado le diría al usuario que sus datos están mal, y no reintentaría.
- Si todo fault fuera "no disponible", un dato mal cargado se reintentaría sin sentido. En facturación, además, un rechazo definitivo gastaría los cinco intentos de la cola en lugar de pasar directo a `EN_REVISION_MANUAL`.

### E. Con el legado caído, reservar como particular con un aviso

Se descartó por tres motivos (SCRUM-91):

- Le mostraría al paciente un copago que puede no ser el real.
- El cobro al confirmar le haría pagar el valor total a un afiliado.
- Obligaría a atrapar la excepción dentro del componente para que la transacción no quedara marcada para rollback.

## Consecuencias

### Positivas

- **SOAP queda en un solo lugar.** Fuera de `externos/obrasocial` y `obrassociales/negocio/soap`, ningún archivo importa `jakarta.xml.ws`, `jakarta.jws`, `jakarta.xml.soap` ni `jakarta.xml.bind`. Se verifica con un `grep` de los imports.
- **Hay una sola traducción, de SOAP al dominio.** Facturación reutiliza la fachada y traduce a su propio port en `CanalDeReclamosSoap`: `DatosInvalidosException` pasa a `ReclamoRechazadoException` y `ObraSocialNoDisponibleException` pasa a `CanalDeReclamosNoDisponibleException`. Esa distinción es la que alimenta los reintentos de la cola (ADR 0002).
- **La espera está acotada.** Cada llamada espera como mucho 7 s, y la caída se puede simular apuntando la URL a un puerto cerrado.
- **El patrón se repite.** Es el mismo que usan pagos (`PasarelaDePagoAdapter`) y telemedicina (`ProveedorDeVideoAdapter`).

### Negativas

- **El SEI se mantiene a mano.** Tiene que coincidir con el WSDL sin ayuda del compilador: un cambio en el contrato no rompe el build y se descubre en ejecución.
- **La clasificación depende del legado.** Si el legado reportara una falla interna con código `Client`, el usuario vería un `400`.
- **Con el legado caído, nadie reserva** (`503`) hasta que vuelva. Es una decisión de producto (alternativa E).
- **La reserva espera con la fila bloqueada.** La transacción puede esperar al legado hasta 7 s, aunque el hold arranca después y no le resta tiempo al paciente.
- **La autorización remota no se compensa.** El legado no tiene una operación de anulación. Si la reserva se revierte después de autorizar, o el hold vence, la autorización emitida en la obra social queda. Un timeout después de que el legado autorizó deja una autorización remota sin fila local.

## Referencias en el código

- [`obrassociales/negocio/SistemaDeObraSocial.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/SistemaDeObraSocial.java)
- [`obrassociales/negocio/soap/SistemaDeObraSocialSoap.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/soap/SistemaDeObraSocialSoap.java)
- [`obrassociales/negocio/soap/ObraSocialLegadoPort.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/soap/ObraSocialLegadoPort.java), [`RespuestaCoberturaXml.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/soap/RespuestaCoberturaXml.java) y [`RespuestaReclamoXml.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/soap/RespuestaReclamoXml.java)
- [`obrassociales/negocio/ServicioDeObrasSociales.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/ServicioDeObrasSociales.java) y [`ObraSocialNoDisponibleException.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/ObraSocialNoDisponibleException.java)
- [`ObraSocialNoDisponibleMapper.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/ObraSocialNoDisponibleMapper.java) y [`DatosInvalidosMapper.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/DatosInvalidosMapper.java)
- [`facturacion/negocio/CanalDeReclamosSoap.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/facturacion/negocio/CanalDeReclamosSoap.java)
- [`externos/obrasocial/ObraSocialLegado.java`](../../src/main/java/ar/edu/uade/da2/mediconecta/externos/obrasocial/ObraSocialLegado.java) y [`docs/obrasocial.wsdl`](../obrasocial.wsdl)
- [`SistemaDeObraSocialSoapTest.java`](../../src/test/java/ar/edu/uade/da2/mediconecta/obrassociales/negocio/soap/SistemaDeObraSocialSoapTest.java)
- Documento técnico, [secciones 6.2, 6.3 y 8.4](../documento-tecnico.md)
