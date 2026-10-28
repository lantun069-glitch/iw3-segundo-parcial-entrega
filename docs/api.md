# API — circuito de una orden

Todos los endpoints requieren JWT (`Authorization: Bearer <token>`), salvo
el login. `orderNumber` identifica la orden: va en el **body** al crearla
(`POST /api/v1/orders`) y en el **path** en todas las operaciones
posteriores sobre una orden ya existente. Errores de negocio/formato usan
`StandartResponse` (`message`, `code`, `devInfo`); `devInfo` nunca expone
detalle interno (stacktrace ni mensaje SQL), siempre `"No stack trace"`.
`401` lo responde Spring Security antes de llegar al controller (cuerpo
vacío).

`presetKg`, `capacidadKg`, `taraKg` y `pesoBrutoKg` son `BigDecimal`
`DECIMAL(15,3)`, estrictamente positivos: un valor no positivo, o con
más decimales/enteros de lo admitido, se rechaza con `400` (no se
redondea en silencio).

`masaAcumuladaKg` y `caudalKgH` (telemetría) también son
`DECIMAL(15,3)`, pero **sin** esa validación de positividad: un valor
que exceda la precisión admitida se rechaza con `400` igual que los
anteriores, pero un valor `≤ 0` no es un error de formato — se procesa
como lectura descartada por negocio (ver esa sección más abajo).

## Estados de una orden

`1` recibida → `2` con tara (y clave de activación) → `2` activada y
cargando (mismo estado, sin tara nueva) → `3` cerrada → `4` con pesaje
final y conciliación.

| Transición | Endpoint | Precondición |
| --- | --- | --- |
| → 1 | `POST /api/v1/orders` | — |
| 1 → 2 | `POST /api/v1/orders/{orderNumber}/tara` | estado 1 |
| (en 2) | `POST /api/v1/orders/{orderNumber}/activacion` | estado 2, clave correcta |
| (en 2) | `POST /api/v1/orders/{orderNumber}/lecturas` | estado 2 y activada |
| 2 → 3 | `POST /api/v1/orders/{orderNumber}/cierre` | estado 2, activada, ≥1 lectura aceptada |
| 3 → 4 | `POST /api/v1/orders/{orderNumber}/pesaje-final` | estado 3 |
| (consulta, sin cambiar estado) | `GET /api/v1/orders/{orderNumber}/conciliacion` | estado 4 |

Cualquier operación fuera de la precondición de estado responde `409`
(orden inexistente → `404`). Las precondiciones de activación y lectura
previa (para cierre y lectura) no son texto literal de la consigna: son
una decisión del equipo para que el circuito no avance sin los pasos
anteriores.

## `POST /api/v1/orders` — recepción (crea estado 1)

Request (campos obligatorios: `orderNumber`, `fechaCargaPrevista`,
`presetKg`, y para camión/chofer/cliente/producto los campos marcados
abajo):

```json
{
  "orderNumber": 900001,
  "fechaCargaPrevista": "2026-11-03T08:30:00",
  "presetKg": 10000.500,
  "camion": {"externalCode": "CAM-00891", "patente": "AB123CD", "descripcion": "Camión cisterna GLP", "capacidadKg": 12000.000},
  "chofer": {"externalCode": "CHO-00457", "nombre": "Marcos", "apellido": "Toledo", "documento": "30111222"},
  "cliente": {"externalCode": "CLI-00019", "razonSocial": "Distribuidora del Sur S.A.", "contacto": "compras@distribuidorasur.com.ar"},
  "producto": {"externalCode": "PRD-GLP01", "nombre": "GLP", "descripcion": "Gas licuado de petróleo"}
}
```

Obligatorios: `camion.externalCode`, `camion.patente`, `camion.capacidadKg`,
`chofer.externalCode`, `chofer.nombre`, `chofer.apellido`,
`cliente.externalCode`, `cliente.razonSocial`, `producto.externalCode`,
`producto.nombre`. Opcionales: `camion.descripcion`, `chofer.documento`,
`cliente.contacto`, `producto.descripcion`. `estado`, el id interno y el
actor del historial no se aceptan desde el cliente: los genera el
servidor.

Response `201`:

```json
{"id": 1, "orderNumber": 900001, "estado": 1, "fechaRecepcion": "2026-10-20T20:06:30.119016"}
```

