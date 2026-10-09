# Plan maestro V10 — Compañero de Clase: plataforma académica integral

> Estado: PLAN VERSIONADO; **no es implementación funcional**. Fecha: 2026-10-08 (México). Baseline verificada de `main`: `841c8a31d891090a5aba593de9509664bf17d01b`. Responsables funcionales: administración institucional; responsables técnicos: contratos compartidos, API Ktor, Android Compose. Revisar SHA/CI en cada PR. El documento V1 de Tutorías y algunos documentos de fundación representan cortes históricos y no describen por completo el HEAD actual.

## 1. Resultado que se persigue

Una sola plataforma con experiencias ESTUDIANTE, DOCENTE, TUTOR ACADÉMICO, COORDINACIÓN, ADMIN INSTITUCIONAL, SUPER ADMIN y FACILITADOR INVITADO, cada una con rutas, alcance de datos, acciones, estados, QA y evidencia. También debe poder inscribir participantes de talleres que no sean estudiantes institucionales, sin otorgarles permisos docentes ni académicos.

**No** se crea una nueva app por rol, otro backend, un LMS paralelo, ni una navegación/tema nuevo. Kotlin + Jetpack Compose + Hilt + ViewModel/StateFlow + Ktor + MongoDB, con interfaces opcionales de integración escolar; se reutilizan módulos existentes.

## 2. Inventario de la línea base

| Dominio | Evidencia en main | Faltante para cierre |
| --- | --- | --- |
| Auth/roles | `shared/contracts/UserRole.kt`; login/registro nativo; experiencia multirrol | Invitaciones externas, elevación/revocación de administradores, permisos por recurso, control de sesión al revocar |
| Tutor | `feature:tutoring` (Home, Groups, Requests, ViewModel, DI); `tutoring` assignments/cases/excuses | Detalle de alumno, seguimiento completo, citas, avisos, reportes, accesibilidad, QA físico y privacidad |
| Admin | `AdminHomeScreen`, servicios de grupos, clases, horarios, tutores, asistencia/avisos | Gestión de usuarios/roles/aprobaciones; tablero real; cohortes/periodos; auditoría; operaciones masivas; consola |
| Super Admin | `SUPER_ADMIN` y `AppExperience.SUPER_ADMIN` | Experiencia y acciones propias de seguridad/configuración; no heredar sin límites datos estudiantiles |
| Coordinación | Pantalla de coordinación y permisos administrativos generales | Delegaciones y alcance explícito, reportes/agendas por dependencia |
| Docente | V8.1 integrada: Home, clases asignadas, asistencia, evaluación, canal | Cierre de regresiones V8, sincronización escolar real opcional, dispositivos y errores |
| Invitado facilitador | No existe `GUEST_INSTRUCTOR` en `UserRole` ni experiencias | Dominio Workshop/Event, propuestas, invitación, alcance, publicación, participantes, recursos, certificados |
| Visual V8 | `core:designsystem/v8`, `core:motion`, `core:navigation`, `core:ui` | Expandir por composición y pruebas; ningún rediseño global |
| Release | `apps/android/app/build.gradle.kts`: `versionName=0.1.0`, `versionCode=1` | Estrategia de versiones, RC y APK trazable |

**Hallazgo P0:** `AdminHomeScreen` contiene métricas de presentación fijas (Servicios: OK, Sesiones: Activas, Auditoría: 0 alertas); prohibido tratarlas como indicadores reales. Revisar también conflictos HTTP 409 de asistencia, horarios y ausencia de roster, sin cambiar semántica del primer filtro QR de entrada ni del segundo pase por clase.

## 3. Contratos innegociables

