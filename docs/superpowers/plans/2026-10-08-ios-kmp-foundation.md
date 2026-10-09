# Base iOS KMP + SwiftUI V8: plan de implementación

> Para ejecución: usar `superpowers:executing-plans` en esta sesión, con pruebas por tarea y una revisión independiente de la rama al final. La especificación ya fue aprobada; este plan fue aprobado por el usuario.

**Objetivo:** aplicación inicial iOS que compile para simulador en Codemagic, sin alterar producción Android/backend.

**Arquitectura:** build KMP independiente en `multiplatform/`, contratos existentes compilados como fuentes comunes mediante lista explícita, Ktor Darwin y UI SwiftUI V8. Framework estático `CompaneroShared` integrado directamente en Xcode.

**Stack:** Kotlin 2.2.21, Gradle 8.14.3, Ktor 3.3.3, coroutines 1.10.2, serialización 1.9.0, SwiftUI iOS 16+, Xcode 26.0, Codemagic M2.

**Spec:** `docs/superpowers/specs/2026-10-08-ios-foundation-design.md`.

## Restricciones globales

- Rama `codex/ios-kmp-foundation`, worktree aislado desde `origin/main` actualizado.
- No modificar módulos de producción Android/backend, build raíz ni versiones existentes.
- Sin secretos, firma Apple, provisioning, publicación ni merge.
- Compartir fuentes de `AuthContracts`, `ApiResponse`, `ApiError`, `UserSummary` y `UserRole`; no copiar ni convertir módulos JVM completos.
- URL HTTPS; HTTP local solo Debug. Sesión solo en memoria, sin prometer Keychain ni refresh persistente.
- Funciones académicas completas pendientes y visibles como tales; no mostrar datos inventados.
- Verificación cloud pendiente hasta existir un build Codemagic exitoso del SHA del PR.

## Foco de revisión

- Endpoint con prefijo de ruta: las peticiones conservan el prefijo.
- Backend sin autenticación o sin base de datos: 503 se distingue de contraseña incorrecta y de conectividad.
- Cambio de endpoint durante una petición: cancela la petición anterior y elimina la sesión previa.
- Respuesta malformada o rol no esperado: error controlado y autorización siempre en servidor.
- Compilación simulador: framework y app tienen arquitectura compatible, sin depender de credenciales Apple.

## Tarea 1: build KMP y contratos comunes

**Archivos:** crear `multiplatform/settings.gradle.kts`, `multiplatform/build.gradle.kts`, `multiplatform/shared/build.gradle.kts`, pruebas en `multiplatform/shared/src/commonTest/kotlin/` y `src/jvmTest/kotlin/`. Copiar especificación y este plan al worktree; registrar ledger de ejecución.

**Interfaces:** tipos wire originales del paquete `org.companerodeescuela.shared.contracts`; framework `CompaneroShared`; targets JVM y Apple. Se consumen las fuentes originales mediante lista explícita.

- [ ] Registrar SHA base y ejecutar baseline de contratos, modelos y validación; registrar resultados Android/API apropiados para la rama base.
- [ ] Configurar build independiente, plugins y fuentes. Ejecutar prueba contractual que pinne login JSON, nombres `SerialName` y roles; observar fallo antes de enlazar las fuentes comunes.
- [ ] Incorporar fuentes compatibles y verificar pruebas JVM: `gradlew -p multiplatform :shared:jvmTest`.
- [ ] Verificar que el build raíz y archivos Android/backend no se modifican. Commit del build y contratos.

## Tarea 2: cliente real, configuración y puente

**Archivos:** crear `ApiConfiguration.kt`, `OperationalContracts.kt`, `MobileApiClient.kt`, `ApiResult.kt`, `IosApiBridge.kt` bajo `multiplatform/shared/src/commonMain/kotlin/org/companerodeescuela/mobile/`; motor en `iosMain` y pruebas con MockEngine en `jvmTest`.

**Interfaces:** `ApiConfiguration(baseUrl: String, allowLocalHttp: Boolean)`; `MobileApiClient` recibe configuración y motor, expone `health`, `readiness`, `login`, `logout`, `close`; `IosApiBridge` ofrece callbacks simples, cancelación y cierre. Resultados distinguen éxito, HTTP, transporte y formato.

