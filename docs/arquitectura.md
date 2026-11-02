# Arquitectura

Un solo módulo Android con Kotlin, Compose y Material 3, sin backend ni cuentas. Sigue el estilo de los demás proyectos: inyección manual (`AppContainer`) y dependencias en `gradle/libs.versions.toml`.

## Flujo de datos

```mermaid
flowchart LR
    R[Feeds de Renfe] --> C[RenfeClient]
    C --> L[LiveRepository]
    L --> S[SnapshotAssembler]
    S --> M[Mapa]
    S --> D[IncidentDetector]
    D --> I[IncidentRepository]
    I --> DB[(Room)]
    I --> N[Notifier]
    DB --> U[Incidencias]
    DB --> G[ReportGenerator]
    G --> P[PDF]
```

`AppContainer.collect()` es el ciclo completo y lo usan dos actores:

- **Con la app visible:** `AppViewModel.pollWhileVisible()`, cada 15, 30 o 60 segundos.
- **En segundo plano:** `CollectWorker`, cada 15 minutos, que además genera los informes pendientes.

## Código

| Paquete | Responsabilidad |
|---|---|
| `data/remote` | Esquema GTFS Realtime y descarga |
| `data/local` | Room: incidencias, muestras e informes. El esquema se exporta a `app/schemas` |
| `data` | Repositorios, ajustes, mapa offline y almacenamiento |
| `domain` | Lógica pura sin Android: clasificación, detección, salud y agregación. Es lo más probado |
| `report` | Dibujo y generación de los PDF |
| `work` | Recogida periódica y notificaciones |
| `ui` | Pantallas Compose: mapa, incidencias, informes y ajustes |

## Decisiones

**Sin backend en el MVP.** No genera costes, no pide cuentas y no recoge datos de nadie. A cambio, cada móvil solo sabe lo que observa, lo que tiene dos consecuencias importantes:

- Android limita las tareas periódicas a un mínimo de 15 minutos y puede espaciarlas, así que **una alerta en segundo plano puede llegar con más de 15 minutos de retraso**.
- Los informes tienen huecos si el móvil estuvo apagado o sin red. Por eso indican su cobertura.

Un servidor con recogida continua y notificaciones push resolvería ambas cosas, y es el principal salto pendiente ([hoja de ruta](hoja-de-ruta.md)).

**Protobuf con `kotlinx-serialization`.** Reduce el consumo a un tercio frente a JSON sin plugin de generación de código. Los enums se guardan como `Int` para que un valor nuevo no rompa la decodificación.

**La urgencia máxima se conserva.** Una incidencia guarda la peor severidad alcanzada, y se vuelve a notificar solo si empeora.

**Cada fuente solo cierra lo que pudo ver.** Una descarga fallida no significa que la incidencia terminó, así que no resuelve nada (`IncidentRepository.record`).

**MapLibre con OpenFreeMap.** Es abierto, gratuito y sin clave. El estilo sigue al tema: `dark` en oscuro y `liberty` en claro. Encima se dibujan, por este orden, la red ferroviaria, las estaciones (desde zoom 7), los trenes y sus etiquetas.

**Avisos en su sitio.** El feed de avisos congelado solo se señala en Incidencias. Si fallan posiciones o retrasos, el banner sale en todas las pestañas porque afecta al mapa.

**Fallos de red.** Cada descarga se reintenta una vez. Si vuelve a fallar se conserva el último dato bueno, marcado como no disponible, para que un fallo puntual no deje el mapa sin retrasos.

**Mapa offline hasta zoom 8.** Zoom 9 serían 1.404 teselas pero midió 58 MB, por encima del límite de 50 MB de OpenFreeMap. Zoom 8 son 384 teselas y unos 30 MB, con los dos estilos, que comparten teselas. Ampliarlo requiere pedirles permiso o alojar las teselas.

**Paleta fija.** Sin color dinámico, para que la urgencia se vea igual en todos los móviles.

**Inglés por defecto en `values/`, español en `values-es/`.** Los avisos de Renfe no se traducen: enviar el texto a un tercero complicaría la privacidad, así que en inglés se marcan como "in Spanish".

**Idioma.** Se aplica solo cuando se elige en Ajustes, para respetar también el idioma por app de Android. Workers y notificaciones usan `localized()` porque corren fuera de una actividad.

## Incidencias en tiempo real

Con la app abierta, cada refresco (15, 30 o 60 segundos) ejecuta el ciclo completo: lee los feeds, recalcula las incidencias y actualiza Room, de modo que la lista y el mapa cambian solos. "En tiempo real" significa al ritmo de los feeds de Renfe, que se actualizan cada 15 a 30 segundos. No es instantáneo.

- **Ubicación.** Cada incidencia guarda dónde dibujarse: la posición del tren en las de tren, que se mueve con él en cada refresco; la estación citada en los avisos oficiales; o la ciudad principal del núcleo en las de red (`Core.latitude`). Las de larga distancia en conjunto no tienen un punto y solo aparecen en la lista.
- **Mapa.** Las incidencias activas y verificadas de urgencia Media o superior se dibujan como anillos con un pulso suave, por debajo de los trenes, para que pulsar un tren abra su ficha. Si se pulsa el anillo, se abre la ficha de la incidencia.
- **Avisos.** Con la app en pantalla, las incidencias nuevas o que empeoran, de urgencia Media o superior, salen como aviso dentro de la app, con un botón para verlas en el mapa, y no como notificación del sistema. Con la app cerrada se notifican según el umbral de Ajustes, con un máximo de 3 notificaciones más un resumen, para que un primer arranque no inunde el móvil.
- **Contadores.** La pestaña Incidencias muestra cuántas activas hay de urgencia Media o superior, en rojo si alguna es Alta o Crítica.
- **Base de datos.** La versión 2 añade `latitude` y `longitude` a `incident` mediante una migración automática de Room. Las incidencias guardadas por la versión 0.1.0 se conservan, sin ubicación.

Las incidencias que ya existen al abrir la app no generan aviso: se ven en la lista y el mapa. El aviso es para lo que aparece mientras se mira.

## Retención y consumo

- Incidencias y muestras se conservan 2 años, y después se borran.
- Los PDF se conservan hasta que el usuario los borra.
- Una muestra ocupa unas decenas de bytes; un año son unas 35.000 filas.
- Con la app abierta a 30 segundos, el consumo teórico máximo es de unos 13 MB por hora. Es menor en la práctica, porque un feed sin cambios se resuelve con `304`. No se ha medido en un móvil real.
- En segundo plano son unos 10 MB al día como máximo.

## Tamaño de la APK

La APK de depuración pesa 71 MB porque incluye MapLibre para todas las arquitecturas. Publicar como Android App Bundle reparte solo la necesaria. Los idiomas no se dividen en el bundle, porque el selector de Ajustes necesita los dos instalados.