1. Un único diseño V8 Red Edition: `V8FrostedGlassPanel`, `V8GlassCard`, `V8CampusBackdrop`, `V8ScreenHeader`, tokens de `core:designsystem`, `CompaneroScaffold`, motion reducido y accesibilidad. Ajustar composición, no duplicar componentes ni librerías.
2. Identidad autenticada y rol NO bastan: exigir institución, recurso, asignación, periodo, vigencia y operación en backend. Denegación por defecto; verificar cada request. No confiar en el cambio de experiencia Android ni en IDs que lleguen del cliente.
3. Cada módulo posee sus datos. Tutorías consulta asistencia/calificaciones mediante servicios autorizados y no muta evidencia; talleres usan registros propios y nunca pasan lista escolar automáticamente; certificaciones de talleres no alteran créditos oficiales.
4. El admin asigna grupos/clases y autoriza tutores. Docentes no crean grupos institucionales; invitado no puede crear asignaciones institucionales, notas oficiales, presencia QR escolar, ni leer expedientes de alumnos por defecto.
5. Cuenta propia con contraseña funciona sin API escolar. Sin integración institucional, no simular matrículas, calificaciones, horarios ni identidad verificada. La sincronización escolar conserva su carácter opcional y el estado visible.
6. No permitir métricas falsas, botones sin acción, pantallas huérfanas, permisos visuales sin protección de API, secretos en capturas ni logs con datos personales.
7. Soportar 360/390/430dp, 768/1024, fuentes 200%, TalkBack, teclado, estados vacío/loading/error/offline/revocación y back navigation. QA con evidencia de emulador y al menos un teléfono físico.

## 4. Experiencias de producto y navegación

### 4.1 Estudiante (conservar)
Hoy, Horario, QR (entrada escolar separada de pase de aula), Mis clases, Perfil. Participa en talleres por inscripción opcional; ve avisos/canal y sus resultados, nunca datos de otros estudiantes. Añadir catálogo de eventos como destino secundario, no quinta/séptima tab.

### 4.2 Docente (completar)
Hoy docente, Horario, Asistencia, Clases, Canal. Evaluación se abre desde clase o una acción contextual. Solo materias/grupos asignados, pase por ocurrencia, retroalimentación y XLSX/CSV con validación. Separar las reglas de retardo/justificación del estado físico del QR. No reconstruir V8.1.

### 4.3 Tutor académico (completar V1)
Inicio, Mis grupos, Seguimiento, Solicitudes (máximo 4 tabs; Perfil contextual). Ficha autorizada con procedencia, casos y notas de visibilidad (STUDENT_VISIBLE, TUTOR_INTERNAL, COORDINATION_RESTRICTED, SENSITIVE_RESTRICTED), citas, acuerdos, avisos y reportes por grupo/periodo. Asignación/revocación administrada por autoridad. Tutor no cambia notas, no inventa asistencias, no revela notas internas al estudiante. Tutor legal/familiar fuera de este alcance.

### 4.4 Administrador institucional
Resumen **real** con health y provenance; Personas y acceso; Grupos/periodos/docentes/aulas; Horarios con importación PDF y revisión; asignación de grupos; tutorías; QR de acceso y controles de vigencia; asistencia institucional y de aula; incidencias/justificantes; avisos; propuestas de cursos/talleres y asignación de facilitadores; exportaciones auditadas. Menú adaptativo por secciones: máximo 4–5 tabs operativas, herramientas secundarias bajo Gestión.

### 4.5 Super Admin
Panel de configuración institucional y seguridad independiente del AdminHome (aunque use componentes V8): organizaciones/instituciones si existen y están correctamente aisladas; creación de admin institucional mediante proceso seguro; permisos/delegaciones; integraciones API; políticas de retención; auditoría, revisiones de acceso y estado del servicio; políticas de despliegue. MFA/step-up para cambios sensibles, doble control en acciones críticas y eventos auditables. **No equivale a acceso indiscriminado a expedientes privados**.

### 4.6 Coordinación
Visibilidad delimitada por programa/periodo/grupo autorizado; seguimiento de grupos, asignación delegada según política, incidencias y reportes agregados. No es Super Admin ni puede autorizarse a sí misma.

