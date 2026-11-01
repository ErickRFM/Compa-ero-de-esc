# V24 — entrega de código Compañero ↔ SIII

Fecha: 31 de octubre de 2026, America/Mexico_City.

## Alcance y verdad de la entrega

El usuario cambió el alcance a **«Solo trabaja en codigo, las pruebas las hago yo»**. Esta entrega incluye implementación, regresiones preparadas, revisión estática, compilación y APK. No constituye certificación E2E, prueba física ni habilitación productiva. No se ejecutaron suites después de esa instrucción; el intento inicial de pruebas base se interrumpió y no se utiliza como evidencia.

Bases verificadas: Compañero `5eba569084cf2742ae130ec15e457c7759d28254`, con PR #241 ya fusionado; SIII `f19d8a0eacf37960aba95e9d09000ce6d4992f7f`. Sus CI anteriores estaban verdes; esos resultados no certifican V24.

## Matriz de cambios

| Función | Archivos principales | Regresiones preparadas para el usuario | Resultado de código |
| --- | --- | --- | --- |
| QR institucional docente/alumno | `SchoolPresenceService.kt`, `SchoolPresenceRoutes.kt` | `SchoolPresenceNativeScopeTest`, `SchoolPresenceNativeRoutesTest` | Reutiliza jornada y QR existente; valida cuenta e institución vigentes. Roles administrativos sin alumno/docente no pueden registrar ingreso. |
| Inicio de pase docente | `AttendanceSessionService.kt`, `AttendanceFeatureGraph.kt` | `NativeAcademicProjectionTest`, `SiiiAttendanceTest` | Requiere asignación vigente e ingreso QR docente; conserva sesión única por ocurrencia. |
| Avisos elegibles e idempotentes | `AttendanceSessionService.kt`, `AttendancePushEligibility.kt`, `PushOutbox.kt`, `OperationsFeatureGraph.kt`, `ApplicationModule.kt` | `NativeAcademicProjectionTest` | Filtra matrícula e ingreso del alumno antes de encolar; revalida sesión, inscripción, cancelación e ingreso antes de cada envío/reintento. Outages reintentan sin enviar; revocación descarta aviso. |
| Cambios/cancelaciones oficiales | `SiiiSectionProjection.kt`, `MongoSiiiSectionSnapshotRepository.kt`, `SiiiAcademicChangeNotifier.kt`, `AcademicChangePushContract.kt`, gateway FCM y servicio Android | `SiiiChangeNotificationTest` | El proyector real encola avisos genéricos antes de confirmar el evento. Persiste destinatarios afectados para conservarlos tras borrado y reintento; clave por tenant/sección/versión y etiqueta estable evitan destinos/avisos visibles duplicados. Revalida cuenta/tenant al enviar. |
| Ausencias persistentes | `AttendanceSessionService.kt` | `NativeAcademicProjectionTest` | El cierre explícito docente registra ausencias de matriculados sin registro, sin afirmar presencia verificada. Repetir el cierre completa una escritura parcial y conserva confirmaciones/correcciones existentes. El cierre administrativo conserva sus permisos existentes y no atribuye decisiones docentes nuevas. |
| QR y revisión del docente en Android | `AttendanceScreen.kt`, `AttendanceViewModel.kt`, `TeacherHomeScreen.kt` | `AttendanceTeacherPrivacyTest`, compilación de instrumentación Academic/Management | Reutiliza escáner/tarjeta de jornada. Permite abrir registros históricos y corregir con motivo; elegir un pase aún abierto reinicia QR y consulta de registros. Cancela peticiones al cambiar modo/cuenta. |
| Historial SIII legible | SIII: `attendance-subject.ts`, `attendance-view.ts`, `personal-attendance.tsx`, ruta `companion-attendance/mine` | `attendance-view.test.ts`, `personal-attendance-route.test.ts`, `personal-attendance.test.ts` | Etiquetas de materia, grupo, docente y periodo desde la fuente institucional revalidada; filtros por materia/periodo. Ktor sigue conservando el único registro de asistencia. |
| Contrato cruzado | `SiiiFullChainLiveContractTest.kt` y SIII `.github/workflows/verify.yml` | Contrato PostgreSQL → outbox/HMAC → Ktor/Mongo → historial SIII | Fixture adaptado para registrar QR docente y alumno antes de abrir el pase. El workflow debe fijarse al commit del receptor entregado; ejecución a cargo del usuario/CI existente. |

