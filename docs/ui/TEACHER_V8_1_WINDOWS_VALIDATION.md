# Docente V8.1 — APK e instrucciones Windows

Artefactos de pruebas. El backend HTTPS respondió con Mongo no accesible; aceptación final pendiente de resolverlo y validar teléfono, selector de archivos y red escolar.

Fuente: `6e25c7d50c828738ebae3e6478fc2b09205620c1`. Commits posteriores agregan evidencia/QA sin cambiar app. Versión existente `0.1.0`, code `1`. SHA-256 en `docs/audits/v8-evidence/teacher-v8-1/BUILD_PROVENANCE.json`.

| Archivo en `build/outputs/teacher-v8-1` | Uso |
|---|---|
| `teacher-v8.1-validation-debug.apk` | Teléfono de pruebas, endpoint HTTPS, firma debug estándar. |
| `teacher-v8.1-emulator-local-debug.apk` | Emulador contra `http://10.0.2.2:8080/`, no teléfono físico. |
| `teacher-v8.1-release-unsigned.apk` | R8 + HTTPS; falta firma de distribución. |

PowerShell, descargar la rama (o main después de fusionar):

```powershell
Set-Location C:\proyectos\esc
git fetch origin
git switch codex/teacher-v8-1-integration-20261008
git pull --ff-only
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:COMPANERO_API_BASE_URL = 'https://compa-ero-de-esc.onrender.com/'
$env:COMPANERO_RELEASE_API_BASE_URL = 'https://compa-ero-de-esc.onrender.com/'
.\gradlew.bat :apps:android:app:assembleDebug :apps:android:app:lintDebug --no-daemon --no-configuration-cache --max-workers=2
Get-FileHash apps\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

Requiere JDK 21 y Android SDK con los paquetes indicados por Gradle. Una reconstrucción puede producir otro hash: registrar commit, URL, tipo y SHA-256. No copiar secretos de producción.

Instalar el APK entregado en un teléfono autorizado para depuración USB:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r build\outputs\teacher-v8-1\teacher-v8.1-validation-debug.apk
```

Una instalación anterior con otra firma rechazará la actualización. Conservar datos; no desinstalar automáticamente.

Verificaciones:

```powershell
.\gradlew.bat testDebugUnitTest :shared:contracts:test :shared:validation:test :services:api:test :apps:android:app:lintDebug :apps:android:app:assembleRelease --no-daemon --no-configuration-cache --max-workers=2
.\gradlew.bat :apps:android:app:connectedDebugAndroidTest --no-daemon --no-configuration-cache --max-workers=2
```

Login instrumentado requiere cerrar sesión mediante Mi perfil. Pendiente físico: autenticación institucional, clase/grupo en destinos, selector XLSX/CSV, limpieza al cambiar clase, respuesta honesta de sincronización, revisión conservando evidencia, cámara/QR efímero y entrada escolar en red real.
