# Compañero de Clase: base iOS

Esta es una base inicial con interfaz SwiftUI V8 y lógica/contratos Kotlin Multiplatform. No demuestra paridad Android, preparación para producción ni disponibilidad de TestFlight/App Store.

## Qué funciona

- Navegación Hoy, Agenda y Conexión.
- Configuración de una URL base de API y comprobación real de `GET /health` y `GET /ready`.
- Login real `POST /auth/login`, nombre de usuario y logout `POST /auth/logout`.
- Sesión solo en memoria: no guarda passwords ni tokens en preferencias; cambiar endpoint elimina la sesión y cancela solicitudes anteriores.
- Errores localizados sin mostrar cuerpos técnicos; HTTP 401, 403, 429 y 503 diferenciados; errores de JSON/transporte y cancelación.
- Tema oscuro/carmesí derivado de los tokens V8 Android.
- Tests KMP JVM/Native y XCTest para callbacks obsoletos. CI de simulador sin credenciales Apple.

Hoy y Agenda indican qué sigue pendiente. No contienen clases, asistencia o avisos simulados.

## Estructura y aislamiento

`multiplatform/` es un build Gradle independiente. Usa el wrapper raíz con `-p multiplatform`. No aplica AGP ni modifica el grafo de Android/backend. `multiplatform/shared` produce el framework estático `CompaneroShared` para Apple y un target JVM de pruebas. Sus contratos seleccionados son los mismos archivos de `shared/contracts`, compilados desde una lista explícita.

`iosApp/iosApp.xcodeproj` tiene el esquema compartido `iosApp`. La fase de build llama `embedAndSignAppleFrameworkForXcode` antes de compilar Swift. La aplicación admite iOS 16.0 o superior.

## Ejecutar en macOS

Requisitos: Xcode 26.0.x, JDK 17, acceso a Maven/Gradle/Kotlin Native. Kotlin se mantiene en 2.2.21 para no actualizar el repositorio Android. No se usa CocoaPods. Las imágenes fijadas deben estar disponibles; no sustituir silenciosamente Xcode por latest.

1. Abrir `iosApp/iosApp.xcodeproj`, elegir esquema `iosApp` y un simulador.
2. Ejecutar la app; abrir Conexión e introducir la URL de la API.
3. Guardar y comprobar API. Iniciar sesión desde Hoy cuando el backend tenga autenticación configurada.

La URL inicial está vacía. Puede preconfigurarse con el setting `COMPANERO_API_BASE_URL` desde Xcode o `xcodebuild`. Si se añade una URL en un `.xcconfig`, escapar las barras según la sintaxis de Xcode (un `//` sin escape empieza un comentario); se recomienda usar la pantalla o el argumento de build.

HTTPS funciona en Debug/Release. Debug permite HTTP únicamente a loopback (`localhost`, `127.0.0.1`, `::1`), con ATS local acotado; Release rechaza HTTP. No hay `NSAllowsArbitraryLoads`. Para backend en el Mac usar `http://localhost:8080/`; `10.0.2.2` es específico del emulador Android y no corresponde al simulador Apple. Un dispositivo físico necesita HTTPS accesible desde su red.

Validación completa de simulador:

```sh
bash infrastructure/scripts/build-ios-simulator.sh
```

El script ejecuta las pruebas JVM y Native del host, exige reportes con pruebas reales, valida plists y esquema, compila Debug sin firma, ejecuta XCTest, instala y abre la app, y genera screenshot/ZIP/procedencia en `build/ios/`. El ZIP contiene una `.app` para la arquitectura del simulador del runner; no es un IPA ni se instala en un iPhone físico.

Pruebas KMP en Windows/Linux:

```sh
./gradlew -p multiplatform :shared:jvmTest
```

En Windows usar `gradlew.bat`. Native/Xcode solo se verifican en macOS.

## Codemagic

`codemagic.yaml` en la raíz define `ios-simulator`, máquina `mac_mini_m2`, Xcode 26.0 y Java 17. Usa exactamente el mismo script que `.github/workflows/ci-ios.yml`; no instala identidades de firma y no publica en tiendas.

Para activar:

1. Abrir https://codemagic.io/app/6ac8735db82b0d75c91e6626/settings (el usuario confirmó que el repo ya está conectado).
2. Seleccionar la rama `codex/ios-kmp-foundation` y pulsar **Check for configuration file** para detectar el YAML raíz. El mensaje No configuration file found corresponde a una rama que aún no tiene ese archivo.
3. Pulsar **Start new build**, escoger esa rama y workflow **ios-simulator**, y lanzar la primera compilación. Para disparos automáticos, comprobar/activar el webhook de push/PR.
4. Verificar el SHA, logs, resultados Native/XCTest, screenshot, arranque y ZIP.
5. Adjuntar URL del build exitoso al PR. Un YAML validado no sustituye una ejecución.

No se requieren Apple ID, Team ID, App Store Connect API key, certificado ni provisioning para este workflow. No pegar tokens de Codemagic en el repo. Si se usan sus APIs, mantener el acceso fuera del repositorio.

Validación local del YAML:

```sh
python -m pip install PyYAML jsonschema
curl -fsS https://codemagic.io/codemagic-schema.json -o /tmp/codemagic-schema.json
python infrastructure/scripts/validate-ios-foundation.py --schema /tmp/codemagic-schema.json
```

El esquema oficial verifica estructura, pero no disponibilidad de imágenes, permisos de cuenta ni compilación. El validador también comprueba referencias PBX, fuentes, esquema y ATS; no reemplaza Xcode.

## Condiciones antes de merge

PR draft hasta aprobación de revisión, CI Android/API y build macOS iOS del SHA del PR. Para afirmar que Codemagic está verificado se requiere además su build exitoso del mismo SHA. No hacer merge con validaciones pendientes. Consultar [reutilización y pendientes](REUSE_AND_GAPS.md) y [evidencia](VERIFICATION.md).
