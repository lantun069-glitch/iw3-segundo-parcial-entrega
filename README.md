# iw3 — Backend (Segundo parcial)

Proyecto integrador de Ingeniería Web III.

## Equipo
- Antún, Lucas Santiago Said
- Martinovic, Jovan
- Toledo Videla, Joaquín
- Rodríguez, Mateo Ariel

## Descripción
Backend en Spring Boot (Java 21) para la recepción y el seguimiento de
órdenes de carga de gas: API REST documentada con OpenAPI/Swagger,
persistida en MySQL, que cubre el circuito completo de una orden.

## Alcance de esta entrega
Recepción, tara/activación, lecturas periódicas, cierre y pesaje final
con conciliación. Por consigna de la cátedra, **el segundo parcial no
incluye interfaz gráfica**: se demuestra íntegramente contra la API, con
las colecciones Postman incluidas (ver [docs/pruebas.md](docs/pruebas.md)).
El frontend (`iw3-frontend`) es trabajo del final y no forma parte de
esta entrega.

El despliegue en la VM asignada por la cátedra ya se realizó y se
verificó junto con el equipo (28/10/2026): circuito completo contra
HTTPS, revisado con el runner de Postman. Detalle completo en
[docs/despliegue-vm.md](docs/despliegue-vm.md). Esto no incluye el
frontend ni los requisitos de F6–F8 del final.

**Estado actual de la VM:** actualizada el 30/10/2026 con el commit
`84cb795` (limpieza de ejemplos docentes aplicada — ver
`docs/decisiones.md`). Login, circuito completo (Postman F5) y OpenAPI
verificados contra `lantun069.duckdns.org`. Detalle completo, evidencia
y alcance de esa verificación en
[docs/despliegue-vm.md](docs/despliegue-vm.md).

## Arranque local, paso a paso (Ubuntu)

Pensado para clonar en una máquina Ubuntu limpia y tenerlo corriendo sin
adivinar nada. Usa dos terminales: **Terminal A** corre el backend en
primer plano y queda abierta; **Terminal B** es para todo lo demás
(Docker, SQL, curl) y no necesita quedar abierta entre pasos.

### 1. Requisitos e instalación

```bash
# Git, openssl (para generar JWT_SECRET), unzip (lo usa ./mvnw) y curl
# (paso 7, y lo usa el propio bloque de instalación de Docker más
# abajo): instalalo acá aunque Docker ya esté instalado, porque ese
# bloque se salta si Docker ya está.
sudo apt update
sudo apt install -y git openssl unzip curl
git --version
openssl version
unzip -v | head -1
curl --version | head -1
```

```bash
# JDK 21 — completo, no solo el runtime
sudo apt update
sudo apt install -y openjdk-21-jdk
java -version    # debe mostrar "21..."
javac -version   # debe mostrar "javac 21...": si falta, se instaló solo el JRE
```

