# RadarTren

App Android que muestra en un mapa en vivo los trenes de Renfe en España, detecta incidencias, avisa según su urgencia y genera informes en PDF cada día, semana, mes y año.

*Android app that shows Renfe trains in Spain on a live map, detects incidents, alerts by urgency and produces daily, weekly, monthly and yearly PDF reports. [English summary below](#english-summary).*

> **RadarTren no es un sistema de seguridad.** Es una app independiente que informa con datos públicos. No tiene relación con Renfe ni con Adif, y puede estar incompleta o llegar tarde. En una emergencia, llama al 112.

## Qué hace

| Función | Detalle |
|---|---|
| Mapa en vivo | Cercanías, Rodalies, alta velocidad, larga y media distancia, sobre la red ferroviaria real. Tema oscuro y claro, color según retraso, filtros por producto con contador, cifras en vivo, búsqueda y ficha de cada tren |
| Incidencias en tiempo real | Detectadas con retrasos y cancelaciones en vivo, más los avisos oficiales de Renfe clasificados por urgencia. Se marcan en el mapa con un anillo que sigue al tren, se actualizan en cada refresco y se abren desde la lista |
| Alertas | Notificaciones por urgencia, con umbral configurable |
| Informes PDF | Día, semana, mes y año, con cobertura real, compartibles |
| Salud de las fuentes | Detecta si una fuente deja de actualizarse y nunca la presenta como actual |
| Sin conexión | Botón para guardar el mapa de España; se borra al desactivarlo |
| Tus datos | Borrar caché, borrar datos guardados, exportar incidencias a CSV |
| Idiomas y tema | Español e inglés seleccionables; tema claro, oscuro o del sistema |
| Comentarios | Envío directo al desarrollador por correo desde la app |
| Para todo el mundo | Lenguaje claro, ayuda con botones para llamar a Renfe y al 112, letra grande y retraso escrito además de por colores |

## Puesta en marcha

Requisitos: Android Studio con SDK 37 y JDK 17 o superior con `jlink` (vale el de Android Studio).

```properties
# local.properties (nunca se sube al repositorio)
radartren.feedbackEmail=tu-correo@ejemplo.com
```

Sin esa línea, la app funciona igual y oculta la opción de enviar comentarios. La dirección queda incluida en la APK, así que quien la analice podrá verla.

```powershell
.\gradlew :app:assembleDebug        # compilar
.\gradlew :app:testDebugUnitTest    # tests de lógica
.\gradlew :app:connectedDebugAndroidTest  # tests en dispositivo, necesitan conexión
```

Si Gradle falla con `jlink executable ... does not exist`, está usando el JRE de la extensión Java de VS Code. Ver [docs/pruebas.md](docs/pruebas.md).

## Documentación

| Documento | Contenido |
|---|---|
| [Fuentes de datos](docs/fuentes-de-datos.md) | Qué datos existen, qué falta, licencias y problemas reales encontrados |
| [Arquitectura](docs/arquitectura.md) | Estructura del código, flujo de datos y decisiones |
| [Clasificación de urgencia](docs/clasificacion-de-urgencia.md) | Reglas y umbrales de las incidencias |
| [Informes](docs/informes.md) | Cómo se calculan y qué significa cada cifra |
| [Pruebas](docs/pruebas.md) | Qué se prueba, cómo, y qué queda sin cubrir |
| [Lenguaje claro](docs/lenguaje-claro.md) | Cómo se escriben los textos para que cualquiera los entienda |
| [Privacidad](docs/privacidad.md) | Política de privacidad de la app |
| [Hoja de ruta](docs/hoja-de-ruta.md) | Pendiente tras el MVP, incluida la barra publicitaria |
| [Git y publicación](docs/git-y-publicacion.md) | Comandos para crear el repositorio y trabajar con ramas |
| [Registro de cambios](CHANGELOG.md) | Historial de versiones |

## Atribución

Origen de los datos: Renfe Operadora (CC BY 4.0). Mapa © OpenFreeMap, © OpenMapTiles. Red ferroviaria y datos de mapa © colaboradores de OpenStreetMap (ODbL).

## English summary

RadarTren reads Renfe's official open GTFS Realtime feeds to draw about 400 running trains on a live map. Renfe's alerts have no severity field, so urgency is computed by the app. Incidents are also detected from live delays and cancellations.

The app checks each feed's freshness from HTTP headers, because a feed can answer `200 OK` while frozen. Notices from a frozen feed are shown as unverified, never notified and never counted. Everything runs on the device, with no backend and no accounts. Reports state the share of the period actually observed.

It is not a safety system. The full documentation is in Spanish, in [docs/](docs/).