### 4.7 Facilitador invitado (nuevo)
Un profesional externo propone o acepta impartir CURSO, TALLER, CONFERENCIA o SESIÓN ESPECIAL. Accede únicamente a **sus eventos aprobados** y participantes inscritos autorizados; agenda, materiales, avisos, participación propia y certificados si el admin delegó emisión. **No** hereda `TEACHER` ni `ADMIN`. La cuenta de visitante solo-lectura y la cuenta de participante externo son experiencias distintas del facilitador. Véase el contrato de invitaciones V10 para seguridad, API y ciclo de vida.

## 5. Alcance funcional del invitado: recorrido completo

1. **Propuesta**: persona externa envía nombre, datos mínimos/contacto, perfil público verificable, descripción, objetivo, modalidad, audiencia, duración, cupo, requisitos, recursos, necesidades de sala/streaming y fechas; o admin prepara propuesta e invita directamente.
2. **Revisión**: Admin revisa identidad e idoneidad, privacidad, materiales y disponibilidad. Puede REQUERIR_CAMBIOS, APROBAR o RECHAZAR con motivo y auditoría.
3. **Invitación**: token opaco aleatorio de alta entropía, de un solo uso, almacenado con hash, caducidad corta, límite de intentos y revocación. Vinculado a una cuenta individual y evento/rol. Confirmar correo antes de aceptar; no compartir contraseñas.
4. **Activación**: el facilitador recibe `GUEST_INSTRUCTOR` solo con una asignación vigente a evento específico. No escalar a roles institucionales. Permisos reevaluados por API al usar el recurso.
5. **Publicación**: administrador aprueba cupo, visibilidad PUBLICO / SOLO_CAMPUS / POR_INVITACION, tipo, disponibilidad y fechas. Publica catálogo; notificaciones existentes del canal/eventos, sin duplicar chat.
6. **Inscripción**: estudiantes con cuenta existente; externos con registro mínimo verificable y consentimiento/aviso de privacidad. Cupo, waitlist, cancelación, fecha límite y prevención de inscripciones duplicadas.
7. **Ejecución**: agenda por sesiones, recursos, avisos moderados, encuestas o actividades simples opcionales; control de asistencia de taller propio, QR/evento distinto al QR institucional y con validez delimitada. No conceder automáticamente presencia académica.
8. **Cierre**: facilitador marca sesión completada; admin verifica requisitos y autoriza constancias. Certificado de **participación/aprovechamiento del evento**, no equivalencia académica automática. Verificador público expone datos mínimos y estado, con revocación/auditoría.
9. **Expiración**: vence el grant y acceso a roster, tokens/recomendaciones/offline cache del invitado. Materiales siguen política de conservación institucional. Reasignación requiere nueva autorización.

### Pantallas invitado V10 (Compose, sin nuevo tema)
- Inbox de invitación / alta y verificación; estados pendiente/aprobado/rechazado/revocado.
- Inicio: próximo evento, estado de solicitud, asistentes, tareas y avisos.
- Mis eventos: propuesta, edición de borradores, sesiones, recursos, asistentes **mínimos** y publicación.
- Sesión: agenda, QR de evento, seguimiento y moderación, sin acceso al pase escolar.
- Materiales y avisos; constancias pendientes de autorización; Perfil. Máximo 3–4 tabs principales, resto secundarios.
- Participante externo: catálogo, inscripción, Mis talleres, materiales habilitados y constancias, sin funciones académicas institucionales.

## 6. Investigación externa que guía las decisiones

- Canvas LMS diferencia roles por cuenta (admin) y por curso: https://community.instructure.com/en/kb/articles/661569-how-do-i-add-a-new-user-role-in-canvas
- Moodle Guest es lectura; Non-editing teacher restringe edición y puede limitarse a grupos. Un invitado que enseña necesita permisos más finos que guest read-only: https://docs.moodle.org/503/en/Guest_role y https://docs.moodle.org/502/en/Non-editing_teacher_role
- OWASP Authorization: deny by default, menor privilegio y comprobación en cada petición: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- 1EdTech LTI 1.3 / Advantage facilita interoperabilidad de contenido/roles/grades si una institución lo requiere; **no es dependencia del MVP**: https://www.1edtech.org/standards/lti
- Open Badges 3.0 da formato portable/verificable a logros y certificados; evaluar fase futura, no declarar soporte actual: https://www.1edtech.org/standards/open-badges