Errores: `400` (campo obligatorio ausente, texto que excede el largo de
su columna, JSON malformado), `401`, `409` (`orderNumber` ya existe),
`500`.

**Camión, chofer, cliente, producto** se sincronizan por `externalCode`:
si no existe se crea, si existe se actualiza. Los campos obligatorios se
sobreescriben siempre; un campo opcional omitido u `null` conserva el
valor ya guardado (no lo borra). "Cisterna" = el camión completo, una
única unidad de carga: no hay entidad `Cisterna` ni compartimentos;
`capacidadKg` es un campo directo de `Camion`.

**Fechas:** `fechaCargaPrevista` se recibe sin offset y se asume en hora
`America/Argentina/Cordoba`; `fechaRecepcion` se genera en esa misma
zona.

**Auditoría:** cada creación agrega una fila en el historial de estados
(fecha/hora, actor tomado del JWT, nunca del body). El actor se toma
siempre del contexto de seguridad autenticado, nunca de un campo del JSON.

## `POST /api/v1/orders/{orderNumber}/tara` — transición 1→2

Request: `{"taraKg": 12000.000}`.

Response `200`:

```json
{"orderNumber": 910001, "estado": 2, "taraKg": 12000.000, "fechaTara": "2026-10-21T09:07:00.711802", "claveActivacion": 54231}
```

`claveActivacion`: entero `[10000, 99999]` generado con `SecureRandom`.
Se devuelve **únicamente** en esta respuesta (nunca en la de activación,
nunca en logs ni en el historial). Errores: `400` (tara ausente/no
positiva/fuera de precisión), `401`, `404`, `409` (orden no está en
estado 1, incluye tara repetida), `500`.

## `POST /api/v1/orders/{orderNumber}/activacion`

Request: `{"clave": 54231}` (entero `[10000, 99999]`).

Response `200`:

```json
{"orderNumber": 910001, "presetKg": 10000.500, "fechaActivacion": "2026-10-21T09:08:39.694564"}
```

No cambia `estado` (la activación ocurre dentro del mismo estado 2 que
generó la tara). Es **idempotente**: repetir con la clave correcta
devuelve el mismo `presetKg` y la misma `fechaActivacion` ya fijada, sin
regenerarla. Una clave incorrecta sigue devolviendo `400` aunque la orden
ya esté activada; el mensaje nunca incluye el valor de ninguna clave.
Errores: `400` (clave ausente, fuera de rango, o incorrecta), `401`,
`404`, `409` (orden no está en estado 2).

## `POST /api/v1/orders/{orderNumber}/lecturas` — telemetría

Request (los cuatro campos obligatorios, sin mínimo: caudal/masa ≤ 0 son
un caso de negocio esperado, no un error de formato):

```json
{"masaAcumuladaKg": 4000.000, "densidad": 0.54, "temperaturaC": 18.5, "caudalKgH": 3000.000}
```

Las lecturas aceptadas o descartadas por las reglas de caudal/masa
responden `200`; los errores de formato, autenticación, existencia o
estado usan los códigos indicados más abajo (una lectura descartada por
negocio **no** es un error HTTP):

```json
{"orderNumber": 920001, "aceptada": true, "motivoDescarte": null, "masaAcumuladaKg": 4000.000, "densidad": 0.5400, "temperaturaC": 18.50, "caudalKgH": 3000.000, "fechaUltimaLectura": "2026-10-21T10:07:00.845232", "fechaInicioCarga": "2026-10-21T10:07:00.845232"}
```

Precondición: estado 2 **y** activada (`fechaActivacion != null`); si
falta la activación, `409` con mensaje distinto al de estado incompatible.

**Reglas de descarte** (evaluadas en este orden cuando varias aplican a
la vez): `caudalKgH <= 0` → `masaAcumuladaKg <= 0` → `masaAcumuladaKg`
estrictamente menor que la última masa aceptada. La igualdad
(`masaAcumuladaKg == últimaMasaAceptada`) **no se descarta**. En la
primera lectura de una orden (sin antecedente) la regla de masa
decreciente no se evalúa. Densidad y temperatura nunca participan del
descarte. Una lectura descartada devuelve el snapshot **previo** sin
cambios; si todavía no hubo ninguna lectura aceptada, el snapshot es
`null` (no cero). No hay deduplicación: una lectura idéntica enviada dos
veces se procesa las dos veces.

