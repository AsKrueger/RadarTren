# Fuentes de datos

Comprobado el 1 de noviembre de 2026, descargando y midiendo los feeds. Si un feed cambia, empieza por aquí.

## Lo que usa la app

Todos son datos abiertos oficiales de [Renfe Data](https://data.renfe.com/dataset), con licencia CC BY 4.0, sin clave de acceso.

| Feed | URL | Renfe dice que se actualiza | Tamaño `.pb` | Tamaño `.json` |
|---|---|---|---|---|
| Posiciones Cercanías | `gtfsrt.renfe.com/vehicle_positions.pb` | Tiempo real | 24 KB | 105 KB |
| Posiciones AV/LD/MD | `gtfsrt.renfe.com/vehicle_positions_LD.pb` | 15 s | 10 KB | 49 KB |
| Retrasos Cercanías | `gtfsrt.renfe.com/trip_updates.pb` | Tiempo real | 20 KB | 114 KB |
| Retrasos AV/LD/MD | `gtfsrt.renfe.com/trip_updates_LD.pb` | 30 s | 12 KB | 54 KB |
| Avisos e incidencias | `gtfsrt.renfe.com/alerts.pb` | 20 s | 43 KB | 100 KB |

La muestra del 1 de noviembre tenía unos 270 trenes de Cercanías y 145 de larga distancia circulando.

### Datos estáticos

Los nombres de estaciones, líneas y trenes vienen de los GTFS oficiales. Se empaquetan en `app/src/main/assets/catalog/` y ocupan 116 KB.

- Larga distancia: `https://ssl.renfe.com/gtransit/Fichero_AV_LD/google_transit.zip`
- Cercanías: `https://ssl.renfe.com/ftransit/Fichero_CER_FOMENTO/fomento_transit.zip`

Para actualizarlos, descarga ambos y ejecuta:

```powershell
python tools/generate_catalog.py google_transit.zip fomento_transit.zip
```

Un tren o estación que no esté en el catálogo se sigue mostrando, sin nombre. Conviene regenerarlo con cada cambio de horarios.

## Problemas reales encontrados

Estos comportamientos no aparecen en la documentación de Renfe. Cada uno condiciona el diseño.

### 1. El feed de avisos estaba congelado

`alerts.pb` y `alerts.json` responden `200 OK`, pero su `Last-Modified` era del **7 de octubre de 2026 a las 07:32 UTC**, 25 días antes de la comprobación. Publicaba como vigentes avisos ya pasados, como "Sin servicio ferroviario" en la R2 o unas obras "del 10 al 12 de octubre".

**Solución:** cada feed se evalúa por sus cabeceras HTTP (`FeedHealth.kt`). Los avisos de un feed congelado se guardan como no verificados, se muestran aparte con advertencia, nunca notifican y no cuentan en los informes. Si Renfe lo reactiva, la app lo detecta sola.

**Consecuencia:** hoy las incidencias que funcionan son las detectadas con retrasos y cancelaciones. La categoría Crítica depende del texto de los avisos, así que **no puede activarse mientras el feed siga congelado**.

### 2. El reloj de la cabecera no es fiable

En `vehicle_positions_LD` el `timestamp` de la cabecera GTFS va una hora por detrás y no hay timestamp por vehículo. Las posiciones sí están vivas: en 90 segundos se movieron 79 de 103 trenes, y coincidían al 100 % con el visor público de Renfe. Por eso `Last-Modified` manda, y se compara con la cabecera `Date` del mismo servidor para no depender del reloj del móvil.

### 3. Un mismo tren puede aparecer dos veces

Los `tripId` de larga distancia acaban en una fecha (`0581712026-10-31`) que puede ser la de ayer, y un tren llegó a figurar con dos fechas. Se identifica por sus 5 primeros dígitos.

### 4. Retrasos imposibles

Aparecen valores como `-25980` segundos. Se descarta todo retraso mayor de 6 horas en valor absoluto.

### 5. Avisos sin metadatos

No traen `cause`, `effect`, `severityLevel` ni `headerText`, y el `language` siempre es `es`, aunque el texto esté en catalán o euskera. Además hay avisos de prueba (`"Pruebas, disculpen las molestias"`), avisos repetidos con el mismo ID y líneas distintas, y textos con el formato `euskera // español`.

### 6. Sin compresión

El servidor no comprime, aunque se le pida gzip. Un ciclo en JSON cuesta unos 320 KB y en protobuf unos 110 KB. Por eso la app usa `.pb` y peticiones condicionales con `ETag`.

## Lo que no está disponible

| Dato | Situación |
|---|---|
| Adif (estado de la red, circulaciones, limitaciones de velocidad) | No se encontró API pública. `adif.es/viajeros/estado-de-la-red` devolvió 403 a una petición automática y **no se ha intentado saltar**. Según la prensa, Adif bloqueó un mapa ciudadano de limitaciones de velocidad alegando seguridad |
| Estado de vías, carriles y señalización | No es público. Es lo más relevante para la seguridad, y la app no puede saberlo |
| Trenes de mercancías | Sin dato público localizado |
| Iryo y Ouigo en tiempo real | Sin feed localizado. Ouigo publica horarios GTFS en el Punto de Acceso Nacional; Iryo no apareció |
| FGC, Euskotren, Metro y otros | No integrados. Euskotren publica horarios GTFS; falta investigar si hay tiempo real |

Por tanto, "todos los vehículos ferroviarios de España" **no es alcanzable hoy**. La app cubre los servicios de Renfe de sus feeds públicos.

## Fuente descartada

`tiempo-real.largorecorrido.renfe.com/renfe-visor/flotaLD.json` da más campos, como retraso y origen, y funciona. Pero es un archivo interno del visor, sin documentar y sin licencia expresa, y puede cambiar sin aviso. El feed oficial ofrece las mismas posiciones, así que no se usa.

## Condiciones de uso

- **Renfe:** la [licencia](https://data.renfe.com/legal) permite el uso comercial. Hay que citar "Origen de los datos: Renfe Operadora" con la fecha de actualización cuando exista, no alterar el sentido de los datos y no sugerir respaldo de Renfe. Reutilizar es responsabilidad de quien reutiliza.
- **OpenFreeMap:** sin clave ni registro, con atribución. Las [descargas offline](https://openfreemap.org/quick_start/) se limitan a 3.000 teselas o 50 MB por descarga, 4 peticiones paralelas y `User-Agent` propio.
