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
- **Franjas:** con el profesional, abre una franja por VU, cada una en un minuto distinto. Si un minuto ya tiene una franja (la regla de SCRUM-125 responde `409`, por ejemplo por una corrida anterior), prueba el siguiente.

El VU *n* reserva y cancela siempre su propia franja: al cancelar el hold, la franja vuelve a `DISPONIBLE` y queda lista para la iteración siguiente. Por eso un `409` en la reserva es un error real del sistema y no dos VUs peleando por el mismo turno. Los emails llevan el id de la corrida, así que se puede correr varias veces contra la misma base.

Una franja por VU, y no una por iteración, hace que la agenda tenga siempre `VUS` franjas. Con una por iteración, la respuesta de `disponibilidad` crecía con la duración de la corrida: una prueba de 10 VUs x 100 iteraciones abría 1000 franjas, bajaba 177 MB y medía la serialización de la agenda más que la reserva.

**Si una cancelación falla, el VU la reintenta una vez** (etiqueta `cancelar_reintento`). Sin el reintento, el hold quedaba tomado y cada reserva siguiente de ese paciente daba `409` (un hold por paciente) durante los 5 minutos del hold. La corrida medía esa cascada y no el sistema. El fallo original igual cuenta en `checks` y en `http_req_failed`.

**Los pacientes de carga no tienen obra social.** Reservan como particulares y `CoberturaEnLaReserva` no llama al legado SOAP. Se mide el componente de turnos, no la integración con la obra social.

## Requisitos

- **k6** instalado ([guía de instalación](https://grafana.com/docs/k6/latest/set-up/install-k6/)). En Windows: `winget install k6 --source winget`, o el `.zip` oficial de [las releases de GitHub](https://github.com/grafana/k6/releases), que trae solo `k6.exe` y no instala nada. En macOS: `brew install k6`.
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
| `ITERACIONES` | `20` | Reservas por paciente (cada VU reserva y cancela siempre su misma franja) |
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
- **Timers y barrido:** cada instancia tiene su propio `ExpiradorDeHolds`. El temporizador de un hold vive en la instancia que lo reservó, y el barrido de cada minuto corre en las dos. No hay doble liberación: `liberar` toma el lock del turno y no hace nada si ya no está `EN_HOLD`. En esta prueba no molesta, porque cada VU reserva y cancela en la misma instancia (ver ADR 0001 para el caso con balanceador).
- **Mensajería:** Artemis está embebido en cada instancia, así que los tópicos y las colas no se comparten. No afecta esta prueba, que no confirma turnos, pero sí afectaría a una prueba de confirmación.
- **Cuello de botella:** se esperaba en PostgreSQL (los locks `PESSIMISTIC_WRITE` de la reserva), pero la medición lo encontró en la CPU: cada request verifica la contraseña con PBKDF2. Ver [Resultados](#resultados-17112026).

## Qué anotar

k6 imprime un resumen al terminar. De ahí sale:

- **p95 por operación:** `http_req_duration{operacion:disponibilidad}`, `{operacion:reservar}` y `{operacion:cancelar}`, en la columna `p(95)`.
- **Throughput:** `http_reqs`, en la columna de la tasa (`/s`). Incluye las requests de `setup`; para comparar entre corridas sirve igual, porque `setup` es el mismo con los mismos `VUS` e `ITERACIONES`. La medida más limpia es `iterations` (`/s`), que equivale a reservas completas por segundo.
- **Errores:** `http_req_failed` (porcentaje) y `checks` (porcentaje de aciertos). Los reintentos de cancelación van con la etiqueta `operacion:cancelar_reintento` y su propio check.
- **Reparto:** `http_reqs{instancia:N}`, con cuántas requests del flujo atendió cada instancia de `BASE_URLS`.
- **Flujo completo:** `flujo_reserva_completo`, en la columna `p(95)`.

Umbrales de referencia (están en el script, y si se rompen k6 termina con código distinto de 0): errores < 1 %, checks > 99 %, p95 de disponibilidad y cancelación < 800 ms, p95 de reserva < 1000 ms.

## Resultados (17/11/2026)

Hay tres mediciones:
- **1 y 2:** comparan 1 y 2 instancias en la misma máquina, sin repartir la CPU entre ellas.
- **3:** repite la comparación con dos nodos del mismo tamaño, cada uno con sus propios núcleos (afinidad de CPU).

La [conclusión general](#conclusión-general) está al final.

### Entorno

- **Máquina:** Intel Core i7-12700K, con 12 núcleos (8 P-cores y 4 E-cores) y 20 procesadores lógicos; 64 GB de RAM; Windows 11 Pro.
- **Software:** WildFly 41.0.0.Final (`standalone-full`, heap por defecto `-Xms64M -Xmx512M`) sobre Java 21.0.8, PostgreSQL 18.6 y k6 v2.4.0 (el `.zip` oficial de GitHub).
- **Una sola máquina para todo:** las dos instancias de WildFly, PostgreSQL y k6. Las dos instancias comparten la base `mediconecta`.
- **Build:** `main` @ `fe1495e`, el mismo WAR en las dos instancias.

### Cómo se corrió

1. **Instancia 1:** la de siempre, en `:8080`.
2. **Instancia 2:** en otra carpeta se copiaron `standalone/configuration` y el WAR desplegado (`standalone/data/content`) de la instancia 1, sin logs, journal de Artemis ni timers. Así hereda el mismo `standalone-full.xml`, ya configurado por `deploy/mediconecta-setup.cli` (datasource, seguridad y mensajería), y el mismo WAR. Se arrancó desde PowerShell, con `JAVA_HOME` y las tres `MEDICONECTA_*_PASSWORD` del seed definidas:
   ```powershell
   $env:JAVA_TOOL_OPTIONS = '-Duser.timezone=America/Buenos_Aires'
   $env:NOPAUSE = 'true'
   bin\standalone.bat -c standalone-full.xml "-Djboss.server.base.dir=<copia>" "-Djboss.socket.binding.port-offset=100"
   ```
   Queda en `:8180`. Su log muestra `WFLYSRV0010: Deployed "mediconecta.war"` y `Seed inicial: ya hay usuarios cargados, no se siembra nada`.
   - La zona es `America/Buenos_Aires` porque es la que reporta la instancia 1 (`user.timezone`). Con `America/Argentina/Buenos_Aires`, el datasource no levantó: este PostgreSQL 18 la rechaza (`FATAL: invalid value for parameter "TimeZone"`).
3. **Base limpia en cada corrida:** antes de cada una, la base vuelve al estado del seed con `limpiar_base` (de `demo/demo-env.sh`). Así todas arrancan igual.
4. **Corridas alternadas:** una instancia, después dos, con la misma carga, y otra vez las dos (`r1` y `r2`). `k6.exe` se usó desde la carpeta del zip, sin instalar nada:
   ```bash
   k6 run -e VUS=10 -e ITERACIONES=200 \
     --summary-export=load-tests/resultados/1-instancia-10vus-r1.json load-tests/reserva-turnos.js
   k6 run -e VUS=10 -e ITERACIONES=200 \
     -e BASE_URLS=http://localhost:8080/mediconecta/api,http://localhost:8180/mediconecta/api \
     --summary-export=load-tests/resultados/2-instancias-10vus-r1.json load-tests/reserva-turnos.js
   ```
   - Lo mismo con `VUS=20 ITERACIONES=120` y con `VUS=50 ITERACIONES=50`.
   - Son entre 2000 y 2500 reservas por corrida, de 64 a 88 segundos cada una.
   - Antes de medir hubo una corrida de calentamiento de la instancia 2, que no se registró.
5. **CPU:** durante cada corrida se muestreó cada ~2 s con los contadores de Windows (`Win32_PerfFormattedData`), por proceso y por grupo de núcleos.

### Resultados por corrida

Latencias en ms. Los JSON de `--summary-export` están en `resultados/`, con el nombre de cada corrida.

| Instancias | VUs | Iter./VU | Corrida | disponibilidad p50 / p95 | reservar p50 / p95 | cancelar p50 / p95 | flujo p50 / p95 | Iteraciones/s | Requests/s | Errores (%) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 10 | 200 | r1 | 80 / 143 | 112 / 184 | 99 / 164 | 316 / 391 | 31.1 | 93.7 | 0.00 |
| 1 | 10 | 200 | r2 | 78 / 142 | 111 / 179 | 96 / 164 | 308 / 387 | 31.7 | 95.6 | 0.00 |
| 2 | 10 | 200 | r1 | 90 / 176 | 100 / 228 | 95 / 202 | 318 / 516 | 28.1 | 84.5 | 0.02 |
| 2 | 10 | 200 | r2 | 84 / 164 | 90 / 198 | 91 / 183 | 296 / 476 | 30.1 | 90.8 | 0.02 |
| 1 | 20 | 120 | r1 | 145 / 232 | 255 / 367 | 214 / 313 | 622 / 788 | 31.4 | 94.8 | 0.00 |
| 1 | 20 | 120 | r2 | 148 / 241 | 258 / 381 | 215 / 323 | 627 / 821 | 30.9 | 93.4 | 0.00 |
| 2 | 20 | 120 | r1 | 138 / 339 | 152 / 562 | 146 / 458 | 456 / 1251 | 29.5 | 88.9 | 0.01 |
| 2 | 20 | 120 | r2 | 138 / 335 | 151 / 545 | 143 / 460 | 450 / 1256 | 29.8 | 89.8 | 0.01 |
| 1 | 50 | 50 | r1 | 418 / 644 | 649 / 858 | 586 / 743 | 1661 / 1980 | 29.5 | 89.6 | 0.00 |
| 1 | 50 | 50 | r2 | 426 / 646 | 645 / 867 | 597 / 762 | 1663 / 1994 | 29.2 | 88.8 | 0.01 |
| 2 | 50 | 50 | r1 | 317 / 1153 | 358 / 2076 | 339 / 1683 | 1003 / 4840 | 28.5 | 86.6 | 0.07 |
| 2 | 50 | 50 | r2 | 307 / 1062 | 345 / 2070 | 335 / 1586 | 983 / 4627 | 29.4 | 89.6 | 0.05 |

Las dos corridas de 2 instancias con 50 VUs rompen los umbrales de p95, y k6 termina con código 99. Las demás pasan los umbrales.

**Comparación**, con el promedio de `r1` y `r2`:

| VUs | Iteraciones/s (1 → 2) | flujo p50 en ms (1 → 2) | flujo p95 en ms (1 → 2) | flujo promedio en ms (1 → 2) |
| --- | --- | --- | --- | --- |
| 10 | 31.4 → 29.1 (-7 %) | 312 → 307 (-2 %) | 389 → 496 (+28 %) | 313 → 313 (+0 %) |
| 20 | 31.2 → 29.6 (-5 %) | 625 → 453 (-28 %) | 804 → 1254 (+56 %) | 622 → 569 (-9 %) |
| 50 | 29.3 → 29.0 (-1 %) | 1662 → 993 (-40 %) | 1987 → 4734 (+138 %) | 1633 → 1350 (-17 %) |

**Reparto entre instancias:** k6 etiquetó cada request con su instancia (`http_reqs{instancia:N}` en los JSON). En todas las corridas de 2 instancias, cada una atendió la mitad del flujo:
- 10 VUs: 3000 y 3001 requests;
- 20 VUs: 3600 y 3601;
- 50 VUs: 3750, y 3753 o 3754.

Las pocas requests de más son reintentos de cancelación.

**CPU durante la carga.** Es el promedio de las muestras con carga. Los procesos van en % de un procesador lógico (100 % = un lógico entero).

| Configuración | CPU total de la máquina | JVMs (suma) | PostgreSQL | k6 | P-cores (lógicos 0–15) | E-cores (lógicos 16–19) |
| --- | --- | --- | --- | --- | --- | --- |
| 1 instancia | 20–24 % | 318–338 % | 8–12 % | 3–5 % | 4–8 % | 94–98 % |
| 2 instancias | 20–23 % | 325–340 % | 9–10 % | 2–5 % | 4–7 % | 96–99 % |

Las corridas `r1` de 10 VUs no tienen el dato por grupo de núcleos, porque se agregó al muestreo después.

### Experimento de control: la instancia 2 sin EcoQoS

Después de la serie, se le sacó EcoQoS (el *power throttling* de Windows) a la JVM de la instancia 2 con `SetProcessInformation(ProcessPowerThrottling)`. Después se la midió sola (`BASE_URLS=http://localhost:8180/mediconecta/api`). No es una comparación 1 contra 2: a la instancia 1, compartida, no se la tocó.

| Instancia 2 sola, sin EcoQoS | disponibilidad p50 / p95 | reservar p50 / p95 | cancelar p50 / p95 | flujo p50 / p95 | Iteraciones/s | Errores (%) | CPU total / P-cores / E-cores |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 10 VUs x 300 | 26 / 38 | 28 / 48 | 28 / 47 | 86 / 145 | 104.5 | 0.20 | 56 % / 55 % / 61 % |
| 50 VUs x 120 | 97 / 156 | 127 / 206 | 115 / 185 | 346 / 464 | 134.8 | 0.15 | 89 % / 88 % / 90 % |

La misma instancia, con EcoQoS, sola y con 10 VUs, había dado 28,0 iteraciones/s en una corrida de diagnóstico. Los JSON de control son `resultados/control-8180-sin-ecoqos-*.json`.

### Conclusión de las mediciones 1 y 2

**Escalar a dos instancias en esta máquina no mejoró el throughput.** Quedó igual o apenas peor: -7 %, -5 % y -1 % con 10, 20 y 50 VUs. Todas las corridas, con una o con dos instancias, dieron entre 28 y 32 reservas completas por segundo.

**La latencia cambió de forma, no de nivel.** Con 20 y 50 VUs, la mediana del flujo bajó un 28 % y un 40 %, pero el p95 subió un 56 % y un 138 %. Con 50 VUs llegó a 4,7 s y rompió los umbrales.

**Por qué no hubo mejora:**

1. **Cada request es trabajo de CPU, no de base de datos.**
   - Toda request autentica con Basic y verifica la contraseña con PBKDF2 de 120.000 iteraciones (`PasswordUtil`).
   - Thread dumps (`jstack`) de la instancia 2 con 10 VUs: los 10 hilos `default task` estaban `RUNNABLE` dentro de `PBKDF2KeyImpl.deriveKey` / `SHA2.implDigest`.
   - PostgreSQL usó entre el 8 % y el 12 % de un núcleo, y `pg_stat_activity` mostró sus conexiones `idle`, esperando al cliente.
   - El lock de la reserva (`SELECT ... FOR NO KEY UPDATE OF tbl`) solo toma la fila del turno, y cada VU tiene el suyo.
   - El cuello de botella que se esperaba en PostgreSQL no apareció en esta prueba.
2. **Windows corrió las dos JVMs con EcoQoS, solo en los 4 E-cores.**
   - Las dos instancias se arrancaron desde una consola en segundo plano.
   - Los E-cores (lógicos 16–19) estuvieron al 94–99 % y los P-cores al 4–8 %. La CPU total de la máquina nunca pasó del 24 %.
   - Las JVMs sumaron siempre entre 3,2 y 3,4 procesadores, con una o con dos instancias. La segunda instancia no agregó CPU: se repartió la misma.
3. **El reparto entre las dos JVMs fue desparejo.**
   - La instancia nueva (8180) recibió más CPU que la 8080, y sus VUs terminaron entre 20 y 30 s antes. Con 50 VUs, 25 VUs ya habían terminado a los 55–58 s de corridas de 85–88 s.
   - El final de cada corrida lo atendió solo la 8080.
   - Por eso baja la mediana (la mitad de los pacientes anduvo más rápido) y sube el p95 (la otra mitad esperó más).
   - El throughput se mide sobre la corrida entera, así que no mejora.

**El control confirma la causa.**
- Sin EcoQoS, la instancia 2 sola llegó a 104,5 iteraciones/s con 10 VUs, 3,7 veces lo que daba con EcoQoS (28,0).
- Con 50 VUs llegó a 134,8 iteraciones/s, con la máquina al 89 % de CPU.
- Una sola instancia sin restricciones ya casi llena la máquina: una segunda instancia en el mismo equipo no tendría CPU libre para aportar.

**Qué sí quedó demostrado.**
- El componente escala horizontalmente en lo funcional: dos instancias con el mismo WAR, contra la misma base, atendieron 13.800 reservas.
- No hubo ningún `409`, y el hold vive en la fila del turno.
- La mejora medible pide más hardware (otra máquina o contenedores con CPU asignada y un balanceador), no otra JVM en el mismo equipo. La medición 3 lo comprueba.
- También se puede bajar el costo por request: hoy se paga PBKDF2 en cada llamada con Basic, cuando alcanzaría con pagarlo en el login (con una sesión o un token).

### Medición 3: dos nodos del mismo tamaño (afinidad de CPU)

**El modelo.** Una sola máquina física hace de dos nodos iguales:
- **Nodo A:** la instancia `:8080`, fijada a los procesadores lógicos 0–7 (P-cores 0–3, con hyperthreading).
- **Nodo B:** la instancia `:8180`, fijada a los lógicos 8–15 (P-cores 4–7).
- **k6:** fijado a los 4 E-cores (lógicos 16–19), para no robarle CPU a ningún nodo.
- **EcoQoS:** apagado en las dos JVMs.
- **La base sigue siendo una sola y compartida,** como en un escalado horizontal real. PostgreSQL no se fijó: corre en cualquier núcleo libre.

**Por qué es la prueba justa.**
- En las mediciones 1 y 2, la segunda instancia no agregaba capacidad: las dos JVMs se repartían la misma CPU.
- Escalar horizontalmente es sumar otro servidor igual al primero. Acá la "1 instancia" usa solo los núcleos del nodo A. Los del nodo B quedan ociosos, como una segunda máquina que todavía no está. Con 2 nodos se suma exactamente la misma capacidad que ya tenía el primero.
- **Lo que este modelo no reproduce:** los nodos comparten la caché L3, la memoria, el disco y la máquina de PostgreSQL y de k6, y no hay red entre ellos. Una medición en máquinas separadas daría números algo distintos.

**Cómo se corrió.**
- **Topología:** se verificó con `GetLogicalProcessorInformationEx`. Hay 8 P-cores con 2 hilos cada uno (lógicos 0–15) y 4 E-cores (16–19).
- **Mismo tamaño de pools:** las dos JVMs arrancan viendo los 20 lógicos y se fijan recién después del arranque. Así dimensionan igual sus pools de hilos y de GC. La 8080 ya estaba corriendo; la 8180 se levantó igual que en la medición 2.
- **Afinidad y EcoQoS**, sobre los procesos en marcha, desde PowerShell y sin permisos de administrador:
  ```powershell
  (Get-Process -Id <pid de la 8080>).ProcessorAffinity = 0x00FF   # nodo A: lógicos 0-7
  (Get-Process -Id <pid de la 8180>).ProcessorAffinity = 0xFF00   # nodo B: lógicos 8-15
  # EcoQoS apagado en las dos: SetProcessInformation(ProcessPowerThrottling,
  #   ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED, StateMask = 0)
  ```
- **k6** se lanzó con `Start-Process` y `ProcessorAffinity = 0xF0000` (E-cores):
  ```bash
  k6 run -e VUS=20 -e ITERACIONES=250 \
    --summary-export=load-tests/resultados/afinidad-1-nodo-20vus-r1.json load-tests/reserva-turnos.js
  k6 run -e VUS=20 -e ITERACIONES=250 \
    -e BASE_URLS=http://localhost:8080/mediconecta/api,http://localhost:8180/mediconecta/api \
    --summary-export=load-tests/resultados/afinidad-2-nodos-20vus-r1.json load-tests/reserva-turnos.js
  ```
- **Cargas:** lo mismo con `VUS=50 ITERACIONES=100` y con `VUS=100 ITERACIONES=50`. Son 5000 reservas por corrida, de 45 a 90 s.
- **Orden:** alternando 1 y 2 nodos, dos veces cada uno, con la base en el estado del seed antes de cada corrida. Antes hubo una corrida de calentamiento con 2 nodos, que no se registró.

**Resultados por corrida.** Latencias en ms. Los JSON son `resultados/afinidad-*.json`.

| Nodos | VUs | Iter./VU | Corrida | disponibilidad p50 / p95 | reservar p50 / p95 | cancelar p50 / p95 | flujo p50 / p95 | Iteraciones/s | Requests/s | Errores (%) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 20 | 250 | r1 | 78 / 220 | 97 / 248 | 88 / 233 | 295 / 526 | 61.8 | 186.1 | 0.06 |
| 1 | 20 | 250 | r2 | 76 / 217 | 95 / 243 | 88 / 231 | 296 / 514 | 62.4 | 187.7 | 0.03 |
| 2 | 20 | 250 | r1 | 48 / 94 | 50 / 99 | 49 / 98 | 164 / 238 | 106.4 | 320.8 | 0.21 |
| 2 | 20 | 250 | r2 | 47 / 91 | 49 / 90 | 48 / 89 | 161 / 220 | 112.1 | 337.5 | 0.11 |
| 1 | 50 | 100 | r1 | 224 / 367 | 325 / 467 | 281 / 422 | 829 / 1073 | 58.5 | 176.6 | 0.02 |
| 1 | 50 | 100 | r2 | 224 / 373 | 320 / 467 | 284 / 433 | 830 / 1075 | 58.3 | 176.1 | 0.03 |
| 2 | 50 | 100 | r1 | 92 / 281 | 128 / 329 | 110 / 314 | 382 / 689 | 112.4 | 339.8 | 0.05 |
| 2 | 50 | 100 | r2 | 91 / 283 | 122 / 337 | 110 / 311 | 380 / 692 | 112.8 | 340.6 | 0.03 |
| 1 | 100 | 50 | r1 | 489 / 774 | 525 / 869 | 515 / 849 | 1708 / 2013 | 56.3 | 171.1 | 0.00 |
| 1 | 100 | 50 | r2 | 489 / 753 | 531 / 863 | 520 / 845 | 1713 / 1957 | 56.3 | 171.2 | 0.01 |
| 2 | 100 | 50 | r1 | 248 / 378 | 279 / 429 | 268 / 407 | 818 / 1018 | 105.3 | 320.3 | 0.08 |
| 2 | 100 | 50 | r2 | 249 / 378 | 278 / 418 | 267 / 399 | 812 / 1008 | 106.2 | 323.0 | 0.11 |

Solo las dos corridas de 1 nodo con 100 VUs rompen un umbral: el p95 de cancelar (849 y 845 ms, contra 800). Con 2 nodos, todas pasan.

**Comparación**, con el promedio de `r1` y `r2`:

| VUs | Iteraciones/s (1 → 2 nodos) | flujo p50 en ms (1 → 2) | flujo p95 en ms (1 → 2) |
| --- | --- | --- | --- |
| 20 | 62.1 → 109.3 (+76 %) | 296 → 162 (-45 %) | 520 → 229 (-56 %) |
| 50 | 58.4 → 112.6 (+93 %) | 830 → 381 (-54 %) | 1074 → 690 (-36 %) |
| 100 | 56.3 → 105.7 (+88 %) | 1710 → 815 (-52 %) | 1985 → 1013 (-49 %) |

**Reparto entre nodos:** cada nodo atendió 7500 requests del flujo por corrida, más sus reintentos de cancelación (de 7501 a 7527).

**CPU durante la carga.** Los procesos van en % de un procesador lógico. Los grupos de núcleos, en % promedio del grupo.

| Configuración | CPU total | JVM 8080 | JVM 8180 | PostgreSQL | k6 | Nodo A (0–7) | Nodo B (8–15) | E-cores (16–19) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 nodo | 33–40 % | 583–705 % | 0 % | 8–13 % | 5–8 % | 75–89 % | 8–10 % | 8–9 % |
| 2 nodos | 72–77 % | 630–699 % | 695–734 % | 28–40 % | 12–18 % | 80–87 % | 89–91 % | 20–33 % |

**Lectura.**
- **Con dos nodos del mismo tamaño, el throughput casi se duplica:** +76 %, +93 % y +88 % con 20, 50 y 100 VUs.
- **La latencia baja a la mitad:** el p50 del flujo baja entre 45 y 54 %, y el p95 entre 36 y 56 %.
- **Un nodo solo se satura** en unas 56–62 reservas/s. Con más VUs no rinde más: solo crece la espera.
- **Por qué no llega a 2×:**
  - PostgreSQL pasa de 0,1 a 0,3–0,4 núcleos y comparte los núcleos libres con k6.
  - Los dos nodos comparten la caché y la memoria.
  - Con 20 VUs, 10 por nodo no alcanzan a saturar cada uno.
  - Aun así, la base no se volvió el cuello de botella: sigue siendo la CPU de cada nodo (PBKDF2 en cada request).

### Conclusión general

1. **Otra JVM en la misma CPU no escala.**
   - Mediciones 1 y 2: con la máquina sin repartir, la segunda instancia no agregó capacidad. Las dos JVMs se repartieron la misma CPU, que además Windows limitó a los 4 E-cores.
   - El throughput no cambió (de 28 a 32 reservas/s) y el p95 empeoró hasta un 138 %.
2. **Un nodo más del mismo tamaño casi duplica la capacidad.**
   - Medición 3: con dos nodos iguales (4 P-cores cada uno) y la base compartida, el throughput subió entre 76 y 93 % (de unas 56–62 a 105–113 reservas/s).
   - La latencia del flujo bajó a la mitad (p50 de -45 a -54 %, p95 de -36 a -56 %).
3. **El componente escala horizontalmente** porque el hold vive en la fila del turno y no en la JVM. Las dos instancias atendieron, con la misma base, 13.800 reservas en la medición 2 y 30.000 en la medición 3, sin ningún `409`.
4. **El límite es la CPU de cada nodo, no PostgreSQL.** Cada request verifica la contraseña con PBKDF2. La base pasó de 0,1 a 0,4 núcleos con dos nodos.
5. **Para una prueba de producción faltan** máquinas separadas con un balanceador, la base en su propio servidor y el arreglo de la carrera de `ExpiradorDeHolds` (ver abajo).

### Errores y hallazgos

**Los errores (0 a 0,07 % en la serie) son todos `500` de una carrera en `ExpiradorDeHolds`.**
- `cancelar(turnoId)` recorre `timerService.getTimers()` y le pide `getInfo()` a cada timer.
- Si otro pedido canceló ese timer en el medio, WildFly lanza `NoSuchObjectLocalException` (`WFLYEJB0331: Timer ... was canceled`) y revierte la transacción entera.
- Pasa al cancelar y también al reservar, porque `programar` llama primero a `cancelar`.
- En la serie fallaron 12 cancelaciones y 1 reserva con 2 instancias, y 1 cancelación con 1 instancia, sobre 13.800 iteraciones de cada configuración.
- Con más concurrencia es más frecuente:
  - en el control sin EcoQoS fallaron 40 cancelaciones y 5 reservas en 9000 iteraciones;
  - en la medición 3 fallaron 104 cancelaciones y 5 reservas en 60.000 iteraciones (0 a 0,21 % de las requests).
- Cada fallo de k6 coincide con una excepción en el log del servidor, y todos los reintentos de cancelación respondieron 200.
- Arreglo sugerido, fuera del alcance de esta prueba: ignorar `NoSuchObjectLocalException` dentro del bucle de `cancelar`, porque un timer ya cancelado es justo lo que se buscaba.

**`409`: ninguno.**
- Todas las reservas fallidas fueron el `500` de arriba.
- La regla de un hold por paciente (#72) no saltó, porque cada VU cancela antes de volver a reservar, y reintenta la cancelación si falla.

**Limitador de login (#71): sin efecto.** El script solo manda contraseñas correctas: la del profesional del seed y la de los pacientes que registra. No hubo `401` ni `429`.

**Timers por instancia (ADR 0001): no afectó la prueba.**
- Cada VU va siempre a la misma instancia, así que reserva y cancela en la misma, y la cancelación encuentra el timer local.
- En ninguna de las dos instancias venció un hold por temporizador ni por barrido, ni en la medición 2 ni en la 3: no hay ninguna línea `Hold vencido` en los logs.
- Con un balanceador round-robin, la cancelación podría caer en la otra instancia. El timer viejo seguiría vivo en la primera y dispararía a los 5 minutos, que es el riesgo que documenta el ADR 0001.
