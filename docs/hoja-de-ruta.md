# Hoja de ruta

Cada apartado está pensado para convertirse en una issue cuando exista el repositorio.

## Antes de publicar

- [ ] Probar en un móvil físico durante varios días y resolver lo que quede en [pruebas](pruebas.md).
- [ ] Recalibrar los umbrales con días laborables y con incidencias reales.
- [ ] Contactar con Renfe Data por el feed de avisos congelado desde el 7 de octubre de 2026.
- [ ] Configurar la firma, activar R8 y publicar como App Bundle.
- [ ] Publicar la política de privacidad en una URL pública. La app enlaza a `docs/privacidad.md` en `AsKrueger/RadarTren`, así que el repositorio debe ser público o hay que moverla.
- [ ] Elegir una licencia para el repositorio.
- [ ] Consultar con un abogado la responsabilidad de ofrecer alertas relacionadas con seguridad.

## Barra publicitaria

Pedida para después del MVP, para cubrir el mantenimiento.

**Propuesta:** una barra arriba que solo ocupa espacio cuando hay anuncio. Si no hay ninguno, no deja hueco.

- Con AdMob: un `AdView` que se muestra en `onAdLoaded` y se oculta en `onAdFailedToLoad`.
- Con anuncios directos: un JSON remoto con imagen, enlace y fechas, más sencillo y sin datos de terceros.

**Antes de implementarla:**

- AdMob exige consentimiento (CMP) en la UE. Hay que actualizar la política de privacidad, que hoy dice que no hay publicidad, y la declaración de datos de Google Play.
- La barra no debe tapar los avisos de datos ni la atribución del mapa.
- Publicidad junto a incidencias graves tiene un riesgo de reputación. Conviene ocultarla si hay una incidencia Crítica activa.
- La licencia de Renfe permite el uso comercial.

## Servidor (el salto más importante)

Un proceso que lea los feeds de forma continua resolvería las dos grandes limitaciones:

- Alertas en segundos por push, no cada 15 minutos o más.
- Informes completos y de referencia, iguales para todos.
- Una sola lectura de los feeds, lo que además reduce la carga sobre Renfe.

Opciones: Cloudflare Workers con cron y D1, o Firebase Cloud Functions con FCM. Los límites gratuitos y el coste hay que estudiarlos antes de decidir.

## Mejoras de datos

- [ ] Detección por corredor en larga distancia, no como una sola red.
- [ ] Detectar trenes parados entre estaciones durante mucho tiempo, con cuidado por los falsos positivos. Requiere servidor.
- [ ] Dibujar las líneas ferroviarias con los `shapes.txt` del GTFS.
- [ ] Añadir clasificación en catalán y euskera.
- [ ] Investigar tiempo real de FGC, Euskotren, Metro, Iryo y Ouigo.
- [ ] Actualización remota del catálogo, sin publicar versión.

## Mejoras de la app

- [ ] Líneas y estaciones favoritas, con avisos solo de ellas.
- [ ] Horas de silencio.
- [ ] Filtrar notificaciones por núcleo.
- [ ] Widget de estado de tu línea.
- [ ] Gráficas de históricos dentro de la app.
- [ ] Regenerar informes en otro idioma.
- [ ] Generar informes de periodos atrasados.
