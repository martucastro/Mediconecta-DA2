# Prueba de carga: reserva de turnos (SCRUM-119)

Script de [k6](https://k6.io/) que mide el flujo de reserva de `ServicioDeTurnos`, el componente stateful del sistema, primero con una instancia de WildFly y después con el componente escalado a dos.

## Qué mide

Cada usuario virtual (VU) es un paciente que repite este flujo:

| Paso | Request | Etiqueta (`operacion`) |
| --- | --- | --- |
| 1 | `GET /api/turnos?profesionalId=...` | `disponibilidad` |
| 2 | `POST /api/turnos` con `{ "turnoId": ... }`: el turno queda `EN_HOLD` y se programa su temporizador | `reservar` |
| 3 | `PUT /api/turnos/{id}/cancelar`: suelta el hold y cancela el temporizador | `cancelar` |

El flujo cancela en vez de confirmar a propósito: confirmar cobra el copago y crea salas de video, y eso mediría la pasarela y el proveedor de video, no el componente de turnos. Cancelar además evita dejar cientos de holds y temporizadores vivos al terminar.

**Datos sin choques entre VUs.** Antes de medir, `setup()` hace dos cosas:
- **Pacientes:** registra un paciente por VU (`carga-<corrida>-<n>@mediconecta.test`).
- **Franjas:** con el profesional, abre una franja por cada iteración de cada VU, todas en horarios distintos.

El VU *n* solo reserva sus propias franjas. Por eso un `409` en la reserva es un error real del sistema y no dos VUs peleando por el mismo turno. Los emails y los horarios llevan el id de la corrida, así que se puede correr varias veces contra la misma base.

**Los pacientes de carga no tienen obra social.** Reservan como particulares y `CoberturaEnLaReserva` no llama al legado SOAP. Se mide el componente de turnos, no la integración con la obra social.

## Requisitos

- **k6** instalado ([guía de instalación](https://grafana.com/docs/k6/latest/set-up/install-k6/)). En Windows: `winget install k6 --source winget`. En macOS: `brew install k6`.
- **MediConecta desplegado** y andando (ver el README principal). Conviene pasar antes `bash deploy/smoke-test.sh --rapido`.
- **La contraseña del profesional del seed.** Por defecto se usa `profesional@mediconecta.com` / `cambiar123`. Si al sembrar se usó otra, o la generó el seed, se pasa con `PROF_PASSWORD`.

## Cómo correrlo

```bash
# Corrida corta para comprobar que todo anda (2 VUs x 3 iteraciones)
k6 run -e VUS=2 -e ITERACIONES=3 load-tests/reserva-turnos.js

# Medición: 20 pacientes x 25 reservas cada uno = 500 reservas
k6 run -e VUS=20 -e ITERACIONES=25 \
  --summary-export=load-tests/resultados/1-instancia-20vus.json \
  load-tests/reserva-turnos.js
```

En Windows (cmd), cambiá `\` por `^` al final de cada línea, o escribí todo en una sola línea. Antes de usar `--summary-export`, creá la carpeta `load-tests/resultados/`.

| Variable | Default | Para qué |
| --- | --- | --- |
| `VUS` | `10` | Pacientes simultáneos |
| `ITERACIONES` | `20` | Reservas por paciente (`setup` abre `VUS x ITERACIONES` franjas) |
| `BASE_URLS` | `http://localhost:8080/mediconecta/api` | Una o varias instancias, separadas por coma |
| `PROF_EMAIL` / `PROF_PASSWORD` | `profesional@mediconecta.com` / `cambiar123` | Profesional que abre las franjas |
| `DURACION_MAXIMA` | `10m` | Corta la medición si se cuelga |

`setup()` no entra en las métricas que importan: sus requests llevan la etiqueta `operacion:setup`, y los umbrales solo miran `disponibilidad`, `reservar` y `cancelar`.

## Medición 1: una instancia

1. Levantá WildFly como siempre (`standalone.sh -c standalone-full.xml`), con el WAR desplegado.
2. Corré dos o tres cargas crecientes, por ejemplo con 10, 20 y 50 VUs, y anotá cada una en la tabla.

## Medición 2: el componente escalado (dos instancias)

`ServicioDeTurnos` es stateful, pero el hold no vive en el bean: vive en la fila del turno (`EN_HOLD` + `inicioHold`). Por eso se puede escalar horizontalmente: varias instancias de WildFly con el mismo WAR, contra la misma base PostgreSQL.

1. **Copiá la instalación** de WildFly a otra carpeta, por ejemplo `wildfly-2`, con su `standalone/` limpio.
2. **Arrancala corrida 100 puertos,** con la primera ya levantada, para que el seed vea que la base ya tiene usuarios y no siembre de nuevo:
   ```bash
   wildfly-2/bin/standalone.sh -c standalone-full.xml -Djboss.socket.binding.port-offset=100
   ```
   Queda en el puerto `8180` (management en `10090`).
3. **Configurala** con el mismo `deploy/mediconecta-setup.cli`, conectándote a su puerto de management:
   ```bash
   wildfly-2/bin/jboss-cli.sh --connect --controller=localhost:10090 --file=deploy/mediconecta-setup.cli
   ```
   y copiá `target/mediconecta.war` a `wildfly-2/standalone/deployments/`.
4. **Corré la misma carga** contra las dos instancias. Cada VU usa siempre la misma: los VUs pares van a una y los impares a la otra. El reparto lo hace el script, sin balanceador.
   ```bash
   k6 run -e VUS=20 -e ITERACIONES=25 \
     -e BASE_URLS=http://localhost:8080/mediconecta/api,http://localhost:8180/mediconecta/api \
     --summary-export=load-tests/resultados/2-instancias-20vus.json \
     load-tests/reserva-turnos.js
   ```

Compará con la medición de una instancia **usando los mismos `VUS` e `ITERACIONES`**.

**Qué saber al escalar:**
- **Timers y barrido:** cada instancia tiene su propio `ExpiradorDeHolds`. El temporizador de un hold vive en la instancia que lo reservó, y el barrido de cada minuto corre en las dos. No hay doble liberación: `liberar` toma el lock del turno y no hace nada si ya no está `EN_HOLD`.
- **Mensajería:** Artemis está embebido en cada instancia, así que los tópicos y las colas no se comparten. No afecta esta prueba, que no confirma turnos, pero sí afectaría a una prueba de confirmación.
- **Cuello de botella esperado:** con las dos instancias contra la misma base, si el p95 no mejora, el límite probablemente esté en PostgreSQL (los locks `PESSIMISTIC_WRITE` de la reserva) y no en WildFly. Es un buen punto para el análisis.

## Qué anotar

k6 imprime un resumen al terminar. De ahí sale:

- **p95 por operación:** `http_req_duration{operacion:disponibilidad}`, `{operacion:reservar}` y `{operacion:cancelar}`, en la columna `p(95)`.
- **Throughput:** `http_reqs`, en la columna de la tasa (`/s`). Incluye las requests de `setup`; para comparar entre corridas sirve igual, porque `setup` es el mismo con los mismos `VUS` e `ITERACIONES`. La medida más limpia es `iterations` (`/s`), que equivale a reservas completas por segundo.
- **Errores:** `http_req_failed` (porcentaje) y `checks` (porcentaje de aciertos).
- **Flujo completo:** `flujo_reserva_completo`, en la columna `p(95)`.

Umbrales de referencia (están en el script, y si se rompen k6 termina con código distinto de 0): errores < 1 %, checks > 99 %, p95 de disponibilidad y cancelación < 800 ms, p95 de reserva < 1000 ms.

## Resultados

Entorno (completar): CPU / RAM de la máquina, versión de WildFly y de PostgreSQL, y si k6 corre en la misma máquina que el servidor.

| Escenario | Instancias | VUs | Iteraciones/VU | p95 disponibilidad (ms) | p95 reservar (ms) | p95 cancelar (ms) | p95 flujo (ms) | Throughput (iteraciones/s) | Errores (%) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Base | 1 | 10 |  |  |  |  |  |  |  |
| Media | 1 | 20 |  |  |  |  |  |  |  |
| Alta | 1 | 50 |  |  |  |  |  |  |  |
| Base | 2 | 10 |  |  |  |  |  |  |  |
| Media | 2 | 20 |  |  |  |  |  |  |  |
| Alta | 2 | 50 |  |  |  |  |  |  |  |

Conclusión (completar): ¿mejoró el p95 y el throughput al escalar? ¿Dónde aparece el cuello de botella?