Si tu versión de Ubuntu no tiene `openjdk-21-jdk` en los repositorios
(versiones muy anteriores a 22.04 LTS), instalá un JDK 21 desde
[adoptium.net](https://adoptium.net/) (Eclipse Temurin).

**Docker Engine + plugin de Compose.** Si ya los tenés instalados,
saltá este bloque:

```bash
docker --version && docker compose version && docker ps
# si las tres líneas corren sin error, ya está: pasá al punto 2
```

Si no, instalación desde el repositorio oficial de Docker:

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo systemctl enable --now docker
systemctl is-active docker        # debe imprimir "active"
docker --version
docker compose version
```

Por defecto, `docker` requiere `sudo`. Para no prefijar cada comando de
esta guía con `sudo`, agregá tu usuario al grupo `docker` una sola vez:

```bash
sudo usermod -aG docker "$USER"
```

Esto **no** toma efecto en la sesión actual con solo abrir una terminal
nueva: cerrá sesión y volvé a entrar (logout/login), o corré `newgrp
docker` en cada terminal donde lo necesites, y recién ahí probá:

```bash
docker ps   # si no pide sudo y no da error, quedó aplicado
```

Si preferís no tocar grupos del sistema, usá `sudo` delante de cada
comando `docker`/`docker compose` del resto de esta guía (el script del
paso 6 ya detecta esto solo, ver más abajo).

**Maven no requiere instalación aparte:** el wrapper `./mvnw`, dentro de
`iw3/`, descarga Maven 3.9.16 automáticamente la primera vez que se
ejecuta (necesita conexión a internet esa primera vez, y `unzip`, ya
instalado arriba).

### 2. Clonar el repositorio

```bash
cd ~   # o la carpeta donde quieras tener el proyecto
git clone https://github.com/lantun069-glitch/iw3-segundo-parcial-entrega.git
cd iw3-segundo-parcial-entrega
```

El resto de esta guía asume que estás parado en esta carpeta
(`iw3-segundo-parcial-entrega/`) salvo que se indique lo contrario.

### 3. Crear los archivos de entorno

Nunca se versionan: `.gitignore` ya los excluye. Desde la raíz del repo
(**Terminal B**):

```bash
cat > .env.mysql <<'EOF'
DB_USERNAME=iw3_app
DB_PASSWORD=cambiar-esta-password-app
MYSQL_ROOT_PASSWORD=cambiar-esta-password-root
EOF

echo "JWT_SECRET=$(openssl rand -base64 48)" > .env.jwt

chmod 600 .env.mysql .env.jwt
```

- `DB_USERNAME` / `DB_PASSWORD` son **ficticios** en el comando de
  arriba (`iw3_app` / `cambiar-esta-password-app`): reemplazalos por los
  que quieras usar, **antes** del paso 4. Lo importante es que **estos
  dos valores son los mismos para Docker (MySQL) y para Spring**: ambos
  leen el mismo archivo `.env.mysql`.
- `MYSQL_ROOT_PASSWORD` solo la usa el contenedor de MySQL (cuenta de
  administración de la base); Spring nunca la usa.
- `JWT_SECRET` se genera automáticamente, random, con `openssl rand`.

**Importante — esto solo vale para la primera vez:** MySQL usa
`DB_USERNAME`/`DB_PASSWORD`/`MYSQL_ROOT_PASSWORD` para crear esas
cuentas **únicamente cuando inicializa un volumen de datos vacío** (la
primera vez que corras el paso 4). Si editás `.env.mysql` **después** de
eso, el contenedor ya no vuelve a leer esos valores para las cuentas que
ya existen: Spring intentaría conectarse con la contraseña nueva contra
una base que todavía tiene la vieja, y fallaría. Si necesitás cambiar la
contraseña de una base ya inicializada, cambiala dentro de MySQL (`ALTER
USER ...`) — no la soluciones borrando el volumen: eso borra todos los
datos cargados hasta ese momento.

### 4. Levantar MySQL

Desde la raíz del repo (**Terminal B**):

```bash
docker compose --env-file .env.mysql -f compose.local.yaml up -d mysql
```

Que el contenedor aparezca "Up" no significa que MySQL ya acepte
conexiones (`compose.local.yaml` no define healthcheck). Esperar con un
límite de tiempo, primero el puerto TCP y después la conexión real:

```bash
MYSQL_WAIT_TIMEOUT=60   # segundos
elapsed=0
until timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/3306' 2>/dev/null; do
  if [ "$elapsed" -ge "$MYSQL_WAIT_TIMEOUT" ]; then
    echo "El puerto 3306 no abrió en ${MYSQL_WAIT_TIMEOUT}s. Revisá:" >&2
    echo "  docker compose --env-file .env.mysql -f compose.local.yaml ps" >&2
    echo "  docker compose --env-file .env.mysql -f compose.local.yaml logs mysql" >&2
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

docker compose --env-file .env.mysql -f compose.local.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT 1"' \
  >/dev/null || {
    echo "El puerto 3306 responde pero el usuario de aplicación todavía no puede autenticarse." >&2
    echo "Esperá unos segundos y repetí este último comando, o revisá:" >&2
    echo "  docker compose --env-file .env.mysql -f compose.local.yaml logs mysql" >&2
    exit 1
  }
echo "MySQL listo."
```

(`MYSQL_PASSWORD`, `MYSQL_USER` y `MYSQL_DATABASE` son las variables que
el propio contenedor ya tiene en su entorno, definidas en
`compose.local.yaml` a partir de tu `.env.mysql`; no hace falta
repetirlas a mano.)

### 5. Arrancar el backend

Otra terminal (**Terminal A**: esta queda abierta mientras uses la API).
Desde la raíz del repo:

```bash
cd iw3

