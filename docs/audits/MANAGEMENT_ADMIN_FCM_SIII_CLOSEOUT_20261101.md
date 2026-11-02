# Compañero Gestión — cierre técnico y puertas de publicación (2026-11-01)

## Cortes verificados
- Compañero `main` inicial: `852fc391482ef957d8a89b54c70bc21647f7bd5a`.
- SIII `main` inicial: `ff39e7a2cafbf79785796df39fb15347f6bff5a9`.
- Gestión Android: `org.companerodeescuela.gestion`. Académica: `org.companerodeescuela`.
- Ktor Render principal: `https://compa-ero-de-esc.onrender.com`; la cuenta QA usa otro servicio y no reemplaza producción.
- Implementación aislada en PR #258. No confundir compilación, entrega CI, despliegue o prueba física.

## Lo corregido
1. `ProfileScreen` presenta rutas reales de Gestión solo con experiencia activa `ADMIN`/`SUPER_ADMIN` y paquete `MANAGEMENT`: panel, horario y clases. Todo continúa sujeto a sesión y backend; no se introducen grants ni rutas ficticias de auditoría.
2. Firebase de Gestión es un **Android app** distinto, configurado con `COMPANERO_MANAGEMENT_FIREBASE_APP_ID`, `COMPANERO_MANAGEMENT_FIREBASE_PROJECT_ID`, `COMPANERO_MANAGEMENT_FIREBASE_SENDER_ID` y `COMPANERO_MANAGEMENT_FIREBASE_API_KEY`. Los cuatro faltantes mantienen FCM off. Lote parcial falla al construir. El ID Android no puede reutilizar el académico.
3. Se eliminaron tokens `LEGACY` como destinatarios de avisos dirigidos a `MANAGEMENT`, tanto en Mongo como en memoria. El filtro exige `org.companerodeescuela.gestion`. El registro rechaza declaraciones de paquete/distribución discordantes. **La declaración del cliente NO equivale a certificación criptográfica del APK**; el servidor sigue validando actor, sesión, recurso y scope.
4. La APK Management nunca debe mostrar una acción de asistencia de alumno en una notificación. El botón y el título de aviso están diferenciados por distribución.
5. Se añadió el generador local `infrastructure/scripts/Build-ManagementFirebase.ps1` para validar la configuración del archivo JSON fuera del repositorio.

## Restricciones institucionales confirmadas
- El editor de Gestión usa `ClassroomAdministrationViewModel.prepareSchedule` → `AcademicRepository.publishSchoolSchedule` → `POST /academic/manual-schedule` → `AcademicScheduleManagementService.upsertScoped`.
- La fuente persistida es explícitamente `AcademicDataSource.ADMIN_MANUAL`. **No produce una publicación oficial de SIII**. El texto visible `academic_schedule_explainer` ya anuncia esa limitación.
- El endpoint requiere usuario activo y verificado, `MANAGE_ACADEMICS`, institución/grupo actuales, docente-clase real, revisión de alcance y prueba MFA asociada al comando. El registro mantiene `operationId`, `operationDigest`, `groupScopeRevision` y revisión, pero eso no sustituye una bitácora inmutable de todas las acciones de Gestión.
- SIII es fuente oficial de matrículas, inscripciones, grupos, horarios y calificaciones; `src/app/api/v1/institutions/[institutionId]/schedule-patterns/route.ts` publica con autoridad SIII y encola snapshots/outbox. **No se debe copiar un token SIII al móvil o inventar HMAC con scope de escritura.**

## Contrato faltante para publicar desde Compañero
Diseñar y probar explícitamente un flujo de **propuesta**, no una falsa publicación:
1. El administrador autorizado crea o edita un borrador `ADMIN_MANUAL` con `operationId`, versión previa y alcance.
2. Ktor solicita revisión a un endpoint SIII con nueva credencial `schedule:propose`, firma/replay protection por institución y razón explícita. El endpoint no hereda `group:write` ni reutiliza `attendance:read`.
3. SIII valida responsable institucional y periodo; confirma/deniega la propuesta mediante acción autorizada en su ERP y transacción oficial.
4. SIII encola snapshot firmado con nueva versión y tombstone de la versión previa, reintenta el outbox y responde con estado auditable.
5. Ktor solo muestra `publicado en SIII` tras consumir confirmación firmada y reconciliar ID/versión; antes debe mostrar `pendiente`, `rechazado` o `fuera de sincronización`.
6. E2E institucional: admin crea → aprobación SIII → outbox → worker Ktor → horario alumno/docente → cambio/cancelación/revocación → historial/auditoría. No crear alumnos, clases, enrolamientos ni grants en producción para forzar el test.

## Configurar Firebase de Gestión y probarlo sin secretos en el repo
1. Registrar la aplicación Android **`org.companerodeescuela.gestion`** en el proyecto Firebase compatible con el emisor FCM de Ktor (o provisionar un emisor independiente).
2. Descargar su `google-services.json` y guardar el archivo fuera del control de versiones, por ejemplo `apps/android/app/google-services-management.json`; ambos nombres están ignorados.
3. En Windows, en la raíz del repo:
   - `powershell -ExecutionPolicy Bypass -File infrastructure/scripts/Build-ManagementFirebase.ps1 -ValidateOnly`
   - `powershell -ExecutionPolicy Bypass -File infrastructure/scripts/Build-ManagementFirebase.ps1 -BuildType Debug`
4. Para GitHub Actions, configurar las **cuatro variables públicas** `COMPANERO_MANAGEMENT_FIREBASE_*` en Variables del repositorio. Ningún secreto de Firebase Service Account se incluye en Android. La credencial de envío permanece solo en Render.
5. Verificar durante el piloto: permiso POST_NOTIFICATIONS, token de Gestión registrado con paquete/distribución/sesión correctos, notificación institucional dirigida a un administrador autorizado, recepción foreground/background/killed, enlace real y revocación. Nunca usar la rama académica como prueba de gestión.

## APK y criterios de cierre
- `ci-android`: compila ambos flavors, tests, lint, R8, `apksigner` y `aapt`; sube release candidates **firmados con clave de debug para QA**, no distribución productiva.
- `ci-api`: valida endpoints, autorización, pruebas Ktor/Mongo y el aislamiento FCM.
- Para APK real firmada: workflow manual `android-distribution-signed`, entorno `android-release`, certificado pinneado, keystore privado y versión monotónica; **no** lanzarlo con CI rojo ni presentar un RC de pruebas como release de tienda.
- Mantener sin fusión cualquier PR cuando falle el CI, existan conflictos no resueltos, o falte evidencia de alcance.
- Gates físicos: cuenta QA Admin/Superadmin, prueba de perfil y navegación, MFA válido/denegado, revisión de horario nativo frente a SIII oficial, FCM real en equipo y revocación de acceso.

## Pendientes no resueltos por este PR
- Configuración Firebase específica de Gestión y prueba de dispositivo real.
- API autorizada SIII `schedule:propose`, reconciliación bidireccional, inventario de inscripciones/identidades QA y bitácora administrativa inmutable.
- Revisión funcional de todas las operaciones de Gestión, pantalla Dirección independiente y versión firmada productiva.
