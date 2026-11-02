# Informes PDF

Cuando termina un periodo con datos recogidos, `CollectWorker` genera su PDF una sola vez, en el idioma elegido, y lo guarda en el móvil. Se puede abrir y compartir desde Informes.

| Tipo | Periodo (hora peninsular) | Archivo |
|---|---|---|
| Diario | De 00:00 a 24:00 | `radartren-dia-2026-10-31.pdf` |
| Semanal | De lunes a domingo | `radartren-semana-2026-W44.pdf` |
| Mensual | Mes natural | `radartren-mes-2026-10.pdf` |
| Anual | Año natural | `radartren-anio-2026.pdf` |

Solo se comprueba el último periodo cerrado de cada tipo. Si el móvil pasa días sin ejecutar la recogida, los informes intermedios no se generan.

El informe empieza con una frase en lenguaje sencillo, por ejemplo "En pocas palabras: 8 de cada 10 trenes iban a su hora y hubo 23 problemas". Si no hay datos de puntualidad, lo dice.

## Cifras

| Cifra | Cálculo |
|---|---|
| Cobertura | Muestras guardadas dividido entre las esperadas (una cada 15 minutos), con máximo del 100 % |
| Puntualidad | Parte de los trenes observados con menos de 5 minutos de retraso |
| Retraso medio | Suma de retrasos positivos dividida entre trenes observados, en minutos |
| Mayor retraso | El máximo visto en una muestra |
| Máximo de cancelaciones | El mayor número simultáneo en una muestra |
| Incidencias | Incidencias verificadas activas en algún momento del periodo |

## Cómo leerlas

- La cobertura aparece siempre, y por debajo del 80 % el informe advierte de que las cifras son parciales.
- Sin muestras, la puntualidad figura como "s/d", no como 100 %.
- Una muestra solo se guarda si todas las fuentes de posición y retraso están en vivo.
- La puntualidad se mide sobre trenes observados en circulación, cada 15 minutos. **No es comparable con la puntualidad oficial de Renfe**, que usa otra metodología.
- Los avisos de una fuente congelada quedan fuera de los totales, y el informe indica cuántos se excluyeron.
- Cada informe incluye metodología, aviso de independencia y atribución a Renfe.

La lógica está en `ReportData.kt`, y el dibujo en `PdfReportWriter.kt`, con el `PdfDocument` nativo de Android.

## Limitación

El informe describe lo que observó **un móvil**. Dos móviles pueden generar informes distintos. Los informes de referencia requieren un servidor ([hoja de ruta](hoja-de-ruta.md)).