Las rutas Kotlin se encuentran bajo `services/api/src/main/kotlin/org/companerodeescuela/api/` y las pantallas bajo `apps/android/feature/attendance/src/main/kotlin/org/companerodeescuela/feature/attendance/`.

## Verificación realizada

- Compilación Ktor y Academic/Management: aprobada.
- Compilación de código de pruebas API, JVM de asistencia e instrumentación de ambas variantes: aprobada; **no implica ejecución**.
- SIII: `npm run typecheck`, `npm run lint`, `npm run build` aprobados.
- Formato Kotlin sobre archivos cambiados y `git diff --check`: aprobados.
- Revisión independiente estática: corregida la selección de pase activo desde historial y una adaptación de fixture; no se ejecutaron pruebas durante la revisión.
- Compilación final y hashes de APK: ver archivos de entrega y manifiesto local `build/v24-apk-sha256.txt`.

## Comandos para la validación del usuario

```powershell
# API, contratos y aislamiento (Mongo de QA/local, nunca datos productivos)
.\gradlew.bat :services:api:test :shared:contracts:test :shared:validation:test
# JVM Android
.\gradlew.bat testDebugUnitTest :apps:android:app:testAcademicDebugUnitTest :apps:android:app:testManagementDebugUnitTest
# Por cada emulador API 31 y 35 conectado
.\gradlew.bat :apps:android:app:connectedAcademicDebugAndroidTest :apps:android:app:connectedManagementDebugAndroidTest
# SIII, en su repositorio y con PostgreSQL desechable preparado
npm test
npm run test:integration
npm run test:ui
```

El contrato completo requiere los probes SIII, PostgreSQL y Mongo locales; el job `Companion live contract` conserva su preparación y sus comprobaciones de cero omisiones. No apuntar esas variables a producción.

## Prueba funcional reservada al usuario

1. Cuentas autorizadas del mismo tenant, grupo y materia; horario oficial visible en ambos clientes.
2. Docente sin QR no abre pase. QR ajeno y rol sin autorización se rechazan.
3. Alumno con matrícula e ingreso recibe aviso; alumno sin QR o revocado no lo recibe. Duplicar apertura/reintento no crea sesión ni destino duplicados.
4. Recibir FCM no crea asistencia. Confirmar explícitamente deja evidencia para revisión docente.
5. Cerrar el pase conserva registros existentes y crea ausencias restantes. Reabrir el historial permite corregir con motivo; repetir cierre no borra la corrección.
6. SIII muestra el mismo resultado Ktor con materia, grupo, docente y filtros. Cambio de sesión, tenant, versión o inscripción descarta datos no autorizados.
7. Probar red interrumpida, notificaciones denegadas, app en primer plano/fondo/cerrada, accesibilidad, fuentes ampliadas y ambas APK.

## Pendientes explícitos

- Pruebas unitarias, integración, matrices API 31/35 y piloto: ejecución reservada al usuario. No evidencia física nueva.
- CI del commit V24 y aprobación del PR: necesarios antes del merge; no heredar el verde de los commits base.
- V24 no se desplegó ni cambió Firebase, credenciales HMAC, variables de hosting, inscripciones o base productiva.
- Las APK locales usan la URL Render predeterminada; no acreditan que allí esté desplegado V24. La compilación local no tiene las cuatro opciones públicas de Firebase: `FIREBASE_CONFIGURED=false`. Para probar FCM en Académica, recompilar con `infrastructure/scripts/Build-AcademicFirebase.ps1` y la configuración Android autorizada. Gestión mantiene FCM deshabilitado por su política de aislamiento existente. No introducir una cuenta de servicio en la APK.
- Los avisos oficiales también requieren validar recepción real FCM. Su contenido es genérico y abre la aplicación autenticada; los detalles y permisos se consultan en el horario oficial. Los destinatarios retirados reciben solo aviso de actualización, sin datos de su antigua asignación.
- El historial mantiene el alcance actual de inscripciones vigentes y máximo 100 registros; no es un archivo histórico ilimitado.
- Conservar `postman/` ajeno sin incorporarlo al commit.

Commit y PR de cada repositorio se entregan en el cierre del chat y en las descripciones cruzadas de los PR. El cierre del código no declara «100% funcional» ni autoriza fusionar con checks fallidos o pendientes.
