# Despliegue en la VM (segundo parcial)

Este documento describe el despliegue del backend del segundo parcial en
la VM asignada por la cátedra, ya realizado y verificado junto con el
equipo el 28/10/2026. Cubre únicamente el alcance de esta entrega (API,
Swagger, Postman); no incluye frontend ni los requisitos de F6–F8 del
proyecto final, que no se desplegaron ni se declaran cerrados acá.

## Arquitectura

```
cliente (Postman / curl)
        │  HTTPS (443)
        ▼
      nginx  (nginx:1.23.3) ── termina TLS, reenvía al backend
        │  HTTP (8080, red interna)
        ▼
     backend (tomcat:11.0.27-jdk21-temurin) ── WAR Spring Boot
        │  JDBC (3306, red interna)
        ▼
   mysql_iw3 (mysql:8.0.43) ── base iw3_prod_2026
```

`adminer` se mantuvo como servicio ya existente del proyecto Compose, sin
cambios.

## Ubicaciones en la VM

- Proyecto Compose reutilizado: `/home/user/infra/docker-compose.yml`.
- Código clonado para esta entrega: `/home/user/apps/iw3-segundo-parcial-2026`.
- Respaldo tomado antes del despliegue:
  `/home/user/backups/iw3-antes-segundo-parcial-20261026-121006/`
  (incluye `infra.tar.gz` y `iw3_prod_2026.sql`; se verificó la
  integridad gzip y que el SQL no estuviera vacío, sin hacer una
  restauración de prueba).

El volumen de MySQL existente se preservó: no se borraron los datos de
la actividad anterior del curso.

## Empaquetado

Desde `iw3/`, en el código clonado en la VM:

```bash
./mvnw -Dbuild=war -Pmysqlprod -DskipTests clean package
```

Resultado: `BUILD SUCCESS`, artefacto `target/ROOT.war`. Este empaquetado
**omite los tests** (`-DskipTests`); la evidencia funcional de esta
entrega es la corrida de Postman descripta más abajo, no la compilación.

## Adaptación de Tomcat y HTTPS

El contenedor de Tomcat usado en la actividad anterior
(`magm3333/tomcat-jdk21:10.1.44`) no es compatible con este WAR: el
proyecto corre sobre Spring Boot 4.1.1 / Servlet 6.1, que requiere
Tomcat 11. Se cambió la imagen a `tomcat:11.0.27-jdk21-temurin` sin
modificar el código del producto.

Montajes actuales del servicio `backend`:

```
./tomcat/webapps:/usr/local/tomcat/webapps
./tomcat/bin/setenv.sh:/usr/local/tomcat/bin/setenv.sh:ro
./tomcat/conf/server-11.xml:/usr/local/tomcat/conf/server.xml:ro
/etc/localtime:/etc/localtime:ro
```

Perfil Spring activo: `mysqlprod`, conectado por JDBC al servicio
`mysql` y a la base `iw3_prod_2026`. Credenciales y `JWT_SECRET` quedan
en configuración privada de la VM, fuera de este repositorio.
`EXPIRED_NOTIFICATION_ENABLED=false`: no se verificó envío de correo
real en este despliegue.

**Problema encontrado:** nginx termina TLS y reenvía al backend por
HTTP interno; con Tomcat externo (no el embebido de Spring Boot),
`SERVER_FORWARD_HEADERS_STRATEGY=framework` por sí solo no corrigió la
URL que Spring genera en las respuestas (por ejemplo, `servers.url` en
el OpenAPI) — seguía resolviendo HTTP en vez de HTTPS.

**Cambio aplicado:** se agregó una `RemoteIpValve` al `Engine` activo de
`server-11.xml`:

```xml
<Valve className="org.apache.catalina.valves.RemoteIpValve"
       remoteIpHeader="X-Forwarded-For"
       protocolHeader="X-Forwarded-Proto"
       protocolHeaderHttpsValue="https" />
```

