# Pruebas (Postman)

## Qué verifica cada colección

| Colección | Qué cubre |
| --- | --- |
| `iw3-f2-recepcion.postman_collection.json` | Recepción de orden: alta válida, duplicado de `orderNumber`, entrada inválida, sin token |
| `iw3-f3-tara-activacion.postman_collection.json` | Tara y activación: camino feliz, clave incorrecta/fuera de rango, estados incompatibles, idempotencia de la activación |
| `iw3-f3-telemetria.postman_collection.json` | Lecturas: aceptación/descarte (caudal/masa/decreciente) y snapshot, verificados por HTTP. No comprueba directamente las filas del histórico (`DetalleCarga`) |
| `iw3-f4-cierre.postman_collection.json` | Cierre 2→3: precondiciones (sin activar, sin lecturas), cierre repetido, lectura después del cierre |
| `iw3-f4-pesaje-conciliacion.postman_collection.json` | Pesaje final y conciliación, consulta posterior |
| `iw3-f4-saltos-retrocesos.postman_collection.json` | Combinaciones de estado inicial/operación no cubiertas por las anteriores (todas deben rechazar con `409`) |
| `iw3-f5-runner-circuito.postman_collection.json` | Circuito completo encadenado: login → recepción → tara/activación → lecturas → cierre → pesaje final con conciliación |

Cada request valida código HTTP y, donde aplica, el cuerpo de la
respuesta — no solo el status code. Ninguna colección verifica cambios
directos en la base de datos: solo lo que la respuesta HTTP expone.
Ninguna ejercita condiciones de carrera (dos requests verdaderamente
simultáneas); eso no es expresable en el runner secuencial de Postman.

## Cómo ejecutar (Postman Desktop)

1. Con el backend corriendo localmente (ver `docs/ejecucion-local.md`),
   importar las colecciones de `postman/` y
   `postman/iw3-local.postman_environment.example.json`.
2. Duplicar ese entorno importado (guardarlo como copia local) y
   completar ahí `testUsername`/`testPassword` con el usuario de prueba
   creado según `docs/ejecucion-local.md`. No versionar esa copia con
   credenciales reales. `baseUrl` ya viene en `http://localhost:8080`.
3. Seleccionar el entorno duplicado en el selector de entornos.
4. Abrir **Runner**, elegir la colección y ejecutar. El runner del
   circuito completo (F5) admite 2+ iteraciones; cada iteración usa una
   orden distinta.

## Cómo ejecutar (Postman CLI)

```bash
postman collection run postman/iw3-f5-runner-circuito.postman_collection.json \
  --iteration-count 2 \
  --env-var testUsername=<usuario_de_prueba> \
  --env-var testPassword=<password_de_prueba>
```

Sin `postman login` ni `--output` (ninguno es necesario para correr una
colección local).

## Evidencia previa del equipo — histórica

Los resultados de esta sección corresponden a la ejecución histórica
sobre el commit `45710e3` (tag `parcial-2-local-v1`). No representan
una reejecución sobre la versión actual. Las validaciones posteriores
se detallan por separado, en "Limpieza de ejemplos docentes —
verificación" más abajo.

| Colección | Resultado registrado |
| --- | --- |
| F2 — recepción | 6 requests, 12 aserciones, 0 fallos |
| F3 — tara/activación | 17 requests, 20 aserciones, 0 fallos |
| F3 — telemetría (2 iteraciones) | 34 requests, 38 aserciones, 0 fallos |
| F4 — cierre | 14 requests, 16 aserciones, 0 fallos |
| F4 — pesaje/conciliación | 19 requests, 21 aserciones, 0 fallos |
| F4 — saltos y retrocesos | 25 requests, 26 aserciones, 0 fallos |
| F5 — runner completo (2 iteraciones) | 18 requests, 68 aserciones, 0 fallos |

