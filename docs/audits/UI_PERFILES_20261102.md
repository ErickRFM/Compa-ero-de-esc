# Auditoría UI/UX y perfiles — 2 noviembre 2026

Base `9ab4655e`. Rama `codex/ui-perfiles-consistencia-20261102`.

## Alcance y evidencia

Inspección del inventario de 22 archivos `*Screen.kt`, profundizando en Perfil, selector, scaffold/resolver, inicios de alumno/docente/tutor/coordinación/administración y Ajustes. El inventario no equivale a certificación visual de todas sus combinaciones. Se reutiliza la identidad V8; no se reemplaza el tema ni la navegación.

Base de comparación: las diez pruebas existentes de `ManagementProfileNavigationTest` y `V14RoleNavigationUiTest`, más las unitarias de `core:navigation`, pasaron en API 30 local (`build/ui-profiles-baseline-20261102.log`). Los destinos de esas fixtures de navegación son texto de prueba; acreditan back stack, no contenido real ni servicios institucionales.

## Hallazgos

| ID | Prioridad | Pantalla / causa | Resultado requerido |
|---|---|---|---|
| ROLE-UI-01 | P2 | Perfil decide solicitud de tutor con el conjunto de roles; una cuenta alumno/docente ve la acción docente aunque use modo alumno | Acciones según experiencia activa y rol autorizado; no es una concesión nueva de permisos de servidor |
| ROLE-UI-02 | P2 | Perfil identifica administración por cualquier rol admin de la cuenta; en modo coordinación puede producir una sección vacía y título ambiguo | Contexto de modo activo separado de roles disponibles; no grupos vacíos |
| ROLE-UI-03 | P1 | Selector centrado sin scroll; múltiples roles y texto al 200% exceden la ventana | Última opción y cerrar sesión alcanzables, selección comprobada |
| VIS-01 | P2 | Encabezados mezclan `headlineSmall`, `headlineLarge`, pesos y descripciones separadas | Primitiva compartida; respetar saludo e identidad de pantallas especiales |
| VIS-02 | P2 | Tutoría y coordinación añaden 16 dp internos sobre los 16 dp de V8GlassCard | Un solo relleno, mayor ancho útil y densidad uniforme |
| VIS-03 | P2 | Título, descripción, refresco y atajo de Perfil compiten en cabecera administrativa | Separar refresco de la cabecera para nombres largos/texto ampliado |
| VIS-04 | P3 | Capturas de Perfil repiten Cuenta/Activa inmediatamente después de identidad/Sesión activa | Eliminar fila redundante; conservar aplicación y versión en Acerca de Compañero |
| A11Y-01 | P2 | Filas propias de Perfil no especifican rol de botón ni altura táctil mínima | Acción anunciada como botón y mínimo 48 dp; información estática sin chevron |
| COPY-01 | P2 | Coordinación muestra identifiers internos (`MANAGE_ACADEMICS`, `MFA Step-Up`) y avisos largos en tarjetas anidadas | Explicar permiso y segundo factor con lenguaje de producto |

## Perfiles y rutas

| Perfil | Distribución | Inicio / herramientas existentes | Comprobación de este lote |
|---|---|---|---|
| Estudiante | Académica | Hoy, Horario, Asistencia, Clases | Resolver y regreso; Perfil sin acciones docentes de otro modo |
| Docente | Académica | Inicio docente, Horario, Asistencia, Canal | Resolver; pendiente no equivale a docente autorizado; solicitud tutor según modo |
| Tutor | Académica | Tutorías, Mi grupo, Justificantes | Resolver y regreso; tarjetas/títulos consistentes |
| Dirección | Gestión | Supervisión, Agenda | Resolver y regreso; identificar Dirección en Perfil |
| Coordinación | Gestión | Supervisión, Agenda | Resolver y regreso; no heredar sección admin de otro modo |
| Administración/general | Gestión | Gestión, Horario, Clases, Canal | Atajos con callbacks/rol/modo; regresiones existentes preservadas |
| Invitado/participante | Académica | Perfil | Identidad explícita, sin herramientas institucionales inventadas |

La selección es presentación. La autoridad de operaciones sigue siendo del servidor. Las pruebas con identidades sintéticas no certifican cuentas institucionales reales.

## Estado de verificación

Las tres regresiones fallaron antes de corregir producto, en API 30 local: solicitud docente en modo alumno, sección administrativa de otra experiencia y ausencia de un ancestro desplazable en el selector. Registro: `build/ui-profiles-red-localized-20261102.log`. Se fijó explícitamente el idioma de las fixtures para que las etiquetas no dependan del emulador.

Se aplicó `V8PageTitle` en doce pantallas (trece usos, contando ambas vistas de Clases), con jerarquía compartida y encabezado semántico. El saludo del alumno y los títulos especiales de Horario/Asistencia conservan su identidad. Perfil identifica la experiencia validada, usa filas con rol de botón/48 dp y presenta la versión real de la app. Tutoría/Coordinación tienen un solo relleno por tarjeta. Gestión/Clases separan la actualización del título.

La revisión independiente del diff no encontró defectos de producción. Detectó un hueco en las pruebas iniciales: se añadió modo activo explícito, callbacks administrativos en la cuenta mixta y rol retirado. La segunda revisión confirmó ese cierre. Las fixtures visuales navegan solo después de que el grafo esté creado; un intento anterior falló por preparar la navegación antes del grafo y no se contabiliza como aprobado.

