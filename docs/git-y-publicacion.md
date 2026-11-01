# Git y publicación

El repositorio, las ramas, los commits y los PR los hace el dueño del proyecto a mano. Claude da los comandos y no ejecuta ninguno de ellos. Los commits y PR no llevan atribución de IA.

Cada trabajo va en una rama creada desde `develop` (`feature/...` o `fix/...`) y se fusiona en `develop` por PR. `main` recibe versiones publicadas.

## Crear el repositorio

`RadarTren` está dentro de `XD`, que es otro repositorio, y sus archivos figuran preparados allí. Esto los saca del índice de `XD`, sin borrar nada del disco:

```powershell
cd C:\Users\lovei\Documents\XD
git rm -r --cached --quiet RadarTren
```

Después, crea el repositorio propio. La política de privacidad necesita que sea público:

```powershell
cd C:\Users\lovei\Documents\XD\RadarTren
git init -b main
git commit --allow-empty -m "Initial commit"
gh repo create AsKrueger/RadarTren --public --source . --remote origin --push
git switch -c develop
git push -u origin develop
git switch -c feature/mvp
git add .
git status
```

Revisa que `local.properties` no aparezca en la lista. Después:

```powershell
git commit -m "Add MVP: live map, incidents, alerts and PDF reports"
git push -u origin feature/mvp
gh pr create --base develop --title "MVP de RadarTren" --body "Mapa en vivo, incidencias, alertas, informes PDF, modo sin conexión y ajustes. Documentado en docs/."
```

## Trabajo diario

```powershell
git switch develop
git pull
git switch -c feature/nombre
# cambios
git add .
git commit -m "Describe el cambio"
git push -u origin feature/nombre
gh pr create --base develop --fill
```

## Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Actualiza `CHANGELOG.md`.
3. Abre un PR de `develop` a `main` y, al fusionarlo, crea la etiqueta:

```powershell
git switch main
git pull
git tag v0.1.0
git push origin v0.1.0
```
