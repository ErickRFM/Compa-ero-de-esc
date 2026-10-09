# Base iOS de Compañero de Clase: auditoría y diseño propuesto

Fecha: 2026-10-08. Estado: especificación aprobada por el usuario; ver docs/ios para implementación y evidencia. No es una declaración de preparación para producción.

## Objetivo y alcance

Crear una aplicación iOS inicial con identidad V8, navegación, conexión configurable a la API Ktor y compilación de simulador sin firma en Codemagic. Conservar la aplicación Android y el backend. Entregar mediante rama y PR sin merge automático. No incorporar credenciales Apple, certificados, perfiles ni secretos.

La base debe permitir ejecutar la aplicación, configurar la API, distinguir conexión, disponibilidad y errores, e iniciar una sesión real cuando el backend tenga autenticación configurada. Las funciones académicas completas quedan para fases posteriores y deben mostrarse como pendientes, sin datos inventados.

## Evidencia del repositorio

Repositorio: `ErickRFM/Compa-ero-de-esc`. Directorio auditado: `C:/proyectos/esc`.

Al comenzar, la rama `feat/attendance-offline-teacher-qr-fallback-20261008` tenía 17 archivos preparados y cinco commits por delante del remoto. Durante la auditoría apareció el commit `83aecf2` y el árbol quedó limpio: hay actividad concurrente. No cambiar la rama ni incluir esos cambios en el PR de iOS. La base propuesta para el nuevo trabajo es `origin/main`, cuyo SHA local observado fue `97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca`; actualizar el remoto y registrar el SHA definitivo antes de implementar.

No se encontró un `AGENTS.md` versionado. No hay worktrees adjuntos a este chat. Windows no proporciona Xcode: la compilación y ejecución de iOS se deben verificar en macOS.

| Área | Estado observado | Consecuencia para iOS |
| --- | --- | --- |
| Build raíz | Kotlin 2.2.21, Gradle 8.14.3, AGP 8.13.2, JDK de módulos JVM 17 | Conservar estos archivos y versiones en la primera fase |
| `shared/contracts` | Plugin Kotlin JVM, 28 archivos de producción, serialización Kotlin | Un JAR JVM no puede enlazarse como framework iOS |
| Contratos de autenticación | `LoginRequest`, `LoginResponse`, `ApiResponse`, `UserSummary`, `UserRole`, `ApiError` usan Kotlin y serialización | Reutilizables como fuentes en `commonMain` |
| Contratos operativos | Health y readiness usan `java.time.Instant` y serializador JVM | Adaptar timestamp ISO a un DTO móvil compatible; probar el JSON existente |
| `TeacherClassContext` | Implementa `java.io.Serializable` | Excluir de fuentes compartidas iniciales |
| `ScheduleConflictRules` | Usa `java.time.LocalTime` | Adaptación futura con pruebas de equivalencia; no portar silenciosamente |
| `shared/models` | `DayOfWeek`, `LocalDate`, `LocalTime`, `Instant` de Java | Conservar JVM; diseñar fechas multiplataforma antes de migrarlo |
| `shared/validation` | Usa `java.net.URI` dentro de `absoluteHttpUrl`, aunque no haya import Java | No asumir que ausencia de imports significa portabilidad |
| `core/network` Android | Ktor 3.3.3, motor Android, JSON, timeouts; captura `java.io.IOException` | Nueva factoría Darwin; preservar cancelación de coroutines y adaptar errores |
| Repositorios académicos | `java.time.Clock`, UUID, cache Room, propiedad de sesión y validación de usuario | No son una dependencia iOS utilizable directamente |
| Asistencia | Room, WorkManager, Hilt, ConnectivityManager, WifiManager, QR y evidencia de red | Requiere implementación y permisos específicos de iOS |
| Seguridad | Android Keystore, DataStore cifrado, inspección JWT Java | Requiere Keychain y diseño de sesión específicos; no simular persistencia segura |
| V8 | Paleta oscura/carmesí, tarjetas y composables; `V8CampusBackdrop` usa `R.drawable` y `painterResource` Android | Reutilizar identidad visual y recursos autorizados; adaptar implementación |
| Backend | Ktor servidor JVM, MongoDB, proveedores institucionales, autenticación real | Se mantiene desplegado y se accede mediante HTTP(S), sin migrar el servidor |