set -a
source ../.env.mysql
source ../.env.jwt
set +a

export SPRING_PROFILES_ACTIVE=mysqldev

./mvnw -Dbuild=war -Pmysqldev spring-boot:run
```

- `source ../.env.mysql` y `source ../.env.jwt` exportan al proceso de
  Spring las **mismas** variables que usó Docker en el paso anterior
  (por eso no pueden quedar distintas entre los dos).
- `-Dbuild=war` activa el perfil de empaquetado que este `pom.xml`
  necesita para resolver `packaging.type` (sin este flag, Maven falla).
- `-Pmysqldev` selecciona el perfil **Maven**; `SPRING_PROFILES_ACTIVE=mysqldev`
  selecciona el perfil **Spring** — son cosas distintas y hacen falta
  las dos (sin la segunda, la app intenta conectarse por el puerto
  `33306` en vez de `3306`).

Esperar el log de arranque (`Started Iw3Application...`) antes de seguir.
Deja esta terminal abierta: cerrarla detiene el backend. **Este paso
también crea las tablas** (`roles`, `users`, `userroles`, `ordenes`, etc.)
con `ddl-auto=update` de Hibernate: tienen que existir antes del paso 6.

### 6. Crear un usuario de prueba

Esta versión no tiene endpoint de alta de usuarios. Otra terminal
(**Terminal B**), desde la raíz del repo, **después de que el backend
haya arrancado al menos una vez** (paso 5, para que existan las tablas):

```bash
bash tools/crear-usuario-local.sh usuario_prueba
```

Va a pedir la contraseña por teclado (oculta, no por argumento ni por
URL). El hash BCrypt se genera localmente con la misma clase que usa el
backend, sin llamar a ningún endpoint. Si el usuario ya existe, el
script lo informa y no toca nada; si `docker` necesita `sudo` en tu
máquina, lo detecta solo.

Para otro usuario, repetí el mismo comando con otro nombre (y, si
querés, un rol distinto como segundo argumento):

```bash
bash tools/crear-usuario-local.sh otro_usuario
```

### 7. Probar: login, Swagger, Postman

**Login y recurso protegido** (Terminal B) — con el usuario y la
contraseña que acabás de crear en el paso 6:

```bash
read -rp 'Usuario: ' TEST_USERNAME
read -rsp 'Contraseña: ' TEST_PASSWORD; echo

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/login \
  --data-urlencode "username=${TEST_USERNAME}" \
  --data-urlencode "password=${TEST_PASSWORD}")

# Sin token: el filtro de seguridad debe rechazar antes de llegar al controller.
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/orders/999999/conciliacion

# Con token, sobre una orden que no existe: debe llegar al controller y
# responder 404 de negocio (StandartResponse), no un 401.
curl -s http://localhost:8080/api/v1/orders/999999/conciliacion -H "Authorization: Bearer ${TOKEN}"
```

Sin token: `401` (cuerpo vacío, Spring Security corta antes del controller).
Con token: `404` con cuerpo `StandartResponse` (`message`/`code`/`devInfo`)
indicando que la orden `999999` no existe — confirma que el token fue
aceptado y que la petición llegó al controller de órdenes. Ese cuerpo es
la diferencia relevante: una ruta que no existe en esta app, aun con token
válido, también puede responder `401` (no `404`) por el reenvío interno a
`/error` que pierde la autenticación stateless — ver "Manejo de errores
sin advice global" en `docs/decisiones.md`. Por eso un `401` aislado no
alcanza para confirmar nada por sí solo, y lo que demuestra que el token
fue aceptado es específicamente este `404` con cuerpo `StandartResponse`.

**Swagger:** abrir en el navegador `http://localhost:8080/swagger-ui.html`.

**Postman — runner del circuito completo:**

1. Importar `postman/iw3-f5-runner-circuito.postman_collection.json` y
   `postman/iw3-local.postman_environment.example.json`.