Contraste calculado de la paleta borgoña oscura por defecto sobre tarjeta V8 `#17191F`: rol activo `#FF303F` **4.81:1**, texto principal `#F8F8FA` **16.56:1**, secundario `#ABB0BE` **8.10:1**. Es una medición de esos pares estáticos, no una certificación de todas las paletas, estados ni imágenes.

La verificación global final pasó con `BUILD SUCCESSFUL`, 1347 tareas y salida 0 (`build/ui-profiles-final-verified-20261102.log`). También pasaron explícitamente las unitarias de ambas variantes de app (`build/ui-profiles-app-unit-verified-20261102.log`). Gradle reutilizó resultados válidos sin cambios y ejecutó de nuevo las suites afectadas.

| Grupo | Registrados | Aprobados | Omitidos |
|---|---:|---:|---:|
| API / Mongo local | 694 | 691 | 3 |
| Shared | 28 | 28 | 0 |
| Bibliotecas Android | 357 | 357 | 0 |
| App Académica unit | 7 | 7 | 0 |
| App Gestión unit | 7 | 7 | 0 |
| UI Académica / API 30 | 28 | 28 | 0 |
| **Total** | **1121** | **1118** | **3** |

Cero fallos. Los tres omitidos son los contratos opt-in `SiiiLiveContractTest`, `SiiiPushLiveContractTest` y `SiiiFullChainLiveContractTest`, que requieren su laboratorio Next/PostgreSQL de loopback. Pasaron `ktlintCheck`, `lint`, ambas APK debug y sintaxis Bash del runner. No cambiaron el baseline, los umbrales de tests existentes ni las aserciones para ocultar fallos.

Comando reproducible con JDK 21 y `V12_TEST_MONGODB_URI=mongodb://127.0.0.1:27017` apuntando al Mongo desechable local:

```powershell
.\gradlew.bat :services:api:test :shared:contracts:test :shared:models:test :shared:validation:test testDebugUnitTest :apps:android:app:testAcademicDebugUnitTest :apps:android:app:testManagementDebugUnitTest ktlintCheck lint :apps:android:app:assembleAcademicDebug :apps:android:app:assembleManagementDebug :apps:android:app:connectedAcademicDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=org.companerodeescuela.ManagementProfileNavigationTest,org.companerodeescuela.ProfileConsistencyUiTest,org.companerodeescuela.V14RoleNavigationUiTest,org.companerodeescuela.V12AdministrationUiTest,org.companerodeescuela.ChannelTaskLayoutUiTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --continue --console=plain --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

CI exige los cuatro casos de `ProfileConsistencyUiTest` y los siete de `ManagementProfileNavigationTest` en Académica/Gestión × API 31/35. Se exportan cuatro PNG específicos de esta pasada. El resultado y SHA finales se consultan en el PR asociado a la rama; el CI previo no sustituye los checks de este lote.

## Evidencia visual

Las cuatro pruebas de Perfil/selector pasaron además en un emulador API 30 configurado temporalmente a 1290 × 2400 px / 480 dpi (**430 dp de ancho**): los topes de 360/390/430 dp pudieron medirse sin compresión por la pantalla anfitriona. Se restauraron y comprobaron tamaño/densidad originales al terminar. Registro: `build/ui-profiles-width430-20261102.log`; esos cuatro casos repetidos no se suman otra vez al total.

Los PNG finales miden 1290 × 2400. Los márgenes exteriores en las fixtures de 360/390 dp corresponden al harness que limita su ventana. Las identidades y callbacks son sintéticos; se renderizan el Perfil y scaffold reales. Se inspeccionaron visualmente los cuatro PNG entregados después de las aserciones de estado visible.

- [Selector original sin desplazamiento, 360 × 400 dp al 200%](evidence/20261102-profiles/picker-before-200.png).
- [Selector corregido: última opción y cerrar sesión alcanzables](evidence/20261102-profiles/picker-after-bottom-200.png).
- [Perfil intermedio que reveló Cuenta/Activa redundantes](evidence/20261102-profiles/profile-before-density-360-dark.png).
- [Perfil estudiante final, 360 dp, oscuro, texto normal](evidence/20261102-profiles/profile-student-360-dark.png).
- [Perfil docente final, 390 dp, claro, texto al 200%](evidence/20261102-profiles/profile-teacher-390-light-200.png).
- [Perfil coordinación final, 430 dp, oscuro, texto al 200%](evidence/20261102-profiles/profile-coordinator-430-dark-200.png).

La aceptación visual completa por rol, rotación, todas las paletas y TalkBack real sigue pendiente. Estas fixtures acreditan renderizado, alcance y callbacks; no una sesión institucional ni entrega física de FCM.

## APK locales de QA

| Variante | Ruta bajo `apps/android/app/build/outputs/apk/` | SHA-256 |
|---|---|---|
| Académica debug | `academic/debug/app-academic-debug.apk` | `ccdfa6459d94fe1ad4b723438b2c2c86f29302d636750115f693f1d7ee56d4c9` |
| Gestión debug | `management/debug/app-management-debug.apk` | `c3250bb64d9c5baa89fa8d4c7240d7bf8d24cdec4ad4af0e76e0c55a6feee64f` |

## Límites de producto conservados

Publicación oficial de notas/horarios SIII, persistencia atómica de intención de aviso, padrón heredado de grupo, traducción completa y aceptación física continúan descritos en `CONSOLIDACION_UI_LOGICA_20261102.md`. Este trabajo de UI no simula su disponibilidad.
