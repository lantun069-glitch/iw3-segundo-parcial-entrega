# Ejecución local

La guía paso a paso para clonar, configurar y ejecutar el backend vive en
el [README](../README.md), sección "Arranque local, paso a paso". Este
archivo no la duplica para evitar que las dos queden desactualizadas por
separado.

Referencia rápida de variables de entorno (detalle completo de cada una
en el README, paso 3):

| Variable | Dónde se usa |
| --- | --- |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL (Compose) y Spring — mismo archivo, mismo valor. Solo se aplican al inicializar el volumen por primera vez (ver README, paso 3) |
| `MYSQL_ROOT_PASSWORD` | Solo MySQL (Compose); Spring no la usa |
| `JWT_SECRET` | Spring, firma/verificación de JWT |
| `SMTP_USER`, `SMTP_PASS` | Spring, exigidas para arrancar; no se usan para enviar correo real en esta entrega |
| `SPRING_PROFILES_ACTIVE`, `EXPIRED_NOTIFICATION_ENABLED` | Spring, perfil runtime y job de notificación (deshabilitado) |

Para crear un usuario de prueba, ver el README, paso 6: el procedimiento
es `tools/crear-usuario-local.sh`, tanto para el primer usuario como
para los siguientes. Requiere que el backend haya arrancado al menos
una vez (crea las tablas).
