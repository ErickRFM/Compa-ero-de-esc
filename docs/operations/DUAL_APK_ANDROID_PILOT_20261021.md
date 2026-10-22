# Compañero V17 — Instalación y validación de dos APK (piloto)

> **Estado:** implementación en PR apilado, NO liberación de producción. La separación es una frontera de experiencia/paquete Android, no un sustituto de los permisos Ktor/SIII.

## Dos paquetes coinstalables

| APK | `applicationId` | Roles mostrados al iniciar sesión |
| --- | --- | --- |
| Compañero de Escuela (academic) | `org.companerodeescuela` | Alumno, Docente, Tutor |
| Compañero Gestión (management) | `org.companerodeescuela.gestion` | Coordinación, Admin, Superadmin |

Dirección permanece **pendiente de implementación como perfil y capacidad independiente** (no se debe confundir con Coordinación). Jefe y subjefe de grupo siguen siendo delegaciones temporales del alumno. Los perfiles invitado/participante de taller no figuran en estas dos experiencias, sin borrar contratos existentes. Una cuenta con varios roles usa identidades autorizadas en ambas aplicaciones y **dos sesiones locales independientes**; ninguna APK comparte token ni Room con la otra.

Se conserva un solo árbol de módulos y API Ktor con el contrato existente; Gradle usa **flavors** `academic`/`management`. La APK original conserva exactamente su ID para continuidad de instalaciones de prueba, pero tras el cambio las tareas de Gradle específicas son:

```bash
./gradlew :apps:android:app:assembleAcademicDebug :apps:android:app:assembleManagementDebug
./gradlew :apps:android:app:testAcademicDebugUnitTest :apps:android:app:testManagementDebugUnitTest
```

Archivos debug esperados:
- `apps/android/app/build/outputs/apk/academic/debug/app-academic-debug.apk`
- `apps/android/app/build/outputs/apk/management/debug/app-management-debug.apk`

Las releases internas/minificadas se construyen con `assembleAcademicRelease` y `assembleManagementRelease`. Se firman con clave de desarrollo solo para QA; **no** distribuir como APK de producción. Para distribución real usar workflow `android-distribution-signed` con keystore privado, huella SHA-256 aprobada, versión incremental y URL HTTPS. Verificar firma y `applicationId` de **ambas** antes de distribuir.

## Convivencia y migración

- No desinstalar Compañero académico anterior ni borrar sus datos durante el piloto. Su ID no cambia, pero la firma DEBE ser compatible para actualizarlo; APK firmada con otra clave genera conflicto de instalación.
- Gestión utiliza ID nuevo, almacenamiento, WorkManager, preferencias, sesiones y notificaciones del paquete separados. Sin sincronización token entre apps; cada login utiliza cuenta autorizada.
- Los accesos de alumno/docente/tutor no se muestran en Gestión ni viceversa. La app muestra estado sin autorización y botón Cerrar sesión si la cuenta no tiene roles para su APK.
- El módulo de Firebase de Gestión está intencionalmente **desactivado** hasta crear su propia app Android en Firebase y proveer identificadores propios; no reutilizar la configuración académica. La ausencia de FCM **no implica** que la sincronización SIII funcione. La académica mantiene su configuración existente.
- Coordinación se incorpora al carrusel de acceso de Gestión; no habilita nuevos privilegios. No habilitar Dirección mostrando una etiqueta falsa en este paso.
- Para pruebas físicas en Poco X7 Pro, instalar ambas, comprobar iconos y nombres diferentes, iniciar sesión de estudiante, docente, tutor, coordinación, admin, superadmin y multirrol, comprobar cierre y expiración de sesión en cada una, sin cruces de datos.

## Gates de CI

1. `ci-android` compila/testea las dos variantes; otras librerías Android mantienen sus propias pruebas.
2. `lint` completo y suite de backend sin regresiones.
3. Firma R8 `apksigner` y `aapt` por cada `applicationId`; artefactos etiquetados con commit y modo de firma.
4. Instrumentación API31 y API35 por **ambas** variantes, con evidencia extraída del paquete exacto. `AppDistributionSmokeTest` verifica identidad real/rol.
5. Verificación posterior E2E de PDF 9.º A, asignaciones, autorización por institución, tutoría, canal, asistencia y proyección SIII, antes de piloto con datos reales.

## Bloqueos explícitos para distribución institucional

Dirección, gestión real de horarios/PDF confirmada por SIII, FCM en Gestión, migraciones/outbox de SIII y pruebas de identidad institucional no quedan completados por este PR. El backend sigue siendo la autoridad. Ningún secreto/migración/bandera de producción se modifica aquí.
