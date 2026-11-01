# RadarTren

App Android (Kotlin, Compose, Room, WorkManager, MapLibre) con datos abiertos de Renfe. Lee `README.md` y `docs/`.

- Todo cambio se documenta en `docs/` y `CHANGELOG.md`.
- Textos de interfaz siempre en `values/strings.xml` (inglés) y `values-es/strings.xml` (español).
- Nunca presentar un dato antiguo como actual: revisa `FeedHealth` antes de tocar alertas o informes.
- Las incidencias detectadas describen datos y nunca afirman una causa.
- Solo fuentes oficiales documentadas. No saltarse bloqueos de acceso.
- Git: ver `docs/git-y-publicacion.md`. Claude no ejecuta commits, ramas ni PR, y no firma con IA.
- Para compilar en esta máquina, usa el JDK de Android Studio (`docs/pruebas.md`).
