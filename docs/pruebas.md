# Pruebas

## Automáticas

| Suite | Ubicación | Comprueba |
|---|---|---|
| `FeedParsingTest` | `app/src/test` | Decodifica capturas reales de los 5 feeds del 1 de noviembre de 2026, cruza datos y clasifica avisos reales |
| `AlertClassifierTest` | `app/src/test` | Reglas de urgencia |
| `IncidentDetectorTest` | `app/src/test` | Umbrales de detección |
| `NetworkStatsTest` | `app/src/test` | Familias de producto y cifras del mapa |
| `ReportBuilderTest` | `app/src/test` | Periodos, cifras, cobertura y cambio de hora |
| `DeviceTest` | `app/src/androidTest` | Feed congelado sin notificar, ciclo de vida, muestras, PDF, y worker completo contra los feeds reales |

```powershell
.\gradlew :app:testDebugUnitTest
.\gradlew :app:connectedDebugAndroidTest
```

El test `workerCollectsAndWritesTheReportOfTheDayThatClosed` usa los feeds reales: necesita conexión y falla si Renfe no responde. Además, borra los datos guardados de la app en ese dispositivo.

Las capturas están en `app/src/test/resources/feeds`. Al actualizarlas, revisa las cifras concretas de `FeedParsingTest`.

## Probado a mano en el emulador

En un Pixel 10 Pro XL con Android 37, el 1 de noviembre de 2026:

- Mapa con unos 400 trenes reales y atribución visible.
- Banner de avisos oficiales congelados y estado de cada fuente en Ajustes.
- Incidencias detectadas reales.
- Cambio a español mantenido tras reiniciar la app.
- Descarga del mapa (unos 30 MB con los dos estilos). El dibujo en modo avión se comprobó con el estilo claro.
- Tema oscuro y claro, cambiados con la app abierta.
- Chips por producto con contador y cifras del mapa filtradas.
- Ficha de un tren al pulsarlo.
- PDF revisado visualmente.

## Sin probar

- En un móvil físico.
- Recepción visible de una notificación real. El permiso y el filtrado sí están cubiertos, pero no se vio una llegar.
- Búsqueda de trenes y botones de zoom.
- Exportación CSV, botones de borrado y envío de comentarios, incluido el aviso si no hay app de correo.
- Comportamiento del worker a lo largo de días, con ahorro de batería de cada fabricante.
- Consumo real de datos y batería.
- Android 8 a 12, versión mínima incluida.
- Build de release, que aún no tiene firma.

## Problema conocido del entorno

Si aparece `jlink executable ... does not exist`, Gradle usa el JRE de la extensión Java de VS Code:

```powershell
.\gradlew --stop
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew "-Dorg.gradle.java.home=C:/Program Files/Android/Android Studio/jbr" :app:testDebugUnitTest
```
