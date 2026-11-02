# Clasificación de urgencia

Renfe no publica la gravedad de sus avisos. La urgencia la calcula RadarTren y **no es una clasificación oficial**.

| Nivel | Significado | Ejemplos |
|---|---|---|
| Crítica | Posible suceso de seguridad, según el texto de un aviso oficial | Descarrilamiento, arrollamiento, colisión, incendio, evacuación |
| Alta | Servicio interrumpido o muy alterado | "Sin servicio ferroviario"; 40 % de una red con retraso |
| Media | Alteración notable | Vía única, transbordos; retraso de 30 min o más |
| Baja | Previsto o de accesibilidad | Obras programadas, ascensores |
| Información | Sin impacto | "Recuperan sus frecuencias" |

Por defecto se notifica desde Alta. Se puede cambiar en Ajustes o desactivar. Cada nivel principal tiene su canal de notificaciones de Android.

## Dos orígenes

**Aviso de Renfe:** publicado por Renfe y clasificado por su texto.

**Detectada con datos en vivo:** calculada por la app. Describe lo que muestran los datos y **nunca afirma una causa**. Un retraso no prueba una avería, por eso una incidencia detectada nunca llega a Crítica.

## Reglas para avisos

Están en `AlertClassifier.kt`. Se aplican sobre el texto sin acentos ni mayúsculas, y gana la primera coincidencia, en este orden:

1. Se descartan los avisos vacíos y los de prueba.
2. Crítica: `descarril`, `arrollamiento`, `colisión`, `choque`, `accidente`, `desalojo`, `evacuación`, `incendio`.
3. Información: `recuperan sus frecuencias`, `restablecido`, `circulación ferroviaria en todo su recorrido`.
4. Alta: `sin servicio ferroviario`, `no se puede garantizar`, `servicio interrumpido`, `falta de suministro eléctrico`.
5. Media: `vía única`, `incidencia en la infraestructura`, `no presta servicio`, `transbordo`, `retraso`.
6. Baja: `ascensor`, `escaleras mecánicas`, `accesible`, `PMR`.

Si el texto menciona obras o un plan alternativo, baja a Obras programadas salvo que sea de seguridad o accesibilidad. Así, "suspendido el servicio por obras" no alarma.

**Limitaciones:** son reglas de palabras clave. La regla de Crítica puede dar falsos positivos si un aviso cita esas palabras en otro contexto, y se ha preferido eso a dejar pasar un suceso real. Además, los términos críticos no se han podido validar con avisos reales, porque la muestra no contenía ninguno. Avisos solo en catalán o euskera pueden quedar como Información.

## Umbrales de detección

Están en `IncidentDetector.kt`, calibrados con los feeds de un domingo por la mañana (439 servicios) y revisados con los de un lunes a las 11:07 (502 servicios). Falta contrastarlos con un día de incidencias graves reales.

| Situación | Media | Alta |
|---|---|---|
| Un tren | 30 min o más | 120 min o más |
| Red con al menos 8 trenes: parte con 15 min o más de retraso | 25 % | 40 % |
| Red: parte de los servicios cancelados, con mínimo de 3 | 10 % | 25 % |

Decisiones tomadas tras probarlo con datos reales:

- El umbral de Alta de un tren pasó de 60 a 120 minutos, porque 3 trenes superaban 60 en esa muestra y habrían sonado cada día.
- Las cancelaciones pasaron de cifra absoluta a proporción. Larga distancia tenía 12 en un día normal, sobre 147 trenes, y salía como Alta.

Resultado del lunes 2 de noviembre de 2026 a las 11:07: 190 trenes con 5 minutos o más de retraso, 24 con 30 o más y ninguno con 120. Solo Murcia/Alicante llegó a Alta por red, con 5 de 12 trenes, el 42 %, con 15 minutos o más. Larga distancia quedó en el 23 %, por debajo de Media. Las cancelaciones no superaron el 10 % en ninguna red. Con el umbral por defecto en Alta, eso supone pocas notificaciones en un día laborable normal, así que los umbrales se mantienen.

Los trenes de Cercanías de un núcleo que la app no conoce (como el prefijo `SP` visto ese lunes) no cuentan en ninguna red, para no mezclarse con larga distancia.

Los trenes de larga distancia cuentan como una sola red, de modo que una incidencia en un corredor concreto puede quedar diluida. Detectar por corredor está en la hoja de ruta.

## Cuándo se resuelve

Una incidencia se cierra cuando deja de aparecer en una lectura correcta de su fuente. Una lectura fallida no cierra nada, y si reaparece se reabre y se vuelve a notificar.
