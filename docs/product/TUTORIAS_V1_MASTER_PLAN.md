# Plan maestro — Tutorías V1 (Compañero de Escuela)

> Estado: PLANIFICADO, no implementado. Línea base verificada: main @ c4cb5acfbe13acdf9486387a55790cfe6a76b808 (PR #83, Docente V8), 2026-10-07. No hay PRs de implementación abiertos en el momento de redactar este documento.
>
> Regla: cada PR de implementación nace de main actualizado y se integra únicamente con pruebas reales, sin sustituir evidencia por afirmaciones.

## 1. Objetivo y alcance

Crear un **espacio de Tutor Académico** para docentes designados por control escolar, usando Kotlin / Jetpack Compose (Android), Ktor (API) y MongoDB. El tutor acompaña a los estudiantes asignados: consulta su grupo, revisa solicitudes/justificantes, documenta observaciones y acuerdos, gestiona citas, envía avisos controlados y consulta reportes de fuentes autorizadas. El alcance se determina siempre en el backend por institución, periodo y grupo.

**No es** el rol de padre/madre/tutor legal (pospuesto); tampoco es un reemplazo del SIS/LMS, un chat libre, una nueva asistencia paralela ni un segundo sistema de calificaciones. La API institucional es opcional y debe mostrar estado ausente/degradado sin inventar información.

### Principios obligatorios

1. Mantener la UX de estudiante/docente V8 y reutilizar sus design tokens, componentes, fondo universitario, accesibilidad y densidad. No hacer rediseño global.
2. La fuente de verdad de asistencia y calificaciones permanece en sus dominios; Tutorías solo consulta mediante servicios/consultas autorizadas.
3. Un docente con roles TEACHER + TUTOR elige experiencia; un TUTOR sin TEACHER obtiene un inicio válido; TEACHER_PENDING jamás hereda permisos docentes ni tutoriales por error.
4. El admin asigna/revoca el rol/grupo; el tutor no crea materias, grupos, estudiantes ni clases.
5. El QR de entrada escolar y el QR de clase son evidencia distinta. Aprobar una excusa nunca falsifica QR/presencia ni cambia retrospectivamente registros técnicos.
6. Los comentarios tutoriales son registros con visibilidad explícita, autor, fecha, auditoría, correcciones y política de retención.
7. Ninguna UI de producción se agrega al shell de apps/android/app. Nada huérfano: ruta, DI, ViewModel, repositorio, contrato, tests y pantalla deben estar conectados.
8. El producto debe funcionar sin conexión para lectura cacheada autorizada; cualquier escritura offline usa outbox con identidad, deduplicación, expiración y reconciliación auditada.

## 2. Inventario auditado antes de implementar

| Recurso | Estado observado | Decisión |
| --- | --- | --- |
| shared/contracts/.../UserRole.kt | TUTOR existe y es staff no administrativo | Reutilizar |
| services/api/.../tutoring/TutorAssignmentService.kt | Asignación y scope al grupo; requiere mejoras de identidad/revocación/vigencia | Endurecer |
| services/api/.../tutoring/TutorAssignmentRepository.kt | Mongo/InMemory, lista filtrada por tutor | Evolucionar sin romper datos |
| services/api/.../tutoring/TutoringRoutes.kt | GET /tutoring/assignments, GET /tutoring/me, POST /tutoring/assignments | Conservar, versionar cambios |
| shared/contracts/.../TutoringContracts.kt | Contratos de alcance/asignación básicos | Evolucionar compatiblemente |
| services/api/.../excuses/ExcuseService.kt | Submit/review/list; no valida membresía académica en submit | Corregir antes del piloto |
| services/api/.../excuses/ExcuseRepository.kt | En lista de tutor usa listAll y filtra después | Filtrar en la consulta con paginación |
| services/api/.../excuses/ExcuseRoutes.kt | GET, POST, PATCH de revisión | Reutilizar y endurecer |
| apps/android/core/navigation/.../RoleExperience.kt | Faltan AppExperience.TUTOR y su ruta | Añadir sin alterar las demás experiencias |
| apps/android/feature/profile/.../ActiveExperiencePreferences.kt | Preferencia de experiencia por usuario | Reutilizar; invalidar opción ya no concedida |
| apps/android/feature/attendance/.../TeacherHomeScreen.kt | Inicio docente y reutilización V8 integrados en PR #83 | Conservar |
| services/api/.../application/ApplicationModule.kt | Tutorías/Excusas registradas; grading sync usa UnavailableGradeSyncGateway | No prometer sincronización académica activa |
| docs/architecture/SERVICE_BOUNDARIES_V15.md | Límites de dominios y checklist anti-huérfanos | Cumplimiento obligatorio |

Hallazgos a validar contra el HEAD al comenzar cada PR: identidad real del tutor al asignar; no existe revocación; alumno puede declarar academicGroupId sin verificación de membresía; fecha de justificación sin formato fuerte; referencias a adjuntos sin verificación de propiedad; review requiere concurrencia/bitácora; el tutor no tiene pantalla propia. Los nombres anteriores son rutas de referencia, no una invitación a copiar lógica entre dominios.

## 3. Roles y permisos

| Operación | STUDENT | TEACHER | TUTOR asignado | COORDINATOR | ADMIN / SUPER_ADMIN |
| --- | --- | --- | --- | --- | --- |
| Ver grupos/materias propios | Sí | Sí | Solo grupos tutorados | Según política institucional | Sí |
| Ver ficha académica de tutorado | Solo propia | Solo necesidades docentes | Sí, campos autorizados | Limitado por alcance | Controlado/auditado |
| Solicitar justificación | Solo propia | No | No | No | Vía política excepcional |
| Revisar justificación | No | No por ser TEACHER | Sí, su grupo vigente | Según permiso explícito | Sí |
| Alterar evidencia QR/presencia | No | No por tutoría | **Nunca** | **Nunca** | **Nunca** |
| Calificar / sincronizar notas | No | Solo materia asignada y gateway disponible | **Nunca por rol TUTOR** | No por defecto | No por defecto |
| Crear nota tutorial privada | No | No por TEACHER | Sí, su grupo | Solo escaladas/autorizadas | Acceso justificado |
| Publicar aviso tutorial | No | Solo su canal docente | Solo grupo tutorado | Según política | Sí |
| Asignar/revocar tutor | No | No | No | Solo si se delega explícitamente | Sí |

Las reglas se aplican al recurso, en servidor, aunque se falsifique UI/JWT. Una cuenta con múltiples roles no debe ampliar accidentalmente acceso a datos privados: separar facultades por experiencia y operación. Usar política central de autorización; no depender únicamente de botones deshabilitados.

## 4. Arquitectura objetivo (sin duplicación)

### Backend

- tutoring/assignment: nombramiento, periodo/vigencia, estado y revocación de tutor, alcance de grupo. Validar usuario existente y rol; validar grupo activo y pertenencia a institución.
- tutoring/cases: registro canónico de casos, prioridad manual, responsable, estado, fecha de revisión y cierre. Sin inferir diagnósticos de estudiantes.
- tutoring/notes: comentarios y observaciones; visibilidad enumerada: STUDENT_VISIBLE, TUTOR_INTERNAL, COORDINATION_RESTRICTED y SENSITIVE_RESTRICTED. El acceso se verifica al leer *cada nota*.
- tutoring/appointments: citas, participantes, consentimiento/autorización según política escolar, asistencia y resultados.
- tutoring/communications: avisos dirigidos y solicitudes estructuradas, sin chats bidireccionales libres.
- excuses: propietario de solicitudes, adjuntos, estados y decisiones; integra políticas académicas y exporta resultados administrativos, nunca actualiza evidencia física.
- attendance, presence, classroom, academic, grading, channel: siguen siendo dueños de sus datos; ofrecen query APIs/proyecciones autorizadas. Evitar imports cruzados de Mongo/repositorios concretos.
- devices/notifications: entrega idempotente de eventos sin adjuntar contenido de notas sensibles.

Posibles colecciones: tutor_assignments (evolucionar existente), tutoring_cases, tutoring_notes, tutoring_appointments, tutoring_audit_events, tutor_announcements, tutoring_action_receipts. Documentar índices por institución, periodo, grupo, estudiante y tutor. No copiar estudiantes, horarios ni calificaciones a tablas de tutorías. Si el modelo no tiene entidad institucional explícita, establecer su alcance primero; no simular multitenencia con texto libre.

### Contratos y rutas sugeridos (especificación sujeta a revisión)

- Mantener GET /tutoring/me, GET/POST /tutoring/assignments.
- PATCH /tutoring/assignments/{id}/revoke (admin; revocación idempotente/auditada).
- GET /tutoring/groups/{groupId}/students?cursor=... (roster solo lectura autorizado).
- GET /tutoring/students/{studentId}/overview (perfil mínimo con procedencia y freshness).
- GET/POST /tutoring/cases; GET/PATCH /tutoring/cases/{id}.
- GET/POST /tutoring/cases/{id}/notes; correcciones versionadas, no borrado silencioso.
- GET/POST/PATCH /tutoring/appointments (rango de fechas, exclusión de citas ajenas).
- Reutilizar /excuses y endpoints existentes; ampliar estados, consulta por alcance y acceso a adjuntos autorizados.
- Avisos: preferir evolución del servicio channel/events con tipo TUTOR_GROUP y autorización específica; no clonar chat.

Todos los listados requieren límite/cursor, orden estable y ausencia de campos internos. Las transiciones de estado deben protegerse con versionado y compare-and-set u otra estrategia contra dobles aprobaciones. Respuestas de conflicto deben ser legibles.

### Android

Crear :apps:android:feature:tutoring **solo cuando tenga UI + DI + ruta + pruebas reales**, registrar módulo en settings.gradle.kts. Screens mínimas:
- TutorHome: resumen contextual real (grupo, solicitudes pendientes, citas, acciones).
- TutorGroups: lista de grupos asignados, cambio de grupo, conteo confiable.
- TutorStudentDetail: resumen autorizado, historial mínimo, sin datos sensibles por defecto.
- TutorFollowUp: casos, comentarios, acuerdos y estado.
- TutorRequests: lista/detalle/revisión de justificantes y sus documentos.
- TutorAppointments: calendario/lista y reserva.
- TutorNotices: avisos oficiales del grupo + confirmación de recepción.

Navegación sugerida: 4 destinos de primer nivel (Inicio, Mi grupo, Seguimiento, Solicitudes); citas, avisos y perfil como acciones contextuales. Nunca añadir siete pestañas inferiores. Conservar V8, tamaños compactos, safe insets, contraste, modo oscuro/claro, fuentes grandes y animaciones reducidas.

Registrar AppExperience.TUTOR en RoleExperienceResolver y Destination; testear cuentas solo TUTOR, TEACHER+TUTOR, STUDENT+TEACHER+TUTOR, TEACHER_PENDING y revocación durante sesión. La preferencia activa nunca reemplaza validaciones de backend.

## 5. Trabajo en PRs secuenciales

### PR 0 — Base y contrato de ejecución (documental)

Rama: docs/tutorias-v1-master-plan.
- [x] Inventario y dependencias verificadas sobre main.
- [x] Alcance, servicios propietarios, flujo Git y criterios de aceptación.
- [ ] Abrir PR, enlazar issue épico y fusionar documentación tras revisión.
**Salida**: plan revisable; no presentar como funcionalidad implementada.

### PR 1 — Autorización + ciclo de vida de asignaciones

Rama: feat/tutoring-v1-scope.
- [ ] Verificación de usuario/rol real y pertenencia institucional, grupo/periodo activo.
- [ ] Asignaciones con start/end, revocación, historial y restricciones únicas; migrar documentos existentes sin pérdida.
- [ ] Alcance central e invalidación de acceso tras revocar.
- [ ] GET /tutoring/me con estados vacíos, y pruebas de filtración cross-group/cross-institution.
- [ ] Tests para admin/tutor/teacher-pending/multirrol, carrera de asignación duplicada y revocación.
**DoD**: ni UI adulterada ni llamada directa API permiten acceso a grupo ajeno; endpoints existentes compatibles.

### PR 2 — Justificantes seguros y semántica de asistencia

Rama: fix/tutoring-v1-excuses.
- [ ] Validar matrícula de estudiante en grupo en fecha solicitada, fecha ISO real, límites de texto/adjuntos y duplicados.
- [ ] Validar existencia, propiedad, MIME, tamaño y permisos de descarga de adjuntos; no confiar en attachmentRefs del cliente.
- [ ] Implementar listados por grupo/tutor directamente en repositorio con cursor/paginación.
- [ ] Estado adicional NEEDS_INFO si UX lo requiere; transiciones y causas explícitas.
- [ ] Revisiones idempotentes, protección de concurrencia y auditoría append-only.
- [ ] Propagar resolución a estado *administrativo* de falta sin mutar SchoolPresence/QR/ClassAttendance evidence.
- [ ] Pruebas de intentos falsificados, fecha/matrícula, documentos no propios, doble decisión y cancelación.
**DoD**: se puede seguir expediente de justificación sin inventar presencia física.

### PR 3 — Experiencia Tutor V8 en Android (MVP visual)

Rama: feat/tutoring-v1-android-shell.
- [ ] Agregar experiencia TUTOR, destinos y conmutación docente/tutor sin menú duplicado.
- [ ] Nuevo módulo feature:tutoring real con repositorios/Hilt/ViewModels/API y estados cargando/vacío/offline/error.
- [ ] TutorHome, TutorGroups, TutorRequests y detalles mínimos conectados a APIs, no mocks.
- [ ] Reusar fondo universitario, brillo moderado, encabezados, tarjetas, motion y tipografía V8.
- [ ] Validar 360/390/430 dp, tablet, navegación de atrás, sistema de fuentes/contraste.
- [ ] Capturas reales del emulador y capturas de referencia con comparación explícita; no usar renders simulados como evidencia.
**DoD**: un tutor exclusivo y un docente+tutor pueden operar el flujo real desde login hasta solicitud/revisión con permisos correctos.

### PR 4 — Expediente tutorial, notas, acuerdos y derivaciones

Rama: feat/tutoring-v1-followup.
- [ ] API de cases/notes con alcance, auditoría y estado OPEN, IN_PROGRESS, REFERRED, CLOSED.
- [ ] Comentarios STUDENT_VISIBLE, TUTOR_INTERNAL, COORDINATION_RESTRICTED, SENSITIVE_RESTRICTED; separar datos sensibles y limitar caches.
- [ ] Alumno visualiza únicamente información dirigida y publicada a él; no notas internas.
- [ ] Acuerdos/responsables/plazo; derivación solo al área asignada y registro de aceptación.
- [ ] UI de seguimiento y ficha de estudiante conectadas con accesibilidad.
**DoD**: lectura/escritura protegidas por objeto y reglas de visibilidad, sin filtraciones por multirrol.

### PR 5 — Citas y comunicación tutorial controlada

Rama: feat/tutoring-v1-appointments-notices.
- [ ] Agendar/reprogramar/cancelar citas y dejar resultado.
- [ ] Solicitud de atención del estudiante con categorías predeterminadas (sin chat libre).
- [ ] Reutilizar canales/eventos para comunicados del tutor, con audiencia autorizada, historial y confirmación de lectura cuando el servicio lo permita.
- [ ] Notificaciones con preferencias, quiet hours, deduplicación y contenido no sensible.
- [ ] Outbox de acciones permitidas offline; nunca afirmar envío hasta recibir ack del servidor.
**DoD**: avisos y solicitudes trazables, sin duplicación ni filtraciones al grupo equivocado.

### PR 6 — Reportes, integraciones opcionales y cierre RC

Rama: feat/tutoring-v1-reports-rc.
- [ ] Reportes por grupo y periodo: asistencia *administrativa*, solicitudes, citas, seguimientos y acuerdos.
- [ ] Solo lectura de calificaciones autorizadas; si GradeSyncGateway real sigue ausente, mostrar “Sin integración escolar”, nunca datos ficticios.
- [ ] Proveniencia y fecha de última actualización para métricas externas/cache.
- [ ] Exportaciones minimizadas y autorizadas; eliminar identificadores personales cuando el reporte no los requiera.
- [ ] Matriz de roles, regresión alumno/docente/admin, pruebas end-to-end, Android APK, documentación, evidencia de emulador y prueba física.
**DoD**: candidate de APK integrada en main con CI verde y checklist anti-huérfanos completa; pruebas físicas documentadas por separado.

## 6. Máquina de estados de solicitudes y casos

**Justificante**:
PENDING → UNDER_REVIEW → APPROVED | REJECTED | NEEDS_INFO.
NEEDS_INFO → UNDER_REVIEW | CANCELLED.
PENDING → CANCELLED solo por solicitante si la política permite.
Terminales: APPROVED, REJECTED, CANCELLED. Un error o corrección posterior produce evento/reapertura explícita, no reescritura silenciosa.

**Caso tutorial**:
OPEN → IN_PROGRESS → REFERRED | CLOSED.
REFERRED → IN_PROGRESS | CLOSED según autorización y evidencia.
Correcciones siempre versionadas.

**Cita**:
REQUESTED → CONFIRMED → COMPLETED | CANCELLED | NO_SHOW.
No puede completarse una cita de otro tutor. La gestión de permisos depende de asignación vigente, fecha y tipo de información.

## 7. Pruebas obligatorias y seguridad

**Autorización**: alumno A no puede leer expediente B; tutor A no puede leer ni operar grupo B; docente sin tutor no ve expedientes; tutor revocado pierde acceso inmediatamente; admin con acceso global conserva auditoría de consulta; roles combinados no saltan límites. Probar también IDOR mediante IDs directos y filtros forzados.

**Datos**: auditoría de autor/fecha/cambio, índices compuestos para performance, migración de asignaciones antiguas, paginación estable, idempotencia de submissions y reviews, race conditions, estados fuera de orden, periodos escolares, sanitización de texto/archivos y retención.

**Integridad académica**: falta justificada distinta de “presente”; original QR permanece inmutable; ausencia de proveedor académico no produce calificaciones o asistencia inventada; editor de grades y teacher roster no se degradan.

**Android**: identidad en caché por usuario, logout sin fuga de datos, conexión intermitente, reintentos, restablecimiento del proceso, accesibilidad semántica, back navigation, scroll y overlaps en 360/390/430/768/1024 dp, modo oscuro/claro y capturas reales de emulador. No usar captura visual simulada como validación.

**Matriz**: por cada feature agregar test unitario dominio, test de policy, tests de rutas API, repositorio (InMemory + Mongo cuando disponible), tests ViewModel, UI/smoke/instrumentation y verificación manual física.

## 8. Gate de Git / CI y evidencia exigida

Comandos de preparación Windows:

    git switch main
    git fetch origin
    git pull --ff-only origin main
    git status --short
    git rev-parse HEAD
    git switch -c feat/tutoring-v1-scope

Al completar cada PR, ejecutar lo que aplique:

    .\gradlew.bat :services:api:test
    .\gradlew.bat :apps:android:core:navigation:test
    .\gradlew.bat :apps:android:app:testDebugUnitTest
    .\gradlew.bat :apps:android:app:assembleDebug
    .\gradlew.bat lint

También comprobar release APK / R8 e instrumentación en emulador en el gate RC. Usar los nombres de tareas realmente disponibles: si algún task no existe, descubrirlo en Gradle y documentar el reemplazo; no declarar PASSED por ausencia de ejecución. No introducir credenciales de API escolar, FCM ni datos reales de alumnos en repositorio o evidencia.

Para integrar: PR contra main actualizado; revisión de diff y CI; resolver conflictos en la rama del PR sin alterar lógica ajena; squash merge solo si todos los gates relevantes pasan. Tras merge obtener SHA de main, actualizar línea base de siguiente PR; limpiar ramas obsoletas luego de validar. Evitar force-push a main y PRs paralelos sobre los mismos archivos de auth, navegación o attendance.

Checklist de cada PR:
- [ ] Un dominio principal; compatibilidad contractual/migración.
- [ ] Rutas registradas, permisos con pruebas negativas.
- [ ] Vista alcanzable, ViewModel/repositorio/DI conectados; sin placeholders.
- [ ] Test suite realmente ejecutada + resultado + versiones/entorno.
- [ ] UX V8 coherente, accesibilidad, loading/empty/offline/error.
- [ ] Sin información de alumnos/secretos en logs, capturas o tests.
- [ ] Docs de endpoints/arquitectura actualizadas.
- [ ] No duplicados, archivos huérfanos ni servicios que se sobrepongan.
- [ ] Screenshot real Android y confirmación de lo que NO se probó.
- [ ] Head/main SHAs y enlace al PR.

## 9. Decisiones de negocio para implementar sin bloquear el MVP

Defaults propuestos, sujetos a reglamento institucional:
- Tutor académico = personal escolar asignado; tutor legal/familiar queda fuera del V1.
- Un grupo puede tener tutor titular y suplente solo con autorización, fecha y alcance explícitos.
- Vistas de calificaciones para tutor: lectura autorizada; nunca edición por rol TUTOR.
- Justificaciones afectan reporte administrativo, no registros de entrada/clase ni validación QR.
- Citas por horario de atención y reglas institucionales; no mensajes privados sin control.
- Intervenciones/alertas son para seguimiento humano, sin predicción clínica ni etiquetas personales.
- Evitar subir documentos confidenciales hasta que exista un sistema de archivos seguro y una política institucional aprobada.
- Reportes sin acceso a expedientes sensibles salvo facultad expresa.

## 10. Definition of Done final

La iniciativa no está cerrada hasta comprobar sobre main:
1. Admin asigna/revoca tutor por grupo y periodo, con identidad validada.
2. Cuenta TUTOR exclusiva entra y navega; TEACHER+TUTOR alterna sin romper Docente V8.
3. Roster y expedientes solo de grupos vigentes; ninguna fuga cross-account/cross-group.
4. Alumno envía una justificación válida; tutor la revisa; ambos ven estado y auditoría.
5. Aprobar justificación conserva asistencia física/QR y diferencia administrativo vs técnico.
6. Tutor genera nota y acuerdo; controles de visibilidad y derivación se cumplen.
7. Citas, avisos y respuestas limitadas funcionan con confirmación e idempotencia.
8. Modo sin conexión y errores muestran información honesta y no filtran cache al cambiar de usuario.
9. Gradle API/Android + lint + instrumentación + emulador: evidencia real, con bloqueos declarados.
10. Un APK RC construido desde SHA de main actualizado, enlaces a PRs y ninguna deuda crítica de seguridad abierta.

## 11. Referencias internas

- docs/architecture/SERVICE_BOUNDARIES_V15.md
- docs/product/MASTER_PLAN.md
- docs/product/UI_V5_3_ACCEPTANCE.md
- docs/ui/V8-RED-EDITION-VISUAL-CONTRACT.md
- services/api/src/main/kotlin/org/companerodeescuela/api/tutoring/
- services/api/src/main/kotlin/org/companerodeescuela/api/excuses/
- apps/android/core/navigation/src/main/kotlin/org/companerodeescuela/core/navigation/RoleExperience.kt
- apps/android/feature/attendance/src/main/kotlin/org/companerodeescuela/feature/attendance/TeacherHomeScreen.kt
- Investigación previa proporcionada por el usuario: revisión de UNAM, PowerSchool, EduPage, ParentSquare, Google Classroom y Canvas. Sus funciones inspiran diseño, no prueban integración actual.

---

Este documento es un plan de trabajo ejecutable y auditado, **no una declaración de funcionalidades ya construidas**, y debe seguir actualizándose con los resultados verificables de los PRs.