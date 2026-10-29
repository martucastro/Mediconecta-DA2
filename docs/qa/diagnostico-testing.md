# Diagnóstico de testing (punto de partida)

Relevamiento del estado del proyecto frente a los criterios de testing de la
clase 15 (entrega del 10/11/2026). Se tomó sobre `main` en `1b32af7` (merge del
PR #30) el 29/10/2026. **No modifica código ni configuración**: todas las
mediciones se corrieron sin cambiar el `pom.xml` del repo (ver "Cómo
reproducirlo").

## Resumen

| Tema | Estado | Dato |
|---|---|---|
| Build de un comando desde un clon limpio | Parcial | `mvn package` genera el WAR (1 min 47 s, incluye frontend). Levantar la aplicación todavía requiere WildFly y PostgreSQL instalados a mano: no hay `docker-compose.yml` ni `Dockerfile`. |
| CI | No existe | No hay `.github/workflows`. Nada corre los tests en cada PR. |
| Tests de unidad | Verdes | 185 tests en 23 clases, 0 fallas. |
| Cobertura de líneas (JaCoCo) | 35,8 % | 754 de 2105 líneas; ramas 43,1 %. |
| Mutación (PIT) | 40,0 % | 307 de 767 mutantes muertos. Sobre el código que sí tiene tests, 93,3 %. |
| Tests de integración | No hay | Ni Arquillian ni Testcontainers. Los DAO y los Resources no se prueban contra una base o un contenedor reales. |
| Pruebas de API | Manuales | Colección de Postman (`postman/`, 66 elementos) y `deploy/smoke-test.sh`. No corren en ningún pipeline (Newman). |
| Frontend | Sin tests | 0 archivos de test. `npm run lint` pasa. |
| Secretos en el historial | Riesgo bajo | gitleaks: 4 hallazgos, todos credenciales de demo en ejemplos de `curl`. Ver detalle. |
| Dependencias vulnerables | 3 altas (frontend) | `npm audit`: `vite` 8.3.0–8.3.2 y `source-map-js`, ambas con arreglo disponible. Backend sin medir. |
| Formato de errores | Propio | `{"error": ...}`, armado en 8 lugares del código; no se usa Problem Details (RFC 9457). |
| Headers de seguridad | No hay | Ni `X-Content-Type-Options`, ni `Content-Security-Policy`, ni HSTS. |
| Límite de intentos de login | No hay | Ningún `429`. |

## Cobertura y mutación por componente

Líneas cubiertas (JaCoCo) y mutantes muertos (PIT), por paquete. "Vivos" son
mutantes que los tests ejecutan pero no detectan: indican asserts faltantes.

| Paquete | Líneas | Mutación | Vivos |
|---|---|---|---|
| `turnos.negocio` | 62,2 % | 62,7 % | 10 |
| `turnos.datos` | 60,4 % | 52,2 % | 1 |
| `turnos.presentacion` | 20,8 % | 28,6 % | 1 |
| `telemedicina.negocio` | 67,2 % | 88,9 % | 0 |
| `telemedicina.datos` | 41,2 % | 57,1 % | 0 |
| `telemedicina.presentacion` | 0 % | 0 % | 0 |
| `obrassociales.negocio` | 90,0 % | 93,0 % | 0 |
| `obrassociales.negocio.soap` | 62,0 % | 64,3 % | 0 |
| `obrassociales.datos` | 57,8 % | 56,2 % | 0 |
| `facturacion.negocio` | 89,1 % | 85,7 % | 2 |
| `facturacion.datos` | 46,5 % | 31,8 % | 1 |
| `facturacion.presentacion` | 26,6 % | 13,3 % | 0 |
| `notificaciones.negocio` | 100 % | 87,5 % | 1 |
| `notificaciones.datos` | 44,0 % | 36,4 % | 0 |
| `notificaciones.presentacion` | 45,2 % | 38,5 % | 1 |
| `usuarios.negocio` | 2,1 % | 0 % | 0 |
| `usuarios.datos` | 33,3 % | 27,3 % | 1 |
| `usuarios.presentacion` | 30,5 % | 40,5 % | 1 |
| `historiaclinica.*` | 0 % | 0 % | 0 |
| `pagos.*` | 0 % | 0 % | 0 |
| `externos.obrasocial` | 85,5 % | 84,0 % | 3 |
| `externos.video` | 65,1 % | 93,8 % | 0 |
| `externos.pasarela` | 0 % | 0 % | 0 |

Lectura:

- **Lo que tiene tests, los tiene buenos** (93 % de mutación sobre lo cubierto).
  El número bajo viene de componentes enteros sin ningún test:
  `historiaclinica`, `pagos`, `usuarios.negocio` y `externos.pasarela`
  suman unas 600 líneas en 0 %.
- **Los `Resource` y los `DAO` casi no se prueban.** Es esperable con tests de
  unidad: necesitan un contenedor o una base. Es el hueco que cubren los tests
  de integración.
- **Los 10 mutantes vivos de `ServicioDeTurnos`** son llamadas que ningún test
  verifica: programar y cancelar el temporizador del hold
  (`expirador.programar` y `expirador.cancelar`), `setInicioHold`, los cuatro
  campos del mensaje JMS `TurnoConfirmado` y el límite de 60 caracteres del
  consultorio (`> 60` contra `>= 60`). Se arreglan con asserts en tests que
  ya existen, sin tocar código de producción.

## Hallazgos puntuales

1. **`ProfesionalDTOTest` es frágil.** Recorre `getDeclaredFields()` y falla
   cuando una herramienta de cobertura instrumenta la clase, porque aparece un
   campo sintético. Con eso, PIT no arranca. Se arregla filtrando
   `Field::isSynthetic` en el test. Para esta medición se excluyó ese test.
2. **Secretos.** El código no tiene contraseñas: los usuarios semilla las
   toman de `MEDICONECTA_*_PASSWORD` o las generan al azar. Lo que queda en el
   repo son valores de demo en la documentación y los scripts (`cambiar123`
   en el README, el smoke test y Postman; `Admin123!` para la consola de
   WildFly; la contraseña del datasource en `deploy/mediconecta-setup.cli`).
   gitleaks, con sus reglas por defecto, solo marca 4 casos de `curl -u` en
   `README.md` y `deploy/smoke-test.sh` (commit `dff2e63`). Conviene pasarlos
   a variables con un `.env.example` y dejar documentado que son de demo,
   antes de que el CI corra gitleaks y falle.
3. **Dos colecciones de Postman.** `deploy/MediConecta.postman_collection.json`
   (21 elementos, del 28/09) parece la versión vieja de
   `postman/MediConecta-Demo.postman_collection.json` (66). Hay que decidir
   cuál vale y borrar la otra.
4. **Solo 2 clases usan `@ParameterizedTest`.** La máquina de estados del
   turno (cada transición inválida da `409`) es el caso más natural para
   parametrizar.
5. **Versión de Java.** El código compila con `release 17` y el README pide
   JDK 21, porque Mockito 5.7 no funciona con JDK 25. El CI y el contenedor
   tienen que fijar JDK 21; si no, los tests fallan aunque el código esté bien.

## Próximos pasos propuestos

En orden, sin depender de las cards del próximo sprint:

1. **Arreglos de tests**, sin código de producción: `ProfesionalDTOTest` y
   asserts para los mutantes vivos de turnos.
2. **CI** (GitHub Actions): `mvn verify` con JaCoCo, PIT, el build del
   frontend y gitleaks en cada PR.
3. **`docker-compose.yml`**: WildFly, PostgreSQL y la configuración del
   datasource, para levantar todo con un solo comando. Con eso, Newman puede
   correr la colección de Postman en el CI.
4. **Tests de integración** con Testcontainers para los DAO.
5. **Convenciones transversales** (Problem Details, headers de seguridad,
   límite de intentos del login): tocan código de todos los componentes, así
   que primero hay que acordarlas con el equipo.

Los componentes en 0 % (`historiaclinica`, `pagos`, `usuarios.negocio`,
`externos.pasarela`) los tiene que encarar cada responsable.

## Cómo reproducirlo

Desde la raíz del repo, con Docker andando:

```bash
# Cobertura (no hace falta tocar el pom)
mvn -Dskip.frontend=true org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test \
    org.jacoco:jacoco-maven-plugin:0.8.12:report
# Reporte: target/site/jacoco/index.html

# Secretos en todo el historial
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.21.2 git /repo --redact

# Dependencias del frontend
cd frontend && npm audit
```

PIT necesita el plugin `pitest-junit5-plugin` como dependencia de
`pitest-maven` 1.17.4, así que hay que agregarlo al `pom.xml`. Para esta
medición se agregó en forma temporal y después se descartó. Queda fijo cuando
se arme el CI.
