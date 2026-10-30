# Plan de actualización de la VM (pendiente — no ejecutado)

Este documento es un plan para una etapa siguiente, separada de esta
limpieza. No se ejecutó ningún paso de este plan; la VM sigue sin
tocarse, con la versión descripta en `docs/despliegue-vm.md` (anterior
al retiro de ejemplos docentes, ver `docs/decisiones.md`). Su Swagger
actual muestra los endpoints heredados porque esa es la versión que
sigue desplegada.

Los valores de rutas, imágenes y configuración citados abajo son los
que quedaron **registrados** en `docs/despliegue-vm.md` al momento de
ese despliegue (28/10/2026); no se volvieron a inspeccionar en la VM
para este plan. El paso 1 es, precisamente, confirmarlos en la VM real
antes de tocar nada — no asumir que siguen vigentes sin esa
confirmación.

## 1. Identificar la versión y configuración desplegadas actualmente

- En la VM: `cd /home/user/apps/iw3-segundo-parcial-2026 && git log -1`
  (o, si no es un checkout git sino el código copiado, inspeccionar el
  WAR real — ver el paso 2).
- Confirmar en la VM, sin asumirlos del documento: el perfil activo
  (registrado como `mysqlprod`), la imagen de Tomcat (registrada como
  `tomcat:11.0.27-jdk21-temurin`), los montajes vigentes (registrados
  como `tomcat/bin/setenv.sh`, `tomcat/conf/server-11.xml` con
  `RemoteIpValve`) y el estado de nginx/certificados por dominio
  (`lantun069`, `jovan-iw3`, `jtoledo495`, `mrodriguez579`).
- Confirmar el estado de la base `iw3_prod_2026` (cuentas registradas:
  `usuario_parcial` habilitado, `usuario_prueba` deshabilitado) tal
  como quedó en `docs/despliegue-vm.md`, y si hubo actividad posterior
  a esa fecha que no esté documentada.

## 2. Respaldo previo a sustituir el artefacto

Respaldo en una ruta **fuera de `tomcat/webapps/` y de cualquier
directorio de despliegue automático** (p. ej.
`/home/user/backups/iw3-rollback-<fecha>/`, siguiendo el mismo patrón
que ya usa `/home/user/backups/iw3-antes-segundo-parcial-20261026-121006/`
para el despliegue anterior) — no basta con renombrar dentro de
`webapps/`, porque Tomcat puede seguir escaneando esa carpeta y tratar
cualquier WAR o subcarpeta que encuentre ahí como una aplicación más a
desplegar:

- **WAR realmente desplegado:** antes de nada, identificar el artefacto
  que está corriendo ahora, no el que se espera que esté corriendo:
  ```bash
  sha256sum /ruta/real/tomcat/webapps/ROOT.war
  ```
  Registrar ese hash en la bitácora de este despliegue, y copiarlo a
  `/home/user/backups/iw3-rollback-<fecha>/ROOT.war` (fuera de
  `webapps/`). Volver a calcular el hash sobre la copia para confirmar
  que coincide con el original antes de seguir.
- **Configuración:** `infra.tar.gz` del proyecto Compose
  (`docker-compose.yml`, `tomcat/conf/server-11.xml`,
  `tomcat/bin/setenv.sh`), igual que el respaldo que ya se tomó antes
  del despliegue anterior
  (`/home/user/backups/iw3-antes-segundo-parcial-20261026-121006/`).
  Verificar la integridad del `.tar.gz` (`tar -tzf infra.tar.gz
  >/dev/null`) y que no esté vacío (`du -h infra.tar.gz`).
- **Base de datos:** `mysqldump` de `iw3_prod_2026` a un archivo con
  fecha. Verificaciones concretas, sin imprimir contenido ni
  credenciales:
  ```bash
  mysqldump --single-transaction iw3_prod_2026 > iw3_prod_2026-<fecha>.sql
  echo "exit mysqldump: $?"               # debe ser 0
  gzip -t iw3_prod_2026-<fecha>.sql.gz 2>/dev/null; echo "gzip ok: $?"  # si se comprime
  wc -l iw3_prod_2026-<fecha>.sql          # > 0 líneas, sin mostrar contenido
  grep -c "^INSERT INTO" iw3_prod_2026-<fecha>.sql  # > 0, confirma que hay datos
  ```