- [ ] Escribir y observar fallos de pruebas de URL: HTTPS válido y prefijo preservado; rechazo de host ausente, userinfo/query/fragment y HTTP remoto; HTTP local limitado a Debug.
- [ ] Implementar configuración y DTO operativos con timestamp ISO string.
- [ ] Escribir y observar fallos de pruebas MockEngine para health/readiness, envoltura login, 401/403/429/503, JSON malformado, transporte y cancelación.
- [ ] Implementar cliente común, Darwin en Apple y puente cancelable; mantener `CancellationException` y evitar logging de secretos.
- [ ] Ejecutar pruebas JVM y registrar cantidad de pruebas reales. Commit de cliente y puente.

## Tarea 3: aplicación iOS V8

**Archivos:** crear `iosApp/iosApp.xcodeproj/project.pbxproj`, esquema compartido `iosApp.xcscheme`, `iosApp/Info.plist`, configuraciones Debug/Release y archivos Swift separados para app, modelo de sesión, theme y pantallas.

**Interfaces:** SwiftUI consume `CompaneroShared.IosApiBridge`; modelo de sesión propietario del cliente y peticiones. `NavigationStack` y `TabView` enlazan login, Hoy, Agenda y Conexión.

- [ ] Crear comprobaciones de sesión: cambiar endpoint invalida resultados anteriores; logout limpia tokens; los callbacks actualizan el main actor. Añadir tests iOS donde el runner macOS pueda ejecutarlos.
- [ ] Implementar proyecto compartido y fase KMP previa a Compile Sources, framework estático y esquema versionado, bundle `org.companerodeescuela.ios`, display name `Compañero de Clase`, iOS 16.0.
- [ ] Implementar pantallas y tokens V8 definidos en spec, accesibilidad y estados de carga/error. Login real, sesión volátil y módulos académicos pendientes.
- [ ] Permitir HTTP local únicamente en configuración Debug mediante ATS acotado; Release conserva HTTPS.
- [ ] Validar estructura Xcode localmente y compilar/ejecutar pruebas y smoke en macOS. Registrar la limitación de Windows sin fingir éxito. Commit iOS.

## Tarea 4: Codemagic y documentación operativa

**Archivos:** crear `codemagic.yaml`, scripts de verificación necesarios en `infrastructure/scripts/`, `docs/ios/README.md`, `docs/ios/REUSE_AND_GAPS.md`; ampliar `.gitignore` solo para artefactos locales iOS/KMP.

**Interfaces:** workflow `ios-simulator` produce ZIP `.app`, resultados de tests y procedencia del commit; usa wrapper con `-p multiplatform` y esquema de Tarea 3.

- [ ] Configurar `mac_mini_m2`, Xcode 26.0, Java 17, tests JVM y Native, build simulador con ambas opciones de firma desactivadas.
- [ ] Comprobar existencia del `.app`, arquitectura y empaquetar ZIP; conservar logs y SHA. Sin archive App Store ni IPA.
- [ ] Validar YAML y esquema oficial disponible; comprobar triggers, paths y comandos reales.
- [ ] Documentar acceso/importación/webhook Codemagic, configuración API, ejecución local macOS y matriz de reutilización/pendientes. Commit CI y documentación.

## Tarea 5: regresiones, revisión y PR

**Archivos:** actualizar documentación de evidencia y ledger; sin cambios de producción Android/API.

**Interfaces:** PR draft contra `main`, checks GitHub Android/API y workflow Codemagic sobre el mismo SHA.

- [ ] Ejecutar pruebas KMP JVM; inspeccionar reportes y cantidades. En macOS ejecutar Native y Xcode.
- [ ] Ejecutar `:services:api:test :shared:contracts:test :shared:models:test :shared:validation:test`; compile Debug Android, todas las suites `testDebugUnitTest`, lint, assembleDebug y assembleRelease. Instrumentation en CI existente.
- [ ] Revisar whitespace, secretos, diff y alcance. Una revisión independiente completa identifica errores críticos/importantes antes del PR; corregir y verificar los cambios pertinentes.
- [ ] Commit final, push de rama y crear PR draft con cuerpo desde archivo; adjuntar PR al chat.
- [ ] Inspeccionar checks y estado Codemagic accesible. No merge. Si falta acceso Codemagic, declarar pendiente la validación cloud y dejar instrucciones concretas.

## Revisión del plan

Confirmar que el plan refleja la especificación aprobada y seleccionar ejecución en esta sesión. Se recomienda ejecución en esta sesión porque los contratos, puente y UI dependen secuencialmente; una revisión independiente al final verifica la integración completa. La habilidad `superpowers:writing-plans` requiere revisión del plan antes de implementación.
