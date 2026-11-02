# Registro de cambios

## Sin publicar

- Lenguaje claro en toda la app, en español e inglés, pensado para personas sin experiencia (`docs/lenguaje-claro.md`).
- Los problemas se cuentan con frases completas: "El tren C2 84906 va con 2 h 1 min de retraso".
- La ficha del tren responde primero: "Va a su hora" o "Va 12 min tarde", dónde está ahora y de dónde a dónde va.
- Pantalla "Cómo usar RadarTren", con botones para llamar a Renfe y al 112, desde el mapa y desde Ajustes.
- La búsqueda encuentra estaciones (la actual o la siguiente, el origen y el destino) e ignora acentos.
- En el mapa, los trenes con retraso llevan escrito "+12'" al acercarse, para no depender solo del color.
- Los informes PDF empiezan con una frase sencilla: cuántos de cada 10 trenes iban a su hora y cuántos problemas hubo.
- Incidencias en tiempo real: anillos con pulso en el mapa que siguen al tren afectado, contador de activas y ficha al pulsarlas.
- La lista de incidencias indica la hora de la última actualización, desde cuándo dura cada una y lleva al mapa.
- Las incidencias nuevas salen como aviso dentro de la app mientras está abierta; las notificaciones del sistema se limitan a 3 más un resumen.
- Contador de incidencias en la pestaña.
- Corregido: los trenes de Cercanías de un núcleo desconocido se contaban como larga distancia.
- Base de datos versión 2, con migración automática.
- Rediseño del mapa: tema oscuro, red ferroviaria real de OpenStreetMap, alta velocidad destacada y estaciones.
- Chips por producto con contador, cifras en vivo, línea de estado con trenes sin posición y controles de zoom.
- El mapa guardado incluye los estilos claro y oscuro.
- Paleta azul propia en toda la app.
- Corregido: un fallo puntual de red dejaba el mapa sin retrasos.
- Corregido: Enviar comentarios podía cerrar la app sin app de correo (#2).
- El aviso de feed oficial congelado solo aparece en Incidencias.

## 0.1.0 - MVP (sin publicar)

- Mapa en vivo con los trenes de Cercanías, Rodalies, alta velocidad, larga y media distancia.
- Búsqueda, filtros por servicio y ficha de cada tren.
- Incidencias detectadas con retrasos y cancelaciones, y avisos oficiales clasificados por urgencia.
- Control de salud de las fuentes, con tratamiento aparte de los avisos no verificados.
- Notificaciones por urgencia y recogida en segundo plano.
- Informes PDF diarios, semanales, mensuales y anuales, con cobertura.
- Mapa de España para usar sin conexión.
- Español e inglés, tema claro y oscuro.
- Borrado de caché y de datos, exportación CSV y envío de comentarios.
