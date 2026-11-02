# Lenguaje claro

RadarTren tiene que poder usarlo cualquier persona: sin estudios, mayor, con la vista cansada o que nunca ha usado una app así. Si alguien necesita que le expliquen un texto, el texto está mal.

## Reglas para escribir textos

1. **Frases cortas.** Una idea por frase.
2. **Palabras de la vida diaria.** "Problema", no "incidencia". "Va con retraso", no "demora". "Funciona", no "en vivo". "Liberar espacio", no "borrar caché".
3. **Sin siglas ni tecnicismos.** Nada de "feed", "GTFS", "CSV", "PMR" o "AV/LD/MD" en pantalla. Si hace falta un nombre propio de Renfe (Cercanías, AVE), se deja tal cual, porque es como lo conoce la gente.
4. **Decir qué pasa y qué puede hacer la persona.** "No hay conexión con Renfe. Puede que veas los trenes donde estaban hace un rato" en vez de "Fuente inaccesible".
5. **Números como se dicen.** "2 h 1 min", no "+121 min". "5 de cada 12 trenes", no "42 %".
6. **Responder primero a la pregunta.** La ficha de un tren empieza por "Va 12 min tarde" o "Va a su hora", en grande y del color de su punto.
7. **No depender solo del color.** En el mapa, al acercarse, cada tren con retraso lleva escrito "+12'". La leyenda y la ayuda dicen qué significa cada color.
8. **Nunca asustar ni prometer.** Las incidencias detectadas dicen "Lo vemos en los trenes" y "No sabemos por qué pasa". La app nunca dice que algo es seguro o peligroso.
9. **Siempre una salida.** La ayuda tiene botones para llamar a Renfe (912 320 320, el teléfono que Renfe publica en su GTFS) y al 112. El botón solo abre el marcador: llamar sigue necesitando que la persona pulse.

## Gravedad

| Nivel interno | Español | Inglés |
|---|---|---|
| `CRITICAL` | Muy grave | Very serious |
| `HIGH` | Grave | Serious |
| `MEDIUM` | Moderado | Moderate |
| `LOW` | Leve | Minor |
| `INFO` | Información | Information |

## Avisos

En Ajustes, las opciones de aviso describen el resultado, no el nivel técnico: "Solo los muy graves", "Los graves y muy graves" (por defecto), "También los moderados" y "No quiero avisos".

## Comprobado

- Con la letra del móvil al 130 %, el mapa, la bienvenida y la ayuda caben sin cortes (emulador, 2 de noviembre de 2026).
- Los textos están en `values/strings.xml` (inglés) y `values-es/strings.xml` (español), y siguen estas reglas en los dos idiomas.

## Pendiente

- Probarlo con personas reales que no sean técnicas y anotar dónde dudan.
- Hacer accesible el mapa con TalkBack. Hoy un lector de pantalla lee la lista de problemas y los botones, pero no los puntos del mapa.
- Que la búsqueda entienda faltas de ortografía. Hoy ignora acentos y mayúsculas, pero no letras cambiadas.