## 7. Arquitectura de implementación

`shared/contracts` define DTOs versionables de alcance, invitación, propuesta, inscripción, sesiones, materiales, auditoría; `services/api` posee casos de uso, repositorios e índices Mongo; `apps/android/feature/<dominio>` conserva UI/ViewModel/repositorios inyectados; `core:designsystem`, `core:navigation` y `core:motion` son comunes.

- Crear `services/api/.../authorization/PermissionPolicy` central; no copiar verificaciones ad hoc en controladores.
- Crear `services/api/.../workshops` (propuestas/invitaciones/eventos/sesiones/inscripciones/materiales/constancias) y `apps/android/feature/workshops` solo cuando existan DI, rutas y pruebas conectadas.
- Tablas/colecciones sugeridas con índices únicos de grant, token hash e inscripción; `institutionId` y `eventId` siempre tipados. Si la plataforma aún carece de modelo institucional firme, agregarlo con migración segura antes de exponer más de una institución.
- Reutilizar servicios existentes de canales, notificaciones, auth nativo, QR criptográfico **como infraestructura/patrones**, sin mezclar IDs de evento y asistencia institucional ni duplicar repositorios escolares.
- E2E: servidor autoriza por principal vigente + permiso explícito + scope. Ni roles de JWT obsoletos ni tabs seleccionadas pueden sostener acceso tras revocar; coordinar invalidación/reconsulta de grants y caches.
- No mezclar data class persistente con DTO público; contratos backward-compatible o nuevas rutas `/v2` donde haya cambios incompatibles.
- Upload de recursos seguro (tipo/tamaño, análisis, propiedad y descarga autorizada), límites de cuota y registro de actividades.

## 8. Entregas por fases, dependencias y gates

| Fase | Prioridad | Hito verificable | Gate |
| --- | --- | --- | --- |
| F0: consolidación | P0 | baseline, matriz V8 de reuso, Git/CI, limpiar docs históricos, cerrar navegación/QR 409/horario Sábado y health del piloto | suite actual verde + APK instalada + cero botones falsos |
| F1: seguridad núcleo | P0 | permisos backend por recurso, grants vigentes, delegación/revocación, sesión al revocar, auditoría y control de acceso entre roles | pruebas negativas de lectura/escritura y pertenencia |
| F2: Tutor V10 | P1 | expediente, citas, avisos, justificantes, reportes; alcance existente reutilizado | ciclo alumno→tutor→revisión→respuesta visible, auditoría |
| F3: Admin V10 | P1 | gestión de personas, grupos, horarios, tutores, QR, aprobación de facilitadores; métricas reales | admin institucional opera sin intervención directa en DB |
| F4: Super Admin | P1 | permisos, instituciones, políticas, integraciones, auditoría, MFA sensible | ninguna fuga cross-role/cross-institution |
| F5: invitaciones invitado | P1 | contratos, propuesta, verificación, aprobación, grants, expiración | invitado no lee ni modifica aulas/notes/QR escolares |
| F6: cursos/talleres MVP | P1 | catálogo, evento, inscripción, sesiones, contenidos, avisos, asistencia de EVENTO | recorrido de propuesta a curso concluido |
| F7: constancias e interoperabilidad | P2 | verificación/revocación de constancias, posible Open Badges/LTI opcional | certificación no suplantable ni créditos falsos |
| F8: cierre release | P0 | e2e, regresión todos roles, offline, visual, accesibilidad, benchmark, seguridad, APK RC | evidencia versionada + rollback + piloto real autorizado |