- No se borra el volumen de MySQL ni ninguna tabla existente en ningún
  paso de este plan.

## 3. Reemplazo del artefacto

- Clonar el repositorio de entrega
  (`https://github.com/lantun069-glitch/iw3-segundo-parcial-entrega`)
  en la VM, en un directorio nuevo junto al actual (no sobrescribir en
  el lugar hasta confirmar el build).
- Usar un commit explícito y aprobado por el equipo — no una rama de
  trabajo sin mergear/aprobar antes.
- Empaquetar igual que el despliegue anterior:
  `./mvnw -Dbuild=war -Pmysqlprod -DskipTests clean package` (la
  evidencia funcional sigue siendo la corrida de Postman, no la
  compilación — igual criterio que el despliegue anterior). Calcular y
  registrar el `sha256sum` del WAR nuevo antes de desplegarlo, para
  poder distinguirlo del respaldo del paso 2 si hiciera falta.
- **Secuencia controlada, deteniendo únicamente el backend** (no
  `mysql`, no `nginx`, no `adminer`). El backend queda detenido durante
  **toda** la sustitución (pasos 1 a 4 siguientes), no solo durante la
  copia del WAR:
  1. `docker compose stop backend` (el resto de los servicios sigue
     arriba; nginx seguirá respondiendo con el backend caído, con el
     error que corresponda a esa falla puntual).
  2. **Carpeta expandida, movida fuera de `webapps/`:** Tomcat expande
     el WAR en `tomcat/webapps/ROOT/` la primera vez que lo despliega;
     si esa carpeta ya existe de un despliegue anterior, Tomcat puede
     no volver a expandirla aunque el `.war` sea nuevo (depende de
     timestamps), y si se la deja renombrada dentro de `webapps/`
     (p. ej. `ROOT.bak-<fecha>`) Tomcat puede seguir escaneándola como
     una aplicación más. Por eso se mueve, no se renombra en el lugar:
     ```bash
     mv tomcat/webapps/ROOT /home/user/backups/iw3-rollback-<fecha>/ROOT
     ```
     Con esto, `webapps/` queda sin la carpeta expandida ni el WAR
     viejo (ya respaldado en el paso 2) — un solo artefacto desplegable
     a la vez, nunca dos.
  3. Copiar el WAR nuevo a `tomcat/webapps/ROOT.war`.
  4. `docker compose start backend` y esperar el log de arranque
     (`Started Iw3Application`) antes de seguir.
  5. Recién si arrancó bien y la verificación del paso 4 de este
     documento pasa, se puede borrar el respaldo de
     `/home/user/backups/iw3-rollback-<fecha>/` (no antes, y no es
     obligatorio: ocupa espacio pero no interfiere con nada mientras
     quede fuera de `webapps/`).
- No se toca `tomcat/conf/server-11.xml` (`RemoteIpValve`), ni
  `tomcat/bin/setenv.sh`, ni la imagen de Tomcat, ni nginx, ni los
  certificados, ni los dominios, ni el volumen de MySQL: nada de eso
  cambió en esta limpieza de código.
- **Sobre los datos existentes:** no se borra ni se modifica
  manualmente ninguna fila de `usuario_parcial` ni del resto de
  `iw3_prod_2026`. Pero afirmar que "los datos quedan iguales" no es
  correcto para todo el despliegue: el paso 4 (verificación) ejecuta el
  runner F5 contra la VM, y ese runner **sí crea datos nuevos** (una
  orden, un camión, un chofer, un cliente y un producto por iteración
  ejecutada — ver más abajo cómo identificarlos). Además,
  `ddl-auto=update` puede modificar el esquema si hay alguna diferencia
  entre las entidades de este código y las tablas existentes (no
  debería haberla para el dominio de órdenes, que no cambió en esta
  limpieza, pero `ddl-auto=update` no es un no-op garantizado: solo
  agrega/ajusta columnas, nunca borra tablas ni columnas existentes).

## 4. Verificación posterior (por HTTPS)

- Login con `usuario_parcial` contra `https://lantun069.duckdns.org`.
- Circuito completo con el runner de Postman F5 (2+ iteraciones) **solo
  contra `lantun069`** — mismo alcance que la verificación del
  despliegue anterior (`docs/despliegue-vm.md`): ese dominio es donde
  se ejecuta el circuito completo, no los cuatro.
