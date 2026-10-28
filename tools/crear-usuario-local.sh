#!/usr/bin/env bash
# Alta de un usuario de prueba en la base local levantada con
# compose.local.yaml. Solo para desarrollo local; no crea ningún
# endpoint nuevo ni cambia lógica de negocio.
#
# Requiere que el backend haya arrancado al menos una vez (paso 5 del
# README): Hibernate crea las tablas `roles`/`users`/`userroles` con
# `ddl-auto=update` en ese momento, no antes. No requiere que el
# backend esté corriendo en este momento.
#
# El hash BCrypt se genera localmente, con la misma clase que usa el
# backend (BCryptPasswordEncoder, ya una dependencia resuelta por
# Maven), compilada al vuelo. No llama a /demo/encodepass por HTTP.
#
# La contraseña se pide por entrada interactiva (oculta), nunca como
# argumento, para que no quede en el historial de la shell.
#
# Si docker necesita sudo en esta máquina, el script lo detecta solo
# (no hace falta anteponer sudo al invocarlo); Maven y Java nunca
# corren como root.
#
# Uso (desde la raíz del repo):
#   tools/crear-usuario-local.sh <usuario> [rol]
set -euo pipefail

USERNAME="${1:?Uso: tools/crear-usuario-local.sh <usuario> [rol]}"
ROLE="${2:-ROLE_TEST}"

# Mismo largo de columna que `User.username` (VARCHAR(100)); solo
# caracteres que no rompen el literal SQL de más abajo.
if ! [[ "$USERNAME" =~ ^[A-Za-z0-9._-]{1,100}$ ]]; then
  echo "Usuario inválido: solo letras, números, '.', '_' o '-', entre 1 y 100 caracteres." >&2
  exit 1
fi
# `Role.name` no define longitud explícita (VARCHAR(255) por defecto de Hibernate).
if ! [[ "$ROLE" =~ ^[A-Za-z0-9_]{1,255}$ ]]; then
  echo "Rol inválido: solo letras, números o '_', entre 1 y 255 caracteres." >&2
  exit 1
fi

if [ ! -f .env.mysql ]; then
  echo "Falta .env.mysql en la raíz del repo (ver README, paso 3)." >&2
  exit 1
fi

DOCKER="docker"
if ! docker info >/dev/null 2>&1; then
  DOCKER="sudo docker"
  echo "docker necesita sudo en esta máquina; se usará 'sudo docker compose' solo para los comandos de Docker (Maven/Java siguen corriendo con tu usuario)." >&2
fi

printf 'Contraseña para %s (no se muestra, no queda en el historial): ' "$USERNAME" >&2
read -rs PASSWORD
printf '\n' >&2
[ -n "$PASSWORD" ] || { echo "Contraseña vacía, cancelado." >&2; exit 1; }

BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

cat > "$BUILD_DIR/EncodePassword.java" <<'JAVA'
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class EncodePassword {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (password == null || password.isEmpty()) {
            System.err.println("Password vacia.");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder().encode(password));
    }
}
JAVA

CP_FILE="$BUILD_DIR/classpath.txt"
( cd iw3 && ./mvnw -q -Dbuild=war dependency:build-classpath -Dmdep.outputFile="$CP_FILE" )
CLASSPATH="$(cat "$CP_FILE")"

javac -cp "$CLASSPATH" -d "$BUILD_DIR" "$BUILD_DIR/EncodePassword.java"
HASH=$(java -cp "$CLASSPATH:$BUILD_DIR" EncodePassword <<< "$PASSWORD")

# Si el usuario ya existe, informar sin tocar nada.
EXISTS_SQL="SELECT COUNT(*) FROM users WHERE username = '${USERNAME}';"
EXISTS=$($DOCKER compose --env-file .env.mysql -f compose.local.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u"$MYSQL_USER" "$MYSQL_DATABASE"' <<< "$EXISTS_SQL")

if [ "$EXISTS" != "0" ]; then
  echo "El usuario '${USERNAME}' ya existe. No se modificó nada." >&2
  exit 0
fi

# Alta de rol (si no existe), usuario y relación, en una sola transacción:
# si algo falla a mitad de camino, MySQL revierte todo al cerrar la conexión
# sin COMMIT (no queda un rol o un usuario huérfano).
$DOCKER compose --env-file .env.mysql -f compose.local.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' <<SQL
START TRANSACTION;

INSERT INTO roles (description, name)
SELECT 'Rol de prueba local', '${ROLE}'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = '${ROLE}');

SET @rol_id = (SELECT id FROM roles WHERE name = '${ROLE}');

INSERT INTO users (account_non_expired, account_non_locked,
                   credentials_non_expired, enabled, email, username, password)
VALUES (1, 1, 1, 1, '${USERNAME}@example.invalid', '${USERNAME}', '${HASH}');

SET @user_id = LAST_INSERT_ID();

INSERT INTO userroles (id_user, id_role) VALUES (@user_id, @rol_id);

COMMIT;
SQL

echo "Usuario '${USERNAME}' creado con el rol '${ROLE}'."