Se pueden adelantar diseños en paralelo; F5 depende de F1 y de aprobación en F3, F6 depende de F5. Tutor puede evolucionar sobre F1 sin esperar talleres. F8 se ejecuta también incrementalmente por PR, no solo al final.

## 9. Git y versionado

- Base: `main` verificada por SHA. Crear `docs/v10-master-roles-guest-20261008` para documentos y epic, después PR pequeño a main. No mezclar PR de documentos con cambios enormes de producción.
- Ramas secuenciales propuestas: `fix/v10-release-gates`, `feat/v10-authz-scopes`, `feat/v10-tutor-complete`, `feat/v10-admin-operations`, `feat/v10-superadmin-control`, `feat/v10-guest-invitations`, `feat/v10-workshops-core`, `feat/v10-workshops-android`, `feat/v10-event-certificates`, `test/v10-rc-e2e`.
- Cada PR incluye alcance y propietario, migración, contrato API, UI conectada, tests negativos, capturas reales, métricas/no datos falsos, Git SHAs, impacto a otros roles y plan de rollback; squash merge tras checks verdes y revisión de solapamientos. Nunca mergear todos los PR sin auditar.
- Versionado SemVer desde el existente `0.1.0`; **V10 es nombre del plan/experiencia, no la versión 10 del binario**. Proponer `0.2.0-alpha.1` al empezar nuevas capacidades, `0.2.0-rc.1` tras gates y `0.2.0` para el primer release funcional verificado. Actualizar `versionCode` monotónico por APK publicada, `CHANGELOG.md`, tag anotado solo al cerrar el release y evidencia SHA-256; no subir versión/tag solo por plan.
- Branch protection deseable: PR requerido, API tests, Android compile/unit/lint/instrumentation/release, secret scan, contrato/DB, dependencias, aprobación humana de release.
- Sin GitHub Actions por facturación: cadena local equivalente con hashes/logs y declaración de CI remota pendiente, no marcar verde por suposición.

## 10. Definition of Done por vertical

- Autorización: matriz server-side testada con 401/403/404 según política; prueba IDOR horizontal y vertical; revocación efectivamente corta acceso.
- Integridad: idempotencia, compare-and-set de decisiones, migraciones, índices, auditoría append-only, datos mínimos y retención configurable.
- UX: todos los botones realizan acción, ningún número hardcodeado como real, buen contraste/font 200%, back/rotación/restauración, paginación de listas, overlays sin bloquear toques.
- Redes: 0 usuarios/clases/eventos, 500 participantes, offline/datos cacheados etiquetados, reintentos limitados, pérdida de sesión, duplicados y multi-dispositivo.
- Release: `main` verde, artefactos identificados por SHA/versionCode, pruebas físicas más emulador, demo end-to-end con roles segregados, evidencia de permisos rechazados.

## 11. Riesgos P0 que no deben perderse

1. Escalada de privilegios por rol de JWT viejo: refresh/revalidación al cambiar asignación; nunca conceder ADMIN/SUPER_ADMIN por registro público.
2. Datos sensibles tutoriales y de menores/extranjeros: mínimo necesario, consentimiento/aviso, cifrado y política de acceso; ninguna exposición en invitaciones ni QR.
3. Admin muestra métricas mock: reemplazar por queries reales y estados desconocidos cuando API esté caída.
4. Attendance 409: UI debe distinguir pase cerrado, expirado, conflicto y ausencia de registros; no permitir abrir sesión desde botón incorrecto y no inventar `0` como prueba de asistencia.
5. Dos filtros de presencia: QR escolar y pase docente siguen independientes; QR de TALLER tiene tercer namespace/eventId independiente.
6. Escuela sin integración: utilizar cuenta nativa y datos propios, no crear falsa sincronización de calificaciones oficiales.
7. Estado visual V8: no considerar pantalla terminada solo por componentes compartidos o porque una suite de login pasa.