- `GET /v3/api-docs` por HTTPS, en **los cuatro dominios**
  (`lantun069`, `jovan-iw3`, `jtoledo495`, `mrodriguez579`): confirmar
  que los paths retirados (`/api/v1/products*`, `/api/v1/integration/*`,
  `/api/v1/authtest/*`, `/api/v1/mail/*`, `/demo/*`) ya no aparecen, y
  que `/api/v1/orders/*` y `/api/v1/login` sí. Esto es solo
  disponibilidad del OpenAPI por HTTPS en los cuatro — **no** implica
  que el circuito completo se haya ejecutado en los cuatro, igual
  distinción que ya hace `docs/despliegue-vm.md` para el despliegue
  anterior.
- Registrar los resultados reales de esta corrida como una sección
  nueva en `docs/despliegue-vm.md`, con su fecha, sin reemplazar la
  evidencia del despliegue anterior — ambas quedan.

### Identificar los datos de prueba creados por esta verificación

El runner F5 (`postman/iw3-f5-runner-circuito.postman_collection.json`)
genera sus propios valores de prueba en el pre-request del primer item,
sin necesidad de agregar ninguna marca nueva:
- `orderNumber` = `Date.now() % 1000000000 + iteración` (un entero
  derivado de la hora de esa corrida).
- `camion.externalCode`, `chofer.externalCode`, `cliente.externalCode`
  y `producto.externalCode` siguen el patrón `*-F5R-<mismo sufijo
  numérico>` (p. ej. `CAM-F5R-173...`, `PRD-F5R-173...`).

Para esta verificación puntual: registrar la hora de inicio y fin de la
corrida contra `lantun069`, y los dos `orderNumber` que haya generado
(uno por iteración). Eso alcanza para ubicar las filas creadas en
`ordenes`, `camiones`, `choferes`, `clientes` y `productos_orden` sin
tener que adivinar por contenido.

## 5. Si algo falla — rollback del artefacto y recuperación de la base, por separado

Estas dos cosas no son el mismo problema y no se resuelven con la misma
acción:

**Rollback del artefacto** (si el backend no arranca, o el circuito
falla por el código nuevo). Igual secuencia que el despliegue, en
sentido inverso, para no dejar nunca dos aplicaciones desplegables a
la vez en `webapps/`:
1. `docker compose stop backend`.
2. Sacar de `webapps/` el WAR nuevo y, si llegó a expandirse, su
   `ROOT/` — moverlos a una ruta de respaldo del intento fallido
   (fuera de `webapps/`), no borrarlos directamente, para poder
   revisarlos después.
3. Copiar de vuelta a `tomcat/webapps/ROOT.war` el WAR respaldado en
   el paso 2 original (`/home/user/backups/iw3-rollback-<fecha>/ROOT.war`),
   confirmando su hash contra el registrado antes de copiarlo. No es
   necesario restaurar la carpeta `ROOT/` vieja: Tomcat la vuelve a
   expandir desde ese WAR al arrancar.
4. `docker compose start backend` y esperar el log de arranque.

Esto no requiere tocar la base de datos.

**Recuperación de la base** (solo si el despliegue nuevo corrompió o
modificó datos existentes de forma incorrecta — no por precaución
automática): **no restaurar el `mysqldump` del paso 2 sin más.** Entre
el respaldo y el momento del rollback pudo haber actividad legítima
(logins, órdenes reales) además de los datos de prueba de la
verificación del paso 4; restaurar ese dump completo borraría también
esa actividad legítima. Antes de considerar una restauración completa:
1. Intentar primero borrar únicamente las filas identificadas en la
   sección anterior (por `orderNumber` y por el patrón
   `*-F5R-<sufijo>` en `externalCode`), no el dump completo.
2. Si el problema es de esquema (una columna que `ddl-auto=update`
   dejó en un estado inesperado) y no de filas puntuales, evaluarlo
   específicamente antes de restaurar nada — una restauración completa
   es el último recurso, no el primer paso, y requiere confirmación
   explícita del equipo antes de ejecutarse.
- Confirmar el rollback con el mismo login + circuito completo del
  punto 4 antes de considerar la VM estable otra vez.