Los documentos de arquitectura son útiles pero contienen afirmaciones desactualizadas: `SYSTEM_ARCHITECTURE.md` dice que no hay endpoints de producto, mientras `AuthRoutes.kt` ya implementa login, registro, refresh y logout. Los contratos y rutas de producción son la autoridad para esta integración.

### Contrato HTTP observado

- `POST /auth/login` recibe `LoginRequest` y responde `ApiResponse<LoginResponse>`; no inventar un prefijo `/v1`.
- `/auth/refresh` y `/auth/logout` reciben `RefreshSessionRequest`.
- La autenticación no configurada responde como dependencia no disponible; no presentar ese error como contraseña incorrecta.
- `/health`, `/ready` y `/version` son comprobaciones diferentes. `/health` exitoso no demuestra que MongoDB ni autenticación estén disponibles. `/ready` puede responder 503 legítimamente.
- Mantener nombres JSON, valores `SerialName`, tokens y envoltura `data`. Los mensajes técnicos y cuerpos completos de errores no se muestran al usuario.

## Viabilidad investigada y alternativas

1. **Recomendada: KMP para contratos/lógica y SwiftUI para la primera interfaz iOS.** Evita tocar pantallas Android; reduce cambios de dependencias. Reutiliza código real de contratos y reglas de roles, con adaptación visual nativa. La interfaz se mantiene separada en esta fase.
2. **KMP y Compose Multiplatform para una nueva interfaz común.** Viable técnicamente: hay Foundation, Material 3, lifecycle y navegación multiplataforma. Exige otra selección de artefactos, recursos `Res`, adaptación de integraciones Android y validación visual por plataforma. No equivale a compilar los actuales módulos Android para iOS.
3. **Migrar ahora todos los módulos `shared` y V8.** Mayor reutilización futura, pero cambia firmas de fechas, publicaciones Gradle y dependencias de backend y Android. No cumple bien el objetivo de una base inicial con cambios mínimos.

La matriz oficial de Kotlin 2.2.21 documenta Xcode 26.0 y AGP hasta 8.11.1. El AGP 8.13.2 del repositorio está fuera de esa matriz KMP. No cambiarlo ni ocultar una advertencia como prueba de compatibilidad: aislar el build KMP inicial de AGP.

## Diseño recomendado

### Aislamiento del build

Crear un build Gradle independiente en `multiplatform/`, dentro del mismo repositorio, con `settings.gradle.kts`, `build.gradle.kts` y módulo `shared`. Ejecutarlo con el wrapper existente mediante `./gradlew -p multiplatform ...`. No modificar el grafo raíz, las versiones Android ni los módulos JVM actuales.

Conservar Kotlin 2.2.21, serialización 1.9.0, coroutines 1.10.2 y Ktor 3.3.3. El módulo tiene targets JVM para pruebas y Apple `iosArm64`, `iosSimulatorArm64`, `iosX64`. Exportar un framework estático `CompaneroShared` mediante integración directa con Xcode. No añadir CocoaPods ni servicios Apple.

### Reutilización real y limitada

Configurar un directorio de fuentes adicional apuntando a `shared/contracts/src/main/kotlin`, con una lista explícita y cerrada de archivos compatibles:

- `AuthContracts.kt`
- `ApiResponse.kt`
- `ApiError.kt`
- `UserSummary.kt`
- `UserRole.kt`

