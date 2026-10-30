# Plan de actualización de la VM (pendiente — no ejecutado)

Este documento es un plan para una etapa siguiente, separada de esta
limpieza. No se ejecutó ningún paso de este plan; la VM sigue sin
tocarse, con la versión descripta en `docs/despliegue-vm.md` (anterior
al retiro de ejemplos docentes, ver `docs/decisiones.md`). Su Swagger
actual muestra los endpoints heredados porque esa es la versión que
sigue desplegada.

## 1. Identificar la versión y configuración desplegadas actualmente

- En la VM: `cd /home/user/apps/iw3-segundo-parcial-2026 && git log -1`
  (o, si no es un checkout git sino el código copiado, revisar
  `target/ROOT.war` y la fecha de despliegue del 28/10/2026 en
  `docs/despliegue-vm.md`).
- Registrar el perfil activo (`mysqlprod`), la imagen de Tomcat
  (`tomcat:11.0.27-jdk21-temurin`), los montajes vigentes
  (`tomcat/bin/setenv.sh`, `tomcat/conf/server-11.xml` con
  `RemoteIpValve`) y el estado de nginx/certificados por dominio
  (`lantun069`, `jovan-iw3`, `jtoledo495`, `mrodriguez579`).
- Confirmar el estado de la base `iw3_prod_2026` (cuentas habilitadas:
  `usuario_parcial`; `usuario_prueba` deshabilitado) tal como quedó
  registrado en `docs/despliegue-vm.md`.

## 2. Respaldo previo a sustituir el artefacto

- WAR actual: copiar `target/ROOT.war` (o el desplegado en
  `tomcat/webapps/`) a una ruta de respaldo con fecha.
- Configuración: `infra.tar.gz` del proyecto Compose (`docker-compose.yml`,
  `tomcat/conf/server-11.xml`, `tomcat/bin/setenv.sh`), igual que el
  respaldo que ya se tomó antes del despliegue anterior
  (`/home/user/backups/iw3-antes-segundo-parcial-20261026-121006/`).
- Base de datos: `mysqldump` de `iw3_prod_2026` (o el `.sql` equivalente
  al que ya existe en el respaldo previo), verificando que el archivo no
  esté vacío antes de continuar.
- No se borra el volumen de MySQL ni ninguna tabla existente en ningún
  paso de este plan.

## 3. Reemplazo del artefacto

- Clonar el repositorio de entrega
  (`https://github.com/lantun069-glitch/iw3-segundo-parcial-entrega`)
  en la VM, en un directorio nuevo junto al actual (no sobrescribir en
  el lugar hasta confirmar el build).
- Usar un commit explícito y aprobado por el equipo — no la rama de
  trabajo (`chore/retirar-ejemplos-docentes`) sin mergear/aprobar antes.
- Empaquetar igual que el despliegue anterior:
  `./mvnw -Dbuild=war -Pmysqlprod -DskipTests clean package` (la
  evidencia funcional sigue siendo la corrida de Postman, no la
  compilación — igual criterio que el despliegue anterior).
- Sustituir `target/ROOT.war` en `tomcat/webapps/` y reiniciar el
  contenedor `backend`. No se toca `tomcat/conf/server-11.xml`
  (`RemoteIpValve`), ni `tomcat/bin/setenv.sh`, ni la imagen de Tomcat,
  ni nginx, ni los certificados, ni los dominios: nada de eso cambió en
  esta limpieza.
- No se crean ni se borran usuarios ni datos: `usuario_parcial` y el
  resto de las filas existentes en `iw3_prod_2026` quedan igual.

## 4. Verificación posterior (por HTTPS, igual método que el despliegue anterior)

- Login con `usuario_parcial` contra `https://lantun069.duckdns.org`.
- Circuito completo con el runner de Postman F5 (2+ iteraciones), igual
  que la corrida registrada en `docs/despliegue-vm.md`.
- `GET /v3/api-docs` por HTTPS: confirmar que los paths retirados
  (`/api/v1/products*`, `/api/v1/integration/*`, `/api/v1/authtest/*`,
  `/api/v1/mail/*`, `/demo/*`) ya no aparecen, y que `/api/v1/orders/*`
  y `/api/v1/login` sí.
- Repetir la comprobación de disponibilidad de `/v3/api-docs` en los
  otros tres dominios, igual alcance que la verificación anterior (solo
  disponibilidad, no el circuito completo).
- Registrar los resultados reales de esta corrida como una sección
  nueva en `docs/despliegue-vm.md`, sin reemplazar la evidencia del
  despliegue anterior — ambas quedan, con su fecha.

## 5. Rollback si falla

- Restaurar `target/ROOT.war` desde el respaldo del paso 2 en
  `tomcat/webapps/` y reiniciar el contenedor `backend` — la base de
  datos no se tocó, así que no hace falta restaurar el `.sql` salvo que
  el nuevo despliegue haya escrito datos inválidos.
- Si hiciera falta restaurar la base: usar el `mysqldump` del paso 2
  sobre la misma base `iw3_prod_2026`, sin borrar el volumen.
- Confirmar el rollback con el mismo login + circuito completo del
  punto 4 antes de considerar la VM estable otra vez.