El archivo queda montado de forma persistente (ver tabla de montajes
arriba). Después de este cambio se verificó la URL HTTPS correcta en el
OpenAPI expuesto. Esto se documenta como la configuración observada en
este despliegue puntual (Tomcat externo detrás de nginx), no como una
receta universal para cualquier combinación de proxy/contenedor.

## Evidencia de verificación (28/10/2026)

**Circuito completo**, ejecutado desde Postman Desktop en la computadora
del usuario, entorno IW3 VM, contra `https://lantun069.duckdns.org`,
colección F5 Runner:

| Métrica | Resultado |
| --- | --- |
| Iteraciones | 2 |
| Peticiones | 18 |
| Comprobaciones aprobadas | 68 |
| Comprobaciones fallidas | 0 |
| Comprobaciones omitidas | 0 |
| Duración | 13,419 s |

Ambas iteraciones completaron login, recepción, tara, activación, dos
lecturas, cierre, pesaje final y consulta de conciliación; la
conciliación consultada coincidió con la respuesta del pesaje final.
Evidencia: capturas revisadas por el usuario, no una ejecución ni un
reporte exportado por este asistente.

Este circuito completo **solo se ejecutó contra `lantun069`**, no contra
los otros tres dominios listados abajo.

**Dominios**, consultados con `curl` desde la propia VM en
`/v3/api-docs`:

| Dominio | Resultado |
| --- | --- |
| `lantun069.duckdns.org` | HTTPS OK, OpenAPI 3.1.0, `servers.url` = `https://lantun069.duckdns.org` |
| `jovan-iw3.duckdns.org` | HTTPS OK, OpenAPI 3.1.0, `servers.url` = `https://jovan-iw3.duckdns.org` |
| `jtoledo495.duckdns.org` | HTTPS OK, OpenAPI 3.1.0, `servers.url` = `https://jtoledo495.duckdns.org` |
| `mrodriguez579.duckdns.org` | HTTPS OK, OpenAPI 3.1.0, `servers.url` = `https://mrodriguez579.duckdns.org` |

Esta verificación por dominio fue solo de disponibilidad del OpenAPI por
HTTPS; no implica que el circuito completo se haya ejecutado en los
cuatro dominios.

**Cuentas de prueba** en la base `iw3_prod_2026`:

- `usuario_prueba` quedó deshabilitado (`enabled=0`).
- `usuario_parcial` quedó habilitado (`enabled=1`); su contraseña se
  cambió mediante un hash BCrypt generado localmente en la VM (sin
  exponerla por HTTP ni en comandos), y el `UPDATE` confirmó una sola
  fila modificada. El login posterior con la contraseña nueva devolvió
  `200 OK` y token, confirmado por el usuario.

No se incluyen contraseñas, hashes ni tokens en este documento ni en el
repositorio. El cambio de contraseña no revocó los tokens JWT emitidos
previamente (este backend no implementa una lista de revocación); eso
no se verificó como corregido en este despliegue.

## Límites de esta verificación

- El empaquetado de producción se hizo con `-DskipTests`: la evidencia
  funcional es la corrida de Postman, no la suite de tests del proyecto.
- El circuito completo (Postman F5) se ejecutó una sola vez, contra un
  solo dominio (`lantun069`); los otros tres dominios solo se
  verificaron por disponibilidad del OpenAPI.
- No se verificó el envío de correo real (`EXPIRED_NOTIFICATION_ENABLED=false`).
- El respaldo previo al despliegue se verificó por integridad (gzip y
  tamaño del SQL), sin una restauración de prueba.
- Este despliegue cubre el alcance del segundo parcial (API, Swagger,
  Postman). No incluye el frontend ni los requisitos de F6–F8 del final
  (roles/autorización completa, alarmas por correo, UI), que siguen sin
  desplegarse y sin declararse cerrados.