Así se compilan los mismos archivos para JVM existente y Kotlin/Native, sin duplicar DTO ni mover tipos usados por Android. Las reglas `UserRole.isStaff`, `isAdministrative` y `UserSummary.isStaff` se reutilizan realmente. Una prueba debe detectar una futura dependencia Java en esos archivos mediante compilación Native; revisar también la clausura de dependencias al ampliar la lista.

Los DTO móviles de health/readiness/version son adaptaciones explícitas: timestamp como string ISO, mismos campos wire, en paquete móvil separado. No declarar que se está reutilizando `InstantAsIso8601Serializer` ni `shared/models`.

### Cliente y puente iOS

El módulo KMP contiene validación de URL, cliente Ktor, consulta operativa y login contra la API. Usar `MockEngine` en pruebas JVM y motor Darwin en Apple. Instalar negociación JSON y timeouts, sin logging de credenciales. Distinguir errores HTTP, transporte, serialización y cancelación; volver a lanzar `CancellationException`.

URL configurable desde configuración de build y pantalla de conexión. Aceptar HTTPS y HTTP local únicamente en Debug; rechazar URL relativa, host vacío, usuario/password embebidos, fragmentos y query en la base. Normalizar la barra final sin perder un prefijo de ruta. En simulador macOS, `localhost` apunta al Mac, no a `10.0.2.2`. No incorporar una URL de producción supuesta.

El puente Kotlin/Swift expone operaciones con callbacks, resultados simples y cancelación/cierre explícito del cliente. Actualizaciones de UI en el hilo principal. Login inicial mantiene la sesión solo en memoria; cerrar sesión limpia los tokens, cambiar el endpoint limpia la sesión y terminar el proceso requiere login de nuevo. No persistir passwords ni tokens en preferencias. Refresh persistente/Keychain queda documentado como pendiente.

### Proyecto iOS

Crear `iosApp/iosApp.xcodeproj` con esquema compartido, SwiftUI, iOS mínimo 16.0 y bundle ID de desarrollo `org.companerodeescuela.ios`. App display name: `Compañero de Clase`. El framework KMP debe compilarse en una fase previa a las fuentes Swift mediante `embedAndSignAppleFrameworkForXcode`, con sandbox de scripts Xcode desactivado según la integración oficial.

Pantallas iniciales: bienvenida/login, Hoy, Agenda y Conexión. Hoy y Agenda muestran el alcance disponible y los módulos pendientes; no inventan clases, asistencia ni sincronización. Navegación nativa con `NavigationStack` y `TabView`. Conexión permite editar el endpoint y consultar health/readiness. Login usa la API real y permite logout. La navegación no representa autorización del backend.

Aplicar V8 Red Edition: fondo `#08090C`, superficie `#15171C`, tarjeta `#252730`, carmesí `#FF303F`, texto principal `#F8F8FA`, secundario `#ABB0BE`, esquinas de tarjeta 22 y separación 12. Usar tipografía del sistema, áreas seguras, tamaños accesibles y estados de carga/error. SwiftUI replica tokens visuales; no reutiliza los composables Android. Los assets de campus y marca solo se incorporan si se puede conservar su procedencia y verificarlos visualmente.

### Codemagic

Agregar `codemagic.yaml` en la raíz con workflow `ios-simulator`, `instance_type: mac_mini_m2`, Xcode 26.0 y Java 17. Confirmar que la imagen fijada esté disponible al ejecutar el workflow. Conservar la CI Android y API existente.

El workflow valida herramientas, ejecuta pruebas JVM KMP y Native de simulador, compila la aplicación Debug para `generic/platform=iOS Simulator` con `CODE_SIGNING_ALLOWED=NO` y `CODE_SIGNING_REQUIRED=NO`, comprueba `.app` y publica un ZIP de simulador y logs/procedencia del commit. Sin archive de distribución, export IPA, provisioning, certificados ni upload a App Store. Una compilación sin firma no requiere credenciales Apple.

