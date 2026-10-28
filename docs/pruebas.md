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

## Evidencia previa del equipo — no re-ejecutada sobre esta copia

El código de esta copia es idéntico, archivo por archivo, al commit que
el equipo verificó en su momento (`45710e3`, etiqueta
`parcial-2-local-v1`). Los resultados siguientes fueron registrados por
el equipo sobre ese mismo código, en su entorno local, **antes** de
armar esta copia de entrega; no se volvieron a correr acá ni se repitió
ninguna prueba para esta revisión.

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
- Login y `GET /api/v1/products` con el usuario creado.
- Swagger UI accesible.
- Runner de Postman (F5) ejecutado contra este entorno aislado.

El entorno aislado (contenedor, volumen y red del proyecto
`iw3-arranque-validacion`) se retira por separado; no afecta a
`iw3-local` ni a ninguna base real.
