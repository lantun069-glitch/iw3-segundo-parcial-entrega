# Fuentes y atribuciones

## Base docente (código)

`https://github.com/magm3333/iw3_2026`, commit
`b7e705de737e5b1039719807bb413ec89279e7d1` (rama `main`, "Backend
completo"). El proyecto no declara una licencia propia; los componentes
de terceros incluidos (Maven Wrapper en `iw3/mvnw`/`iw3/mvnw.cmd`,
librerías JS en `iw3/src/main/resources/static/ui/js/`) conservan sus
avisos de licencia originales, sin modificaciones.

## Consigna del trabajo integrador

Fuente: transcripciones oficiales de la consigna (cátedra), citas
textuales. Las reglas de negocio que estas citas respaldan están
aplicadas en `docs/api.md`.

- **Clave de activación:** "...cuando el camión arriba a la planta en el
  turno asignado, se registra el pesaje del camión vacío que es la
  tara. Se agrega una contraseña de activación compuesta por un número
  entero de cinco dígitos que se asocia a la orden... Al completar este
  paso, la orden pasa a estado dos." La clave se genera en la misma
  operación que registra la tara.
- **Descarte de lecturas:** "Se deben descartar los registros cuyo
  caudal sea menor igual que 0... Y cuando la masa acumulada sea menor
  igual que 0... tampoco tengo que almacenar una masa menor que el valor
  anterior, eso sería un error porque la masa siempre es acumulativa."
  La fuente dice "menor", no "menor o igual": la igualdad de masa no se
  descarta.
- **Densidad:** "La densidad del producto es un valor entre 0 y 1 y la
  unidad de medida es kilogramos por metro cúbico, esto es un dato
  anecdótico por ahora que simplemente lo tenemos que guardar." Rango y
  unidad citados no son evidentemente compatibles entre sí; "anecdótico"
  se lee como "no es el foco de la lógica de negocio", no como "sin
  validación de rango" — son afirmaciones distintas y la fuente solo
  sostiene la primera. Queda como ambigüedad de la fuente, sin resolver
  por cuenta propia.
- **Conciliación:** "el neto por balanza... sería el pesaje final menos
  el pesaje inicial"; "esa diferencia puede existir, y ahí la decisión
  después de qué se hace con esa diferencia no es un problema nuestro
  sino una decisión humana" — una diferencia distinta de cero no implica
  rechazo automático por parte del sistema.
- **Segundo parcial sin interfaz gráfica:** "Deberán implementarse sin
  interfaz gráficas... yo no quiero interfaz gráficas para el segundo
  parcial." Por eso esta entrega se demuestra con Postman, sin frontend
  (ver `docs/pruebas.md`).
- **Evaluación individual:** "la persona que no me contesta no aprueba"
  — cada integrante debe poder responder sobre cualquier parte del
  sistema, no solo la propia.

## Herramientas utilizadas

- **Herramientas de IA:** apoyo al desarrollo y la documentación, con
  revisión del equipo.
- **Postman CLI** (`postman collection run`): ejecución de las
  colecciones versionadas en `postman/` (ver `docs/pruebas.md`).
- **NotebookLM:** consulta de las fuentes oficiales de cátedra citadas
  en este documento.