2. Duplicar ese entorno importado (no editar el `.example` original) y,
   en la copia, completar las variables `testUsername` y `testPassword`
   escribiendo ahí el usuario y la contraseña **reales** que creaste en
   el paso 6 (no el texto literal `${TEST_USERNAME}`/`${TEST_PASSWORD}`:
   esos son solo nombres de variable de esta guía, no algo que Postman
   reconozca). `baseUrl` ya viene en `http://localhost:8080`.
3. Seleccionar esa copia en el selector de entornos de Postman.
4. Abrir **Runner**, elegir la colección y ejecutar.

Detalle completo de esta y las demás colecciones en
[docs/pruebas.md](docs/pruebas.md).

### 8. Detener y volver a iniciar (sin perder datos)

```bash
# Terminal A: Ctrl+C para detener Spring

# Terminal B, desde la raíz del repo
docker compose --env-file .env.mysql -f compose.local.yaml stop mysql
```

Para retomar más tarde, los datos quedan en el volumen de Docker
(`iw3-local_mysql-data`) mientras no se use `down -v`:

```bash
docker compose --env-file .env.mysql -f compose.local.yaml up -d mysql
# repetir el chequeo de espera del paso 4, y después el paso 5 para Spring
```

## Problemas habituales

| Síntoma | Causa probable | Qué hacer |
| --- | --- | --- |
| `permission denied` al hablar con `docker.sock` | Tu usuario no está en el grupo `docker`, o lo acabás de agregar pero no cerraste sesión | Paso 1 (`usermod -aG docker` + logout/login o `newgrp docker`), o usar `sudo` en cada comando `docker`/`docker compose` |
| `docker compose up` falla por puerto 3306 u 8080 ocupado | Otro proceso/contenedor ya usa ese puerto | `sudo ss -ltnp \| grep -E ':3306\|:8080'` para identificarlo y liberarlo. Si en cambio preferís cambiar el puerto de MySQL en `compose.local.yaml` (ej. `"3307:3306"`), también hay que actualizar la URL JDBC en `iw3/src/main/resources/application-mysqldev.properties` (`jdbc:mysql://localhost:3306/...`) al mismo puerto nuevo — cambiar solo el mapeo de Compose no alcanza, Spring seguiría apuntando al 3306 original |
| El chequeo del paso 4 falla después del tiempo límite | MySQL todavía estaba iniciando, o no llegó a arrancar | Revisar `docker compose ... logs mysql`; si recién inicializó el volumen puede tardar más la primera vez, correr el chequeo de nuevo |
| Spring no se conecta aunque MySQL "está listo", y nunca lo estuvo con este `.env.mysql` | Editaste `.env.mysql` después de la primera inicialización del volumen (ver aviso del paso 3): la cuenta ya existente en MySQL sigue con la contraseña vieja | No borres el volumen como arreglo automático: cambiá la contraseña dentro de MySQL (`ALTER USER`) o volvé a poner en `.env.mysql` el valor que usaste la primera vez |
| Spring no arranca, error de placeholder sin resolver (`${DB_USERNAME}` o similar) | Faltó `source` de `.env.mysql`/`.env.jwt` en la misma terminal donde corrés `./mvnw`, o se exportaron en otra terminal distinta | Repetir el paso 5 completo, en una sola terminal, en este orden |
| `java -version` no muestra 21, o `./mvnw`/`javac` fallan | Hay otra versión de Java activa por defecto, o solo instalaste el JRE | `update-alternatives --config java` (y `--config javac`) para elegir la 21, o exportar `JAVA_HOME` apuntando al JDK 21 completo antes de `./mvnw` o de `tools/crear-usuario-local.sh` |

## Documentación
- [docs/api.md](docs/api.md) — endpoints, estados, reglas y conciliación.
- [docs/pruebas.md](docs/pruebas.md) — cómo correr las colecciones Postman.
- [docs/decisiones.md](docs/decisiones.md) — decisiones técnicas de diseño.
- [docs/fuentes.md](docs/fuentes.md) — base docente, atribuciones y fuentes.
- [docs/despliegue-vm.md](docs/despliegue-vm.md) — despliegue verificado en la VM de la cátedra.
- [docs/plan-actualizacion-vm.md](docs/plan-actualizacion-vm.md) — plan de ese despliegue, con su estado de aplicación.
