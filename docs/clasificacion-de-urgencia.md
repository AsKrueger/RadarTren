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

Están en `IncidentDetector.kt`, calibrados con los feeds de un domingo por la mañana (439 servicios). **Es una sola muestra**, y hay que recalibrar con datos de días laborables y de días con incidencias reales.

| Situación | Media | Alta |
|---|---|---|
| Un tren | 30 min o más | 120 min o más |
| Red con al menos 8 trenes: parte con 15 min o más de retraso | 25 % | 40 % |
| Red: parte de los servicios cancelados, con mínimo de 3 | 10 % | 25 % |

Decisiones tomadas tras probarlo con datos reales:

- El umbral de Alta de un tren pasó de 60 a 120 minutos, porque 3 trenes superaban 60 en esa muestra y habrían sonado cada día.
- Las cancelaciones pasaron de cifra absoluta a proporción. Larga distancia tenía 12 en un día normal, sobre 147 trenes, y salía como Alta.

Los trenes de larga distancia cuentan como una sola red, de modo que una incidencia en un corredor concreto puede quedar diluida. Detectar por corredor está en la hoja de ruta.

## Cuándo se resuelve

Una incidencia se cierra cuando deja de aparecer en una lectura correcta de su fuente. Una lectura fallida no cierra nada, y si reaparece se reabre y se vuelve a notificar.