Las condiciones de carrera (dos requests concurrentes sobre la misma
orden) se probaron por fuera de Postman, con `curl` en paralelo; ese
detalle y la evidencia de consultas directas a la base quedan en el
repositorio de desarrollo y el respaldo, no en esta copia.

## Evidencia de despliegue en la VM (28/10/2026)

El runner del circuito completo (F5) también se ejecutó contra la VM
desplegada, por HTTPS, desde Postman Desktop: 2 iteraciones, 18
peticiones, 68 comprobaciones aprobadas, 0 fallidas, 0 omitidas, 13,419 s.
Detalle de arquitectura, evidencia y límites de esa verificación en
[docs/despliegue-vm.md](despliegue-vm.md); esa corrida no reemplaza la
evidencia local de la tabla anterior, que sigue siendo la referencia
completa por colección.

## Validación de la guía de arranque local

Se probó la guía de arranque (README) sobre una copia temporal de este
contenido, contra un proyecto Docker Compose y un volumen de MySQL
propios y aislados (`iw3-arranque-validacion`, no `iw3-local`), en esta
misma máquina — no equivale a una instalación limpia de Ubuntu.

Verificado directamente (JDBC con el driver del propio proyecto y
conteos de filas, sin pasar por `docker exec`):
- MySQL aislado acepta conexión y autenticación real (`SELECT 1`).
- El backend arranca contra esa base (log `Started Iw3Application`,
  puerto 8080 abierto).
- `tools/crear-usuario-local.sh` crea el usuario una vez y, al
  ejecutarlo de nuevo con el mismo usuario, lo detecta sin duplicar
  nada: una fila en `users`, una en `roles` y una en `userroles` antes
  y después de la segunda corrida.

Confirmado por un integrante del equipo, no observado directamente en
esta validación (sin cantidades de requests/aserciones registradas para
esta corrida en particular — ver la tabla de evidencia previa arriba
para los números de ejecuciones anteriores sobre otro entorno):
- Login y `GET /api/v1/products` con el usuario creado — sobre la
  versión del código previa a la limpieza de ejemplos docentes
  (`docs/decisiones.md`); ese endpoint ya no existe en esta entrega (ver
  la verificación de login/protección más abajo, que lo reemplaza).
- Swagger UI accesible.
- Runner de Postman (F5) ejecutado contra este entorno aislado.

El entorno aislado (contenedor, volumen y red del proyecto
`iw3-arranque-validacion`) se retira por separado; no afecta a
`iw3-local` ni a ninguna base real.

## Limpieza de ejemplos docentes — verificación (esta entrega)

Se retiraron del código el catálogo de ejemplo (productos/categorías),
las integraciones `cli1`/`cli2` (y sus perfiles), el WebSocket de
chat/gráfico con su UI estática, el job de productos vencidos con
`EmailBusiness`, `AuthtestRestController` y el endpoint
`/demo/encodepass` — ninguno pertenece al circuito de órdenes (detalle
en `docs/decisiones.md`). Verificado en la rama
`chore/retirar-ejemplos-docentes`, sobre una base MySQL propia y
aislada (proyecto Compose `iw3-limpieza-validacion`, puerto `3310` solo
en `127.0.0.1`, volumen propio) — no `iw3-local`, no la de la VM:

- `./mvnw -Dbuild=war -Pmysqldev clean verify` (compilación, los 7 tests
  del proyecto y empaquetado del WAR, sin `-DskipTests`): `BUILD
  SUCCESS`, `Tests run: 7, Failures: 0, Errors: 0`. Hibernate creó
  únicamente `camiones`, `choferes`, `clientes`, `detalles_carga`,
  `historial_estados`, `ordenes`, `productos_orden`, `roles`,
  `userroles`, `users` — sin tablas de productos/categorías de ejemplo
  ni de las integraciones retiradas.
- Carga del contexto completo (`Iw3ApplicationTests`) también bajo el
  perfil `mysqlprod` contra esa misma base aislada (override puntual de
  `spring.datasource.url`, nunca la base de producción): arranca sin
  errores, confirmando que `MysqldevScanConfig`/`MysqlprodScanConfig`
  simplificados (sin exclusiones de `cli1`/`cli2`, que ya no existen)
  siguen descubriendo las entidades y repositorios de `auth` y
  `ordenes.model` en ambos perfiles.