**`densidad`:** la fuente da un rango (0–1) y una unidad (kg/m³) que no
son obviamente compatibles entre sí; el valor se persiste tal cual, sin
conversión ni validación de rango (ver `docs/fuentes.md`).

**Snapshot vs. histórico:** el snapshot (columnas de `Orden`) se
actualiza con cada lectura aceptada. El histórico (`DetalleCarga`, una
fila por lectura aceptada) no tiene frecuencia configurable en esta
versión — cada lectura aceptada genera una fila.

**`fechaInicioCarga`:** se fija en el instante de la primera lectura
aceptada, una sola vez; no se confunde con `fechaActivacion`. Errores:
`400`, `401`, `404`, `409` (estado incompatible o sin activar).

## `POST /api/v1/orders/{orderNumber}/cierre` — transición 2→3

Sin body. Response `200`:

```json
{"orderNumber": 940556, "estado": 3, "fechaFinCarga": "2026-10-21T19:43:05.798897"}
```

`fechaFinCarga` toma el valor de la última lectura **aceptada**, no el
instante de esta llamada (ese instante sí se registra, pero en el
historial de estados). Precondiciones: estado 2, activada, al menos una
lectura aceptada (las dos últimas son decisión del equipo, no texto
literal de la consigna). Errores: `401`, `404`, `409` (estado
incompatible, sin activar, o sin lecturas aceptadas), `500`. El cierre no
agrega ningún chequeo nuevo contra lecturas posteriores: alcanza con que
`registrarLectura` ya rechace cualquier orden fuera de estado 2.

## `POST /api/v1/orders/{orderNumber}/pesaje-final` — transición 3→4

Request: `{"pesoBrutoKg": 16080.000}`.

Response `200`:

```json
{"orderNumber": 900001, "taraKg": 12000.000, "pesoBrutoKg": 16080.000, "netoKg": 4080.000, "productoCargadoKg": 4000.000, "diferenciaKg": 80.000, "ultimaTemperaturaC": 18.50, "ultimaDensidad": 0.5400, "ultimoCaudalKgH": 3000.000}
```

Errores: `400`, `401`, `404`, `409` (orden no está en estado 3, incluye
pesaje repetido), `500`.

## `GET /api/v1/orders/{orderNumber}/conciliacion`

Sin body, sin escritura; puede llamarse repetidas veces y siempre
devuelve el mismo resultado que el pesaje final de esa orden. Errores:
`401`, `404`, `409` (orden no está en estado 4).

## Cálculo de la conciliación

- `netoKg = pesoBrutoKg - taraKg`.
- `productoCargadoKg` = última masa acumulada **aceptada** del snapshot
  de telemetría — **no** el `presetKg` (la consigna distingue
  explícitamente el preset del producto realmente cargado).
- `diferenciaKg = netoKg - productoCargadoKg`. Una diferencia distinta de
  cero **no** causa ningún rechazo; no se aplica tolerancia ni redondeo.
- `ultimaTemperaturaC`/`ultimaDensidad`/`ultimoCaudalKgH` son los
  **últimos** valores de telemetría (no promedios: los promedios
  quedan fuera de esta versión).
- Sin redondeo: la resta conserva toda la precisión de los valores
  persistidos.

## Concurrencia

Toda operación que cambia estado usa bloqueo pesimista por `orderNumber`
dentro de la misma transacción que la escritura (no se usa optimistic
locking). El bloqueo serializa las operaciones sobre una misma orden: no
se procesan dos a la vez, pero cada solicitud vuelve a evaluar sus
propias precondiciones de estado al tomar el lock. El resultado de cada
una depende de en qué estado encuentre la orden en ese momento (p. ej.
dos taras sobre una orden en estado 1 pueden dar una `200` y una `409`,
pero no es una regla general para toda combinación de operaciones). La
consulta de conciliación no usa bloqueo (es una lectura simple, no
participa de ninguna transición).

## Qué no cubre esta versión

Sin consulta/actualización/borrado de órdenes más allá de los endpoints
listados; sin CRUD de camión/chofer/cliente/producto; sin alta de
usuarios (ver `docs/ejecucion-local.md` para crear un usuario de prueba
directamente en la base); sin frecuencia configurable de histórico, sin
promedios de telemetría y sin alarmas (quedan para una versión posterior,
fuera de este parcial).