Configurar triggers para PR y rama iOS, cancelando builds obsoletos. Documentar importación del repo y webhook: el YAML versionado no activa por sí solo una aplicación en una cuenta Codemagic. Validar YAML contra el esquema oficial vigente y ejecutar el workflow cuando haya acceso a una cuenta conectada. Si ese acceso falta, declarar la compilación cloud pendiente, nunca aprobada.

## Funciones pendientes después de esta base

Keychain, renovación/persistencia de sesión, biometría, clases y agenda reales, caché/offline académico, asistencia y revisión docente, QR/cámara, evidencia Wi-Fi, sincronización en segundo plano, notificaciones, enlaces profundos, adjuntos/PDF/OCR, tutorías, calificaciones, canal institucional y perfiles de todos los roles. No declarar paridad con Android, TestFlight, firma de dispositivo ni publicación en App Store.

## Verificación y condiciones del PR

1. Crear workspace aislado desde el `origin/main` actualizado en rama `codex/ios-kmp-foundation`; conservar el checkout actual.
2. Ejecutar baseline en esa base y registrar SHA, comandos, resultados y cantidad de pruebas. Los resultados del checkout concurrente son evidencia preliminar, no baseline del nuevo PR.
3. Probar contratos JSON con muestras compatibles con backend, roles, validación de endpoint, prefijos de ruta, 401/403/429/503, cuerpos malformados, timeout y cancelación con `MockEngine`.
4. Ejecutar pruebas JVM y Native; compilar/enlazar Swift y el framework en macOS. Instalar y abrir la app de simulador y comprobar navegación, configuración y estados sin backend. Añadir smoke automatizado si existe infraestructura apropiada.
5. Ejecutar compile Debug Android, todas las suites unitarias, lint, APK Debug y Release; conservar instrumentation en CI. Ejecutar suites API, contratos, modelos y validación, además del smoke backend existente.
6. Validar YAML, esquema Xcode, diff, secretos y ausencia de cambios en producción Android/backend.
7. Crear PR draft, adjuntarlo al chat y documentar cambios, reutilización exacta, pendientes y evidencias. Mantenerlo sin merge hasta checks GitHub, compilación Codemagic del SHA del PR y revisión aprobados.

## Estado de verificación de esta auditoría

Se inició el comando de baseline sobre el checkout actual: `gradlew.bat :shared:contracts:test :shared:models:test :shared:validation:test :services:api:test :apps:android:app:compileDebugKotlin testDebugUnitTest --console=plain --max-workers=2`.

El primer intento no pudo abrir el lock de la caché Gradle por restricciones del sandbox. El segundo intento autorizado terminó con BUILD SUCCESSFUL en 4m 22s: 507 tareas, 110 ejecutadas y 397 up-to-date. Reportes: Android 142 pruebas en 36 suites; contratos 16, modelos 3, validación 7 y API 168; cero fallos y cero omitidas. El checkout cambió de rama durante la ejecución, por lo que esta evidencia preliminar no reemplaza un baseline del worktree aislado. No se ha compilado iOS ni creado un artefacto de simulador.

## Fuentes oficiales consultadas

- [Matriz de compatibilidad Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).
- [Compose Multiplatform y Jetpack Compose](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html).
- [Integración directa Kotlin/Native con Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html).
- [Motores de cliente Ktor](https://ktor.io/docs/client-engines.html).
- [Configuración codemagic.yaml](https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/).
- [Builds iOS de simulador sin firma](https://docs.codemagic.io/yaml-code-signing/ios-simulator-builds/).
- [Kotlin Multiplatform en Codemagic](https://docs.codemagic.io/yaml-quick-start/building-a-kmm-app/).

## Revisión necesaria antes de implementación

La habilidad `superpowers:brainstorming` exige aprobar la especificación escrita antes de elaborar el plan e implementar un cambio arquitectónico. Esta propuesta presenta la decisión concreta: KMP aislado y SwiftUI V8 inicial, sin migrar Android. Solicitar revisión de este documento y consentimiento para un worktree aislado; después elaborar y revisar el plan de implementación según `superpowers:writing-plans`.