- Arranque real (`java -jar target/ROOT.war`, perfil `mysqldev`) contra
  esa base aislada, con un usuario de prueba dado de alta directamente
  (mismo mecanismo que `tools/crear-usuario-local.sh`, sin pasar por
  `docker exec` por no tener permisos de `sudo` en el entorno de
  validación):
  - Sin token, sobre una ruta protegida real: `401` (cuerpo vacío).
  - Con token, `GET /api/v1/orders/999999/conciliacion` (orden
    inexistente): `404` con cuerpo `StandartResponse` — confirma que el
    token fue aceptado y que la petición llegó al controller de
    órdenes.
  - Con el mismo token, una ruta que no existe en absoluto (p. ej.
    `GET /zzz-no-existe`): `401`, no `404` — por el reenvío interno a
    `/error` que pierde la autenticación *stateless* (ver
    `docs/decisiones.md`, "Manejo de errores sin advice global"). Esto
    confirma que, en esta app, un `401` aislado no demuestra nada por sí
    solo: lo que demuestra autenticación + ruta válida es
    específicamente el `404` con cuerpo `StandartResponse` de arriba.
  - `GET /ui/index.html`, `GET /demo/encodepass` y `GET
    /api/v1/products`, sin token, responden `401`. Ese `401` por sí
    solo **no** prueba que el endpoint se haya retirado — en esta app
    también lo devuelve una ruta protegida que sí existe (ver el punto
    anterior) o una ruta que no existe en absoluto (reenvío interno a
    `/error`, ver más arriba). La prueba real de que estos tres se
    retiraron es de código y de OpenAPI, no de este código HTTP: no
    existe ninguna clase `ProductRestController` en el árbol fuente, el
    método `encodepass` ya no está en `AuthRestController`, y el archivo
    estático `static/ui/index.html` fue borrado (`git status`) junto
    con la regla `permitAll` de `/ui/**` en `SecurityConfiguration`.
    `GET /favicon.ico` sigue público (`200`), sin cambios.
- Runner de Postman F5 (`iw3-f5-runner-circuito`, 2 iteraciones) contra
  ese mismo backend local: **18 requests, 68 assertions, 0 failed** —
  mismos números que la evidencia histórica de la tabla de arriba,
  reproducidos sobre el código ya limpio.
- `GET /v3/api-docs`, contrastado explícitamente contra las rutas que
  existían antes de esta limpieza: únicamente quedan 8 paths —
  `/api/v1/login` y los 7 de `/api/v1/orders/**` documentados en
  `docs/api.md`. Confirmada la ausencia puntual de
  `/api/v1/products`, `/api/v1/products/categories`,
  `/api/v1/integration/cli1/products`,
  `/api/v1/integration/cli2/products/list-expired`,
  `/api/v1/authtest/admin`, `/api/v1/authtest/user`,
  `/api/v1/mail/test` y `/demo/encodepass` en el documento generado
  (no solo su ausencia del listado general). Los recursos estáticos
  retirados (`static/ui/**`) no se documentan en OpenAPI en ningún caso
  (ni antes ni después): su baja se confirma por la eliminación del
  archivo, no por este documento. `swagger-ui.html` y `/v3/api-docs`
  siguen públicos.

No ejecutado en esta validación: un arranque HTTP completo (no solo
carga de contexto) bajo el perfil `mysqlprod` — se consideró suficiente
la carga de contexto de ese perfil combinada con el arranque HTTP
completo bajo `mysqldev`, para no repetir la batería completa dos
veces. Tampoco se probó el envío real de correo (la funcionalidad se
retiró por completo) ni se desplegó nada en la VM — ver
`docs/despliegue-vm.md` y `docs/plan-actualizacion-vm.md`.
