# Decisiones técnicas

Decisiones de diseño necesarias para entender el código, no cubiertas ya
por `docs/api.md` ni `docs/ejecucion-local.md`.

## Credenciales por entorno

El historial heredado de la base docente tenía credenciales hardcodeadas
(BD, SMTP, clave JWT). Se externalizaron como variables de entorno, sin
default inseguro en el código: si falta alguna obligatoria, el arranque
falla o es rechazada explícitamente (caso `JWT_SECRET`).

## Perfiles Maven vs. perfiles Spring

Los perfiles del POM (`mysqldev`, `mysqlprod`, `cli1`, `cli2`) solo fijan
una propiedad Maven; no activan por sí solos el perfil Spring equivalente
en runtime, que hay que exportarlo aparte (`SPRING_PROFILES_ACTIVE`).

## Manejo de errores sin advice global

`OrdenRestController` resuelve sus propios casos de validación con
`@ExceptionHandler` locales en vez de un `@ControllerAdvice` global, para
evitar un forward interno a `/error` que, según cómo esté configurada la
seguridad, podía devolver `401` en vez de `400`. Si `/error` llega a
exponerse como ruta pública, su cuerpo por defecto puede incluir detalle
interno (stacktrace) según la configuración de Spring Boot vigente en ese
momento — esto no es una propiedad fija, depende de cómo quede
configurado. Por eso los catch de negocio de este controller nunca pasan
la excepción real a `StandartResponse` (`devInfo` siempre `"No stack
trace"`).

## Bloqueo pesimista y transacciones

Toda operación que cambia estado usa bloqueo pesimista por `orderNumber`
dentro de una transacción con `rollbackFor = Exception.class`, necesario
porque las excepciones de dominio son *checked* y Spring no las revierte
por defecto. No se usa `@Version`.

## Precisión temporal

Las fechas generadas por el servidor se truncan a microsegundos
(`ChronoUnit.MICROS`) antes de persistir, para coincidir con la precisión
de la columna `datetime(6)` de MySQL — sin esto, una respuesta inmediata
(en memoria) y una relectura posterior del mismo valor ya persistido no
coinciden byte a byte, lo que rompe la idempotencia esperada en
activación y conciliación.

## Auditoría

Se audita cada cambio de **estado** (creación, tara, cierre, pesaje
final) con una fila en el historial. La activación y una lectura
aceptada no agregan fila porque no cambian `estado`.
