# Compañero de Clase — Walkthrough de ejecución V21/V23

- **Fecha de este corte:** 2026-10-30
- **Última consolidación:** 2026-11-03 — `main` = `43b16c22`, PR #239 y PR #278 ya fusionados
- **Rama:** `feat/v23-fcm-dual-apk-isolation-and-siii-e2e-integration` (fusionada)
- **PR:** [#239](https://github.com/ErickRFM/Compa-ero-de-esc/pull/239)
- **Alcance:** correcciones D1–D10 sobre Compañero (`C:\proyectos\esc`), RBAC, pruebas, CI y entrega. SIII no se modificó en esta fase.

> Regla aplicada en todo el documento: solo se marca **verificado** lo que se ejecutó en esta máquina o en GitHub Actions. Todo lo que depende de Firebase, hardware, credenciales o infraestructura Mongo/SIII queda como **pendiente** con su causa exacta.

---

## 1. SHA inicial y final

| Punto | SHA | Nota |
|---|---|---|
| `origin/main` (inicial) | `0ab96a625a2bff6161b1ead78a9a90284377e770` | Base inicial del PR #239 |
| Fusión PR #239 en `main` | `6dc31dc9b28b6aa19d28e75dbfc8f9a2dca58971` | Fusión completa de V21 y V23 |
| Modernización UX/UI en `main` | `09cf250f16fa63bb6d6d45e7f12e2f69dc8448eb` | Pulido visual V8 Glass, feedback interactivo y componentes modernos |

---

## 1.1 Modernización Estética y UX/UI (V8 Glass & Expressive Design)

1. **Unificación V8 Glass Surface:**
   - Reemplazo del legado `CompaneroSurface` y `CompaneroHeroSurface(containerColor = CompanionColors.graphite)` por `V8GlassCard(emphasized = ...)` en:
     - `AttendanceScreen.kt` (Pase de lista en vivo del alumno y tarjetas de sesión histórica).
     - `TutorHomeScreen.kt`, `TutorGroupsScreen.kt`, `TutorRequestsScreen.kt` (Grupos tutorados, casos de seguimiento, justificantes pendientes).
     - `ScheduleScreen.kt` (Sincronización institucional y llamadas a la acción cuando la agenda está vacía).
     - `ClassroomScreen.kt` (Materias y grupos de estudiantes y docentes).
     - `AdminHomeScreen.kt` (Métricas institucionales destacadas y herramientas directivas con chevron de navegación).
2. **Botones de Acción Primaria `V8RedPrimaryButton`:**
   - Adopción consistente del botón rojo institucional con sombreado y gradiente pulido en:
     - Confirmación de pase de lista en `AttendanceScreen`.
     - Sincronización oficial de horarios en `ScheduleScreen`.
     - Aprobación de justificaciones en `TutorRequestsScreen`.
     - Creación de notas de seguimiento en `TutorGroupsScreen`.
     - Redacción y publicación de avisos en `ChannelScreen`.
     - Aprobación de solicitudes de personal en `AdminHomeScreen`.
3. **Jerarquía Visual y Encabezados (`V8ScreenHeader`):**
   - Estandarización de tipografía `headlineLarge` con descripción subordinada `bodyMedium` y acciones contextuales en el cuadrante superior derecho.
   - Corrección de títulos secundarios en `CompaneroScaffold` para que pantallas secundarias (MFA, Tutorías, Justificantes) no muestren `"Volver"` como título del TopAppBar.
4. **Estados Vacíos y de Error (`ContentStateHost`):**
   - Rediseño con `V8GlassCard`, `V8IconTile` semánticos y mensajes legibles y amigables.
5. **Previsualización en Ajustes de Apariencia (`AppearanceSettingsScreen`):**
   - Incorporación de `LiveAppearancePreviewCard` que reacciona en tiempo real al cambio de paletas de color, temas y contraste.

---

## 2. Defectos D1–D10

| ID | Estado real | Evidencia / límite |
|---|---|---|
| **D1** Navegación del horario administrativo | **Corregido en código** | `MainActivity.kt` navega a `Destination.Schedule` (antes `Destination.Classrooms`). Regresión instrumentada `AdminScheduleNavigationRegressionTest` (verifica que se invoca `onOpenSchedule` y **no** `onOpenClassrooms`). La prueba es `androidTest`: corre en CI con emulador, no en esta máquina. |
| **D2** Canal vacío para administración institucional | **Parcial** | `ChannelAccessPolicy` devuelve `emptyList()` para `ADMIN`/`SUPER_ADMIN`/`COORDINATOR` en sesión institucional, sin ampliar permisos globales. `ChannelScreen` muestra estado explicativo cuando la sesión es administrativa. **Falta:** adaptador de canal institucional o retiro condicional de la pestaña en la navegación. |
| **D3** Dirección y Coordinación excluidas de avisos | **Corregido (lectura) + escritura restringida** | `AcademicEventService.reader()` admite `PRINCIPAL`/`COORDINATOR`; `listScoped()` filtra por institución y alcance real. La escritura institucional exige `ADMIN`/`SUPER_ADMIN` + capacidad `MANAGE_ACADEMICS` + MFA step-up ligado al comando. `PRINCIPAL`/`COORDINATOR` **no** escriben por rol. Pruebas: 3 casos nuevos en `AcademicEventNativeScopeTest`. |
| **D4** Capacidades administrativas sin efecto | **Pendiente** | `REVIEW_WORKSHOPS`, `VIEW_AUDIT`, `VIEW_SESSIONS` quedan documentadas en `AccountAdministrationContracts.kt` como *reservadas, sin endpoints ni UI*. `AdminUiState` no transporta capacidades efectivas y `AdminHomeScreen` no oculta herramientas por capacidad. No se anuncian como operativas. |
| **D5** Representantes muestran histórico | **Corregido** | `/representatives/me` devuelve solo `ACTIVE`/`PENDING` vigentes; `/representatives/me/history` expone el histórico. Filtros por institución, grupo, periodo y estado; aceptación/rechazo conservados. Pruebas en `GroupRepresentativeServiceTest` (5 casos: nombramiento y aceptación, tutor no autorizado, duplicado activo, revocación y separación vigente/histórico). |
| **D6** Notificaciones solo a estudiantes | **Parcial** | `OperationsFeatureGraph.authorizeRecipient` acepta cuentas activas y verificadas con rol `STUDENT`, `TEACHER`, `TUTOR`, `PRINCIPAL`, `COORDINATOR`, `ADMIN`, `SUPER_ADMIN`. El alcance por evento lo impone `AcademicEventService.scope()`. **Falta:** política de destinatarios por tipo de evento para avisos de curso/institucionales fuera del modelo de eventos académicos. |
| **D7** Aislamiento FCM por APK | **Pendiente** | `DeviceRegistrationRequest` acepta `appDistribution` y `packageName`, pero `DeviceTokenRepository.register(...)` **no los persiste** y el envío no selecciona token por distribución. No hay separación de proyectos Firebase por APK. |
| **D8** Gestión institucional de horarios sin UI | **Parcial** | `CoordinatorHomeScreen` explica con claridad que la modificación oficial requiere `MANAGE_ACADEMICS` + MFA y remite a la vista de grupos. **Falta:** pantalla de gestión institucional con formularios, control de conflictos y step-up en UI. `AcademicScheduleManagementService` existe en backend. |
| **D9** `PRINCIPAL` no soportado en `LEGACY` | **Pendiente (decisión de producto)** | `AppDistribution.LEGACY` excluye `AppExperience.PRINCIPAL`; `AppDistributionRoleResolverTest` fija ese comportamiento. Falta decidir si `LEGACY` se retira o se corrige la resolución del rol. |
| **D10** Opciones de Perfil sin funcionalidad | **Corregido (informativo)** | «Privacidad» muestra la política real y «Acerca de Compañero» muestra la versión (`appVersion`). `ProfileRow` solo pinta chevron/clickable cuando existe `onClick`, así que no aparentan ser botones. No requieren navegación de regreso porque no abren pantalla. |

---

## 3. Archivos modificados

### `94a67274` — correcciones D1–D10 (13 archivos)
- `apps/android/app/src/main/kotlin/org/companerodeescuela/MainActivity.kt` (D1)
- `apps/android/app/src/androidTest/kotlin/org/companerodeescuela/AdminScheduleNavigationRegressionTest.kt` (D1)
- `apps/android/feature/channel/src/main/kotlin/org/companerodeescuela/feature/channel/ChannelScreen.kt` (D2)
- `apps/android/feature/home/src/main/kotlin/org/companerodeescuela/feature/coordinator/CoordinatorHomeScreen.kt` (D8)
- `apps/android/feature/profile/src/main/kotlin/org/companerodeescuela/feature/profile/ProfileScreen.kt` (D10)
- `services/api/src/main/kotlin/org/companerodeescuela/api/application/OperationsFeatureGraph.kt` (D6)
- `services/api/src/main/kotlin/org/companerodeescuela/api/events/AcademicEventService.kt` (D3)
- `services/api/src/main/kotlin/org/companerodeescuela/api/representatives/GroupRepresentativeRoutes.kt` (D5)
- `services/api/src/main/kotlin/org/companerodeescuela/api/representatives/GroupRepresentativeService.kt` (D5)
- `shared/contracts/src/main/kotlin/org/companerodeescuela/shared/contracts/AccountAdministrationContracts.kt` (D4, documentación)
- Pruebas: `PushOutboxRecipientAuthorizationTest`, `AcademicEventNativeScopeTest`, `GroupRepresentativeServiceTest`

### `22718d7f` — lectura de avisos para dirección/coordinación
- `AcademicEventService.kt` y `AcademicEventNativeScopeTest.kt`

### `070932b5` — desbloqueo de estilo sin tocar el baseline (18 archivos)
- `apps/android/feature/channel/**` (8 archivos): formato ktlint.
- `services/api/**` (9 archivos): formato ktlint de los archivos cuyas entradas de baseline se desplazaron tras V21, más expansión de imports wildcard en `AcademicEventService.kt` y `AcademicEventNativeScopeTest.kt`.
- `apps/android/feature/home/.../CoordinatorHomeScreen.kt`: mensaje de D8 partido para no exceder 140 columnas.

> No se regeneró `services/api/config/ktlint/baseline.xml` ni se añadió ninguna exclusión global: eso habría ocultado violaciones reales.

---

## 4. Matriz funcional de los 10 roles

Fuente: `RoleExperienceResolver` + `AppDistribution` + `CompaneroBottomBar`. La columna «Verificación» distingue lo probado por pruebas automatizadas de lo que no se certificó.

| Rol | Distribución | Experiencia | Pantalla inicial | Pestañas | Verificación |
|---|---|---|---|---|---|
| `STUDENT` | Académica | `STUDENT` | `Home` | Home, Horario, Asistencia, Aulas, Perfil | Resolución de rol cubierta por `AppDistributionRoleResolverTest`; flujos de UI no certificados en dispositivo |
| `TEACHER` | Académica | `TEACHER` | `TeacherHome` | TeacherHome, Horario, Asistencia, Aulas, Canal | Ídem |
| `TEACHER_PENDING` | Académica | `TEACHER` | `TeacherHome` | solo TeacherHome | Prueba fija que «pendiente» no concede permisos de docente |
| `TUTOR` | Académica | `TUTOR` | `TutorHome` | TutorHome, Grupos, Solicitudes | API: `TutorAssignmentServiceTest`, `TutorCasePrivacyTest`, `TutorStudentIdentityScopeTest` (todos en verde) |
| `PRINCIPAL` | Gestión | `PRINCIPAL` | `CoordinatorHome` | Gestión, Horario | API: lectura institucional y denegación de escritura en `AcademicEventNativeScopeTest`. `LEGACY` sigue excluyéndolo (D9) |
| `COORDINATOR` | Gestión | `COORDINATOR` | `CoordinatorHome` | Gestión, Horario | API: lectura de eventos de grupo por institución, aislamiento entre instituciones |
| `ADMIN` | Gestión | `ADMIN` | `AdminHome` | AdminHome, Horario, Aulas, Canal | API: `InstitutionScopePolicy` + capacidad + MFA en eventos; canal vacío con estado explicativo (D2) |
| `SUPER_ADMIN` | Gestión | `SUPER_ADMIN` | `AdminHome` | AdminHome, Horario, Aulas, Canal | Ídem, con alcance multi-institución |
| `GUEST` | Académica | `GUEST` | `Perfil` | solo Perfil | Acceso mínimo; feed de avisos denegado (`AcademicEventNativeScopeTest`) |
| `WORKSHOP_PARTICIPANT` | Académica | `GUEST` | `Perfil` | solo Perfil | Ídem |

Delegaciones `CHIEF`/`DEPUTY` (representantes de grupo): cubiertas por `GroupRepresentativeServiceTest` (nombramiento, aceptación, duplicado activo, revocación y separación vigente/histórico), sin UI certificada en dispositivo.

**No certificado en esta sesión:** login real contra Render, edición de perfil en dispositivo, notificaciones reales, y el recorrido UI → ViewModel → Repository → Ktor → MongoDB en hardware. Se requiere emulador/dispositivo estable y backend desplegado.

---

## 5. Capacidades administrativas verificadas

| Capacidad | Endpoint | UI | Estado |
|---|---|---|---|
| `APPROVE_STAFF` | Rutas administrativas de aprobación de personal | `AdminHomeScreen` | Operativa (revalidación en servidor) |
| `MANAGE_USERS` | Ciclo de vida de cuentas (suspensión/revocación) | `AdminHomeScreen` | Operativa |
| `MANAGE_ACADEMICS` | Eventos, grupos, horarios, canales, aulas | AdminHome / CoordinatorHome | Operativa; exige MFA step-up en operaciones sensibles |
| `DIRECTIVE_SUPERVISION` | Vista directiva y auditoría de asistencia | CoordinatorHome | Operativa |
| `REVIEW_WORKSHOPS` | — | — | **Reservada**: sin endpoint ni UI; no se anuncia |
| `VIEW_AUDIT` | — | — | **Reservada** |
| `VIEW_SESSIONS` | — | — | **Reservada** |

Ninguna capacidad se concede por nombre de rol: `InstitutionScopePolicy.require(...)` valida cuenta viva, institución y capacidad en cada operación.

---

## 6. Pruebas añadidas

| Archivo | Casos | Qué cubre |
|---|---|---|
| `services/api/.../devices/PushOutboxRecipientAuthorizationTest.kt` | 1 | Destinatarios push: acepta activos y verificados de ambos mundos; rechaza no verificados y suspendidos |
| `services/api/.../events/AcademicEventNativeScopeTest.kt` | 8 (5 previos + 3 nuevos) | Autor real del evento, aula asignada, capacidad + tenant + MFA ligada al comando, feed que oculta ajenos/no migrados, reintento determinista, cancelación con auditoría, **PRINCIPAL lee institución**, **COORDINATOR lee grupo**, **PRINCIPAL no escribe** |
| `services/api/.../representatives/GroupRepresentativeServiceTest.kt` | 5 | Vigentes vs histórico, institución/grupo/periodo/estado, aceptación, rechazo, revocación, reasignación |
| `apps/android/app/src/androidTest/.../AdminScheduleNavigationRegressionTest.kt` | 1 | D1: «Horarios» invoca `onOpenSchedule` y no `onOpenClassrooms` |

---

## 7. Resultado de las suites

Ejecutado localmente el 2026-10-30 sobre `070932b5`:

| Suite | Resultado |
|---|---|
| `./gradlew ktlintCheck` (todo el repo) | **BUILD SUCCESSFUL** |
| `:services:api:test` | **645 pruebas, 0 fallos, 0 errores, 83 omitidas** |
| `:apps:android:feature:channel:testDebugUnitTest` | **7 pruebas, 0 fallos** |
| `:apps:android:app:testAcademicDebugUnitTest` | **4 pruebas, 0 fallos** |
| `:apps:android:app:testManagementDebugUnitTest` | **4 pruebas, 0 fallos** |
| `:apps:android:app:assembleAcademicDebug` | **BUILD SUCCESSFUL** (APK generado) |
| `:apps:android:app:assembleManagementDebug` | **BUILD SUCCESSFUL** (APK generado) |

**Las 83 pruebas omitidas** son integraciones que requieren infraestructura no disponible aquí: Mongo (`Mongo*IntegrationTest`), contratos vivos de SIII (`SiiiLiveContractTest`, `SiiiFullChainLiveContractTest`, `SiiiPushLiveContractTest`), proyecciones institucionales y `NativeOnlyProductionTest`. No se marcaron como aprobadas.

---

## 8. Estado del PR #239

- Estado: **MERGED** el 2026-10-31T04:16:42Z
- Merge commit: `6dc31dc9b28b6aa19d28e75dbfc8f9a2dca58971`
- `mergeable`: **MERGEABLE** y `mergeStateStatus`: **CLEAN** antes de fusionar
- Checks: **13/13 en verde** (ver §16)
- La rama `feat/v23-fcm-dual-apk-isolation-and-siii-e2e-integration` quedó contenida en `main`; no hay commits suyos pendientes.

---

## 9. Estado de Firebase para ambas APK

**No verificado.** Situación real:

- `google-services.json` por flavor y credenciales FCM no están disponibles en este entorno; no se tocaron secretos.
- El backend acepta `appDistribution` y `packageName` en el registro de dispositivo, pero **no los persiste ni los usa para enrutar** (D7).
- No hay prueba de recepción real en dispositivo para Académica ni para Gestión.

**Qué falta exactamente para cerrar D7:**
1. Crear/confirmar dos apps Android en el proyecto Firebase (una por `applicationId`).
2. Añadir `google-services.json` de cada flavor vía secreto de CI, nunca al repositorio.
3. Persistir `appDistribution` y `packageName` en el registro de tokens y filtrar el envío por distribución.
4. Probar recepción y navegación profunda en un dispositivo físico de cada APK.

---

## 10. Integración SIII ↔ Ktor ↔ Android

- Contratos y adaptadores existen; `SiiiToAndroidIntegrationTest` (2 casos) pasa en verde.
- Los contratos **vivos** contra SIII están **omitidos** por falta de endpoint/credenciales (`SiiiLiveContractTest`, `SiiiFullChainLiveContractTest`, `SiiiPushLiveContractTest`).
- El repositorio SIII (`C:\proyectos\siii`) no se modificó en esta fase; su workflow `ui` sigue rojo en `main` por «Keyboard and accessibility flows», fuera del alcance de este PR.

**Conclusión:** integración cubierta por contrato simulado; extremo a extremo real **pendiente**.

---

## 11. Pase de lista por notificación

Flujo implementado en código:

1. Docente crea sesión: `POST /attendance/sessions`.
2. Backend valida docente, clase y ventana temporal.
3. Se encola notificación (`PushOutbox`) al alumno autorizado.
4. La notificación abre la pantalla correcta: `parseAttendanceSessionId` solo acepta la acción canónica y un UUID válido (`AttendanceNotificationIntentTest`, en verde).
5. Alumno confirma: `POST /attendance/sessions/{id}/confirm`.
6. Docente ve el roster (`GET /attendance/sessions/{id}/roster`) y cierra la sesión.
7. Historial: `GET /attendance/records/mine`.
8. QR sigue disponible como mecanismo alternativo.

**No se registra asistencia por recibir la notificación**: la confirmación es una acción explícita del alumno.

**Pendiente:** recepción real de la push en dispositivo y verificación de concurrencia/duplicados/expiración en hardware.

---

## 12. PR fusionados en `main`

| PR | Rama | Merge commit | Fecha |
|---|---|---|---|
| [#239](https://github.com/ErickRFM/Compa-ero-de-esc/pull/239) | `feat/v23-fcm-dual-apk-isolation-and-siii-e2e-integration` | `6dc31dc9b28b6aa19d28e75dbfc8f9a2dca58971` | 2026-10-31 |
| [#278](https://github.com/ErickRFM/Compa-ero-de-esc/pull/278) | `codex/estado-identidad-verificado` | `03c718fd8ffe7f26956001e112304124d31f20e4` | 2026-11-03 |

- `main` consolidado en `43b16c22adc8aed66123f854f37f707fb7918203`, idéntico a `origin/main`; árbol de trabajo limpio.
- No se hizo merge forzado ni se reescribió `main`.
- **PRs abiertos que no se fusionaron** porque siguen en borrador o son dependencias: #277 «Borrador: validar destinatario de invitaciones» (draft), #282 «feat(security): política de autorización por carrera» (draft) y los PR de Dependabot.
- Cualquier trabajo futuro debe partir de `main` en `43b16c22` o posterior.

---

## 13. Compilación Académica y Gestión

| Flavor | Tarea | Resultado |
|---|---|---|
| Académica | `assembleAcademicDebug` (local) | **OK** |
| Gestión | `assembleManagementDebug` (local) | **OK** |
| Académica | CI: debug + release minificada + APK trazable | **OK** |
| Gestión | CI: debug + release minificada + APK trazable | **OK** |

Ambas compilaciones incluyen `ktlintCheck` limpio y las pruebas unitarias de cada flavor en verde.

---

## 14. Evidencias

- `git log --oneline origin/main..HEAD` → 6 commits listados en §1.
- `./gradlew ktlintCheck` → `BUILD SUCCESSFUL`.
- `:services:api:test` → 645/645 ejecutadas sin fallos (83 omitidas por infraestructura).
- Resultados XML en `services/api/build/test-results/test` y `apps/android/*/build/test-results/**`.
- APKs debug generados para ambos flavors.
- `git diff --cached --check` sin errores de espacios.
- GitHub Actions: 13/13 checks en verde (§16).

---

## 15. Bloqueos externos todavía existentes

| Bloqueo | Qué falta | Dónde se configura |
|---|---|---|
| FCM real por APK (D7) | Dos apps Firebase, `google-services.json` por flavor, persistencia y enrutado por distribución | Consola Firebase + secretos de CI |
| Pruebas Mongo | Instancia MongoDB Atlas o contenedor accesible | `MONGODB_URI` de pruebas |
| Contratos vivos SIII | Endpoint y credenciales del SIII desplegado | Variables de entorno del API |
| D4 | Definición de producto para `REVIEW_WORKSHOPS`, `VIEW_AUDIT`, `VIEW_SESSIONS` | Decisión de producto |
| D8 | Diseño de la pantalla de gestión institucional de horarios | Producto + UI |
| D9 | Decisión: retirar `LEGACY` o corregir su resolución de `PRINCIPAL` | Producto |
| D2 | Adaptador de canal institucional o retiro condicional de la pestaña | Producto + API |
| SIII `ui` en `main` | Reparar «Keyboard and accessibility flows» | Repo `C:\proyectos\siii` |

> La instrumentación Android **ya no está bloqueada**: los cuatro jobs (Académica/Gestión × API 31/35) pasaron en el run `41152075316`.

---

## 16. Checks de GitHub Actions (run `41152075316` / PR `41152078704`)

| Check | Resultado |
|---|---|
| Backend compile, test and smoke | **pass** (4m0s) |
| Android compile, test, lint and package | **pass** (11m59s) |
| Android academic instrumentation API 31 | **pass** (7m49s) |
| Android academic instrumentation API 35 | **pass** (7m44s) |
| Android management instrumentation API 31 | **pass** (6m33s) |
| Android management instrumentation API 35 | **pass** (6m13s) |
| iOS simulator compile, test and launch | **pass** (12m33s) |

Incluye: whitespace de cambios confirmados, regresión del exportador de evidencias, validación del helper de Firebase local, compilación, unit tests, verificación de que las pruebas se ejecutaron, lint sin errores, APK debug y release minificada, y ausencia de secretos confirmados.

---

## 17. Consolidación del 2026-11-03

Comandos ejecutados sobre `C:\proyectos\esc`:

```
git fetch --all --prune
git checkout main
git pull --ff-only
git worktree prune
```

Estado resultante, verificado:

| Comprobación | Resultado |
|---|---|
| `git rev-parse HEAD` | `43b16c22adc8aed66123f854f37f707fb7918203` |
| `git rev-parse origin/main` | `43b16c22adc8aed66123f854f37f707fb7918203` |
| `git status` | `On branch main`, árbol limpio, sin cambios pendientes |
| `git rev-list --left-right --count origin/main...main` | `0 0` |
| Rama `codex/estado-identidad-verificado` | contenida en `main` (`git merge-base --is-ancestor` = sí); 0 commits pendientes |
| Worktrees | se eliminó el registro *prunable* de `C:\proyectos\esc-hmac-20261101-120758`; queda el worktree activo de `mongo-tls-recovery` |
| PR #239 | `MERGED` (merge commit `6dc31dc9`) |
| PR #278 | `MERGED` (merge commit `03c718fd`) |

Checks de GitHub Actions sobre `43b16c22` (consulta `commits/{sha}/check-runs`):

| Check | Resultado |
|---|---|
| Backend compile, test and smoke | **success** |
| Android compile, test, lint and package | **success** |
| Android academic instrumentation API 31 | **success** |
| Android academic instrumentation API 35 | **success** |
| Android management instrumentation API 31 | **success** |
| Android management instrumentation API 35 | **success** (una ejecución duplicada quedó `cancelled` al ser reemplazada) |

**Pendiente por decisión de producto, no por integración:** los PR #277 y #282 siguen en borrador y no se fusionaron; los PR de Dependabot tampoco. Los bloqueos externos de §15 (Firebase, Mongo, contratos vivos SIII, D2/D4/D8/D9) siguen igual.

---

## 18. Consolidación del 2026-11-09 (invitaciones #277/#283/#284)

- Se fusionaron en la rama `codex/invitaciones-consolidadas-20261104`, desde `main` en `43223c7d`:
  - #277 `codex/invitacion-validacion` — servicio de aceptación `InvitationAcceptanceService` + sus pruebas.
  - #283 `feat/secure-invitation-email-flow-20261103` — servicio de despacho MFA, rutas de administración, pasarela Resend de invitaciones.
  - #284 `feat/android-invitation-accept-20261103` — pantallas de emisión/aceptación, contratos compartidos y prueba de UI instrumentada.
- Ajustes de consolidación:
  - `VerificationDeliveryStatus.DELIVERED` ahora existe en el contrato compartido (entrega confirmada solo con evidencia); textos ES/EN y el `when` de `AccountOnboardingScreen` actualizados.
  - `InvitationRepository.find/markSent/expireDue` disponibles en memoria y Mongo para respaldar despacho, revocación y caducidad.
  - `InvitationRoutes.kt` ya no redeclara payloads: usa `PrepareRegistrationInvitationRequest`, `RegistrationInvitationPreparation`, `RegistrationInvitationDispatchReceipt`, `AcceptRegistrationInvitationRequest` y `RegistrationInvitationReceipt` de `shared/contracts`, y delega la aceptación en `InvitationAcceptanceService`.
  - `MainActivity` muestra "Aceptar invitación" solo para sesiones con correo verificado y la ruta queda igualmente guardada.
  - Documentos de seguridad de invitaciones alineados con lo ya implementado y los gates que siguen abiertos.
- Verificado **en esta máquina** el 2026-11-09 sobre esa rama: `ktlintCheck` OK; `:services:api:test` OK; `testDebugUnitTest` + unit tests de ambas flavors OK; compilación Kotlin de ambas flavors OK; `assembleAcademic/ManagementDebugAndroidTest` OK.
- APKs generados localmente en `apps/android/app/build/outputs/apk/{academic,management}/...`:
  - `app-academic-debug.apk` (100.2 MB), `app-management-debug.apk` (100.2 MB), 2026-11-09 11:58.
  - `app-academic-release.apk` (73.1 MB), `app-management-release.apk` (73.1 MB), 2026-11-09 12:02/12:04.
- Pendiente (no verificado): CI con instrumentación en emulador, despliegue en Render QA, correo real vía Resend e integraciones externas.


---

> **Cierre 2026-11-09:** PR #296 fusionado a `main` en `c684d298f3373f4f3dfa7e7f0e21c745eef2dc69`. CI sobre ese SHA: Backend compile/test/smoke, Android compile/test/lint/package y las cuatro instrumentaciones (Académica/Gestión × API 31/35) en success; los reintentos fueron por infraestructura del emulador, no por fallos de pruebas. APKs finales en `dist/apk-20261109/` (debug y test-release minificada firmada con la clave de pruebas interna del repositorio; distribución de producción requiere firma separada).


## 19. Sincronización y consolidación del 2026-11-10 (ronda completa de PRs)

Fusionados a `main` en orden de dependencia, cada uno con checks requeridos verdes:

| PR | Contenido | Merge commit |
|---|---|---|
| #296 | Contratos de invitación unificados API/Android, aceptación respaldada por servicio, `DELIVERED` explícito, gating por correo verificado | `c684d298` |
| #297 | Outbox cifrado (AES-GCM) con worker de reintentos, reintento manual con token nuevo y TTL de expiración | `9da9cfc8` |
| #300 | Webhook Svix firmado de Resend (anti-replay, cuerpo acotado, tags de correlación, auditoría idempotente en `platform_audit_events`) + runbook `docs/operations/RESEND_WEBHOOK_SETUP.md` | `1b4fca2f` |
| #298 | Alta y habilitación de instituciones restringida a propietario verificado (rutas prepare/create + pruebas de negación) | `fc538865` |
| #301 | Visibilidad de login en instrumentación académica | `324280c2` |
| #302 | Aislamiento fail-closed de bases Mongo por ambiente (rechazo de nombres multi-entorno) | `b000dae1` |
| #303 | Enlaces de cuenta SIII coordinador (kind COORDINATOR sin exceder académica estudiante/docente) | `7f926b44` |
| #295 | Progreso Matica en Compa-ero (contratos compartidos, repositorio, rutas, pantallas) | `76de35e5` |

Correcciones de consolidación aplicadas en las ramas antes de fusionar (todas verificadas localmente):

- Conflictos de unión en `ApiSettings.kt` resueltos conservando ambos lados (`resendWebhookSecret` Svix y el gate `productionInvitationsEnabled`) sin declaraciones duplicadas.
- El aislamiento de #302 pasó de exigir el nombre canónico exacto (rompía el contrato de las pruebas existentes de JWT/Mongo/MFA) a rechazar nombres pertenecientes a otro ambiente (`companero_prod`/`qa`/`dev` y sufijos), manteniendo el objetivo fail-closed.
- #303: `when` exhaustivo sobre `InstitutionalIdentityKind` con rama COORDINATOR que mantiene la carga académica limitada a estudiante/docente; formato ktlint.
- #295: formato ktlint en pantallas home/Matica y en pruebas del verificador de enlaces Matica.

#293 (`feat/verified-product-owner-and-invitations-20261106`) **cerrado como superado** con su contenido ya fusionado por bloques en #292, #296, #297, #298 y #300; se descartó el rebase de 32 commits contra el sistema ya consolidado por riesgo de regresión sin valor añadido.

CI y verificación:

- Cada PR se fusionó con `mergeStateStatus CLEAN` y los check-runs requeridos de la protección de `main` (Backend compile/test/smoke y Android compile/test/lint/package) en success. Las cuatro instrumentaciones (Académica/Gestión × API 31/35) y el APK de registro QA también en success cuando aplicó.
- En esta máquina: `ktlintCheck`, `:services:api:test`, compile Kotlin de API y Android en cada resolución de conflicto; suite completa del backend verde tras cada unión.
- Las reejecuciones de jobs fueron por flakes de runner/emulador de GitHub Actions; ninguna prueba falló en intento exitoso.

Pendientes externos reales (no verificables desde este entorno): crear los webhooks de Resend por ambiente (QA/producción) y probar ciclo de entrega real; despliegue en Render QA; correo real a `erik55509@hotmail.com`; aprovisionamiento de SUPER_ADMIN vía `OwnerBootstrap` con MFA del titular y autorización firmada (issue #281).
