# Plan maestro V17 — Dos APK, perfiles, permisos y sincronización SIII

**Fecha:** 2026-10-21 · **Estado:** ejecución incremental, no liberación de producción.
**Baseline auditado:** Compañero `main@5ad15061903ff21a308043177abbe0336285f221`; SIII `main@e494f651b011d8567f999eac0a4103b0eac01c76`.

## 0. Decisión de producto y límites

Entregar dos APK Android **instalables simultáneamente**, con diferentes `applicationId`, nombre, icono, entrada de login y navegación, pero **una identidad y un backend institucional**.

| Aplicación | Perfiles activos | Responsabilidad |
| --- | --- | --- |
| **Compañero de Escuela** (académica) | Alumno, Docente, Tutor académico | Hoy, horario/agenda, asistencia QR, clases, canal cerrado, tutorías, avisos, notificaciones, expediente autorizado |
| **Compañero Gestión** (administrativa) | Dirección, Administrador, Superadministrador | Importar y publicar horarios/listas, grupos, personal, tutorías, delegaciones, avisos, conciliación SIII, auditoría y gobierno institucional |

**Jefe/subjefe de grupo** es un nombramiento delegado del alumno, restringido a grupo, periodo, estado y vigencia; no se convierte en `ADMIN`. **Coordinación** ya existe como rol técnico `COORDINATOR`, pero **Dirección no está representada en `UserRole` hoy**. No etiquetar Coordinación como Dirección ni concederle privilegios administrativos generales: incorporar una autoridad/capacidad específica de Dirección con migración de contratos, identidad y pruebas. `GUEST`/tallerista se conserva en el contrato heredado, pero no se introduce silenciosamente en las dos nuevas APK; definir producto/flujo por separado.

**SIII** conserva autoridad de matrículas, inscripciones, grupos, docentes, asignaciones, horarios institucionales y calificaciones oficiales. **Ktor + MongoDB** conserva identidad/proyección móvil autorizada, asistencia y evidencias QR, tutorías/canales propios, inbox/outbox e historial operacional. Toda escritura académica oficial debe confirmar su operación mediante API de SIII o quedar como **propuesta pendiente**: nunca marcar como publicada por modificar únicamente Mongo/Room. Nunca compartir tokens institucionales con móviles ni escribir directamente en Supabase desde Android.

## 1. Estado de código encontrado (no darlo por terminado)

- Solo existe el módulo instalable `:apps:android:app`, con un `applicationId`. Gradle y CI actuales esperan `app-debug.apk` y `app-release.apk`; activar flavors sin migrar workflows rompe empaquetado y firmas.
- `UserRole` contiene `STUDENT, TEACHER_PENDING, TEACHER, TUTOR, COORDINATOR, ADMIN, SUPER_ADMIN`, más invitado/taller; falta Dirección independiente.
- `RoleExperienceResolver` ya navega por perfiles. `AppDistribution` introduce aquí el **filtro de experiencias de presentación** con comportamiento legado por defecto, sin afirmar segregación efectiva de las APK.
- `AdminCapability` actual: `APPROVE_STAFF, MANAGE_USERS, MANAGE_ACADEMICS, REVIEW_WORKSHOPS, VIEW_AUDIT, VIEW_SESSIONS`. Debe evolucionar a acciones con recurso/alcance verificadas en backend, no solo booleanos UI.
- Existen repositorios/servicios reales para tutorías, representantes, canal cerrado, edición de horario con MFA, importación PDF en Android y proyecciones SIII. Hay pruebas de navegación por los seis perfiles actuales. Reutilizarlos; no reconstruir pantallas ni reimplementar asistencia.
- Pendientes visibles: PRs abiertos de UX/tema y bootstrap del piloto; evidencia end-to-end SIII ↔ Ktor ↔ dos móviles insuficiente, PDF físico de 9.º A pendiente, API35/CI variable. SIII PR #71 documenta un límite de migraciones de producción previo a 0018/outbox; **NO** aplicar SQL sin preflight/revisión/backup.

## 2. Matriz funcional objetivo (rol + alcance + autorización de recurso)

Leyenda: `P` propio; `G` grupo/curso asignado vigente; `I` institución vigente; `D` delegación excepcional documentada; `X` plataforma con step-up; `—` denegado.

| Capacidad | Alumno | Docente | Tutor | Jefe/subjefe | Dirección | Admin | Superadmin |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Consultar horario | P | G | G lectura | G como alumno | I lectura | I gestión | D soporte |
| Registrar/confirmar asistencia QR | P propia | G sesión | G revisión autorizada | — | I métricas | I contingencias aprobadas | — |
| Canal: leer publicaciones | G | G | G | G | I | I | D |
| Canal: publicar aviso académico | — | G | G si delegación | G solo comunicados representativos aprobados | I | I | D |
| Consultar alumnos | — | G mínimos | G asignados | — | I con finalidad | I con finalidad | D soporte auditable |
| Publicar horario institucional | — | propuesta | propuesta | — | aprobar/I según delegación | I mediante SIII | D con MFA y doble control |
| Importar PDF de listas/horarios | propio local | propuesta de curso | propuesta del grupo | — | revisión/I | importar-validar-confirmar/I | recuperación delegada |
| Asignar/revocar tutor | — | — | — | — | aprobar/I | I con MFA | D con MFA |
| Nombrar/revocar jefe/subjefe | aceptar propio | — | G si delegado | aceptar/rechazar propio | supervisión/I | I si delegado | D |
| Gestionar identidad/roles | perfil propio | perfil propio | perfil propio | perfil propio | consulta/I | roles no privilegiados/I | X con MFA y auditoría |
| Auditoría/sincronización | propio | G mínimo | G mínimo | — | I supervisión | I operaciones | X operativa minimizada |

No inferir acceso por jerarquía: superadmin **no** obtiene automáticamente expediente de alumno, calificaciones privadas ni asistencia de otra institución. Dirección no hereda `MANAGE_USERS` ni `MANAGE_ACADEMICS` por nombre; un grant aprobado define operaciones, institución, periodo y revocación. Los tutorados y grupos se resuelven desde asignación activa; jamás por coincidencia de correo/matrícula.

**Contrato obligatorio de autorización:** identidad activa + email verificado + rol + capability + `institutionId` + `groupId/sectionId` (cuando aplique) + periodo + delegación vigente + estado de objeto + step-up MFA para escritura sensible. Claims en JWT y selección de APK no autorizan por sí solas. Denegación/ revocación exige respuesta consistente (401/403/404/409), invalidación de caché y auditoría sin PII.

## 3. Fases de ejecución y definición de terminado

### F0 — Baseline y trazabilidad (bloqueante)
- Inventariar ramas/PR abiertos; no mergear #210/#211/#212 sin CI e impacto revisados.
- Recuperar CI API y Android API31/API35, pruebas/lint, y diagnóstico de workflow firmado `No jobs were run`.
- Matriz real endpoint × roles × tenant × recursos: evidencia positiva/negativa y alcance; crear backlog por diferencia, no por capturas.
- **Gate:** ejecutables reproducibles, fallas clasificadas y sin regresión del alumno, login, QR o 9.º A.

### F1 — Contratos de experiencias/roles
- Resolver audiencia `ACADEMIC` / `MANAGEMENT` por perfil, preservando default `LEGACY` hasta migración de build (primera entrega de este PR).
- Introducir Dirección como identidad/capacidad separada de Coordinación, con migración de datos y retrocompatibilidad de `UserRole`, endpoints `/auth/me`, Android y SIII.
- API `/me/capabilities` real con grants por recurso, sin listar acciones falsas. Revocación efectiva en sesión activa y pruebas de cuentas multirrol.
- **Gate:** toda combinación de 7 actores × 2 APK tiene respuesta justificada; roles pendientes y suspendidos no obtienen datos académicos.

### F2 — Dos paquetes Android reales, sin duplicación
- Elegir flavors `academic` y `management` del módulo app (o módulos launcher mínimos si la modularización lo exige); identificadores únicos, iconos/nombres, deep links, canales FCM, variantes debug/release, almacenamiento independiente y firma verificada.
- Inyectar audiencia desde variante Android a `RoleExperienceResolver`, carrusel de login, selector de experiencia y *guardas* de navegación. Quien tiene ambas membresías inicia sesión de forma independiente en cada APK; nunca comparte almacenamiento/token por paquete.
- Separar dependencias/pantallas en composición modular progresiva, no copiar toda la app. Mantener `core:designsystem` V9 mate, tema claro/alto contraste, i18n, tipografía escalable y movimiento reducido.
- Migrar CI: `compileAcademicDebugKotlin`, `compileManagementDebugKotlin`, tests de variantes, `assembleAcademicDebug/Release`, `assembleManagementDebug/Release`, caminos nuevos de artefactos, apksigner y firma por paquete. No publicar release con debug key.
- **Gate:** dos APK distintas, coinstalables, abiertas en Poco X7 Pro, roles cruzados rechazados y no hay botón que entre a pantalla ajena.

### F3 — Perfiles académicos completos
- **Alumno:** matrícula y carga desde SIII, Hoy, semana lunes-sábado, cambios fechados, QR campus + clase, estados genuinos (pendiente/aprobada), calificaciones oficiales de solo lectura, canal cerrado.
- **Docente:** cursos asignados, horario, sesiones QR online/contingencia offline, lista autorizada, evidencias, cambios de clase y canal de avisos; pendientes de aprobación no acceden a sesión.
- **Tutor:** grupos y alumnos asignados, solicitudes/justificantes, tutorías/casos privados, avisos delegados, sin acceso a terceros ni notas restringidas.
- **Jefe/subjefe:** aceptación/revocación y expiración de nombramiento; canal limitado, sin modificar horario ni asistencia.
- **Gate:** estados carga/vacío/error/offline/reintento/éxito, deep links, regreso, scroll, sin fallas API31/35, permisos negativos de cada flujo.

### F4 — Gestión y Dirección
- **Dirección:** panel ejecutivo basado en datos reales, revisiones y autorizaciones scoped, reportes por institución, nunca administración global implícita.
- **Admin:** alumnos/docentes/grupos, periodos, vinculaciones, tutorías/representantes, revisión y publicación de horarios, avisos segmentados, estado de sincronización, bitácora.
- **Superadmin:** alta/revocación institucional, recuperación operativa, configuración, asignación de administradores, auditoría y MFA, sin acceso irrestricto a datos escolares.
- Toda acción mutante confirma permisos desde Ktor/SIII, `operationId` y versión esperada; conflictos reales 409, auditoría con autor/antes-después minimizados.
- **Gate:** pruebas de no autoelevación, cross-tenant, restricciones de administración entre pares, revocación a mitad de sesión, concurrencia y step-up.

### F5 — PDF unificado y publicación institucional
- Android selecciona PDF de horario/lista; *no* asumir OCR infalible. Reusar `DigitalPdfTimetableParser`, geometría/OCR y fixture 9.º A; validar 9.º A físico, tablas parciales, sábado, rotaciones, grupos y asignaciones docentes.
- Procesar **borrador → vista previa con procedencia y confianza → comparación/diff → resolución de colisiones → autorización MFA → transacción SIII → versión de publicación → proyección móvil**.
- Nunca poblar oficialmente alumnado a partir de texto PDF sin reconciliar IDs de matrícula/empleado en SIII. Mismatches se ponen en cola de revisión; no se publican ni se duplican.
- No sobrescribir modificaciones manuales confirmadas o fuentes más recientes; idempotencia, deshacer mediante revisión compensatoria, conservación de fuente y auditoría.
- **Gate:** una única carga produce cambios correctos en alumno, docente, tutor y Gestión, sin escribir 3 veces ni atribuir horarios a persona equivocada.

### F6 — Canal/avisos y entrega a destinatarios
- Mantener canal cerrado: alumno lee, no envía mensajes libremente ni entrega tareas. Docente publica en cursos asignados; tutor/jefe solo con delegación vigente; Administración por destinatarios institucionales.
- Segmentación estricta por institución/carrera/periodo/grupo/perfil; previsualización de audiencia, versión de contenido, borrador/publicación/cancelación, notificación FCM con redacción mínima.
- Outbox, reintentos, deduplicación, recibos, conciliación y offline. El evento no se da por aplicado hasta comprobar proyección, distinto de 202 recibido.
- **Gate:** no hay filtraciones de avisos a grupos ajenos ni duplicados tras reintento.

### F7 — Sincronización SIII verificada
- Revisar en Supabase los dos ledgers y migraciones antes de habilitar 0018/outbox; backup, hashes, usuario limitado y RLS/ FORCE; sin migraciones manuales a ciegas ni otra base.
- Identidad institucional explícita, enlaces/tenant correctos en Mongo, bootstrap HMAC cifrado con variables temporales solo durante provisión. Mantener flag OFF hasta preflight aprobado; los PR de bootstrap no autorizan configuración en producción.
- Contratos `siii.academic_section_snapshot.v1`, versiones monotónicas, SIII outbox, receptor Ktor firmado, proyección durable, rollback y reconciliación. Usar datos **sintéticos** para un piloto de institución; nunca reales antes de gates.
- Recorrido verificable: admin publica → SIII commit → outbox → Render 202 → worker proyección → estudiante/docente/tutor consulta → Android Room/FCM actualizados.
- **Gate:** eventos duplicados/fuera de orden/tampered/tenant ajeno/retirada de matrícula rechazados; recuperación tras caída probada.

### F8 — UX/UI y accesibilidad
- Unificar shell V9 mate/claro/negro profundo para todas las pantallas; conservar identidad UPTlax, QR central cuando aplique y navegación por contexto.
- Auditar pantallas con capturas **reales** por modo claro/oscuro/alto contraste, tamaño de texto, TalkBack, teclado, offline, permisos denegados, gestión de errores y scroll/navegación.
- Revisar que los widgets representen datos reales; no indicadores ficticios de asistencia ni métricas institucionales sintetizadas.
- **Gate:** matriz de screenshots + pruebas y revisión manual por los siete perfiles y ambas APK.

### F9 — Releases, piloto y corte controlado
- CI obligatoria: backend, contrato SIII, Android unit/lint/assemble/instrumentation API31+35, prueba estática de variante/firma, permisos negativos, secretos y dependencias; ninguna suite omitida contada como aprobada.
- Dos APK debug para piloto, dos release con keystore(s) privados y huellas verificadas, rollback y semver; no distribuir `unsigned`.
- Prueba en dispositivo físico: dos instalaciones, alumno+docente+tutor+Dirección+Admin+Superadmin y alumno representante; QR online/offline, PDF 9.º A, FCM y sincronización ida/vuelta.
- **Go/No-go:** sin evidencia de la cadena SIII completa o con fallos críticos de permisos/PDF/firma/CI, NO declarar producción lista.

## 4. Orden de PR y dependencias

1. `feat/two-apk-audience-foundation-20261021` — audiencia central, pruebas unitarias y este contrato (**sin activar todavía dos APK**).
2. `feat/role-grants-direction-contract` — rol Dirección, capacidades scoped, auth/me y servidor + pruebas de migración.
3. `feat/dual-apk-build-and-login` — Android flavors, filtro de login/navegación y CI/artefactos duales.
4. `feat/academic-role-end-to-end` — alumno/docente/tutor/representantes, permisos en API.
5. `feat/management-workflows` — admin/dirección/superadmin, directorios, delegaciones y auditoría.
6. `feat/pdf-preview-siii-publication` — pipeline PDF, revisión y commit institucional.
7. `feat/channel-targeting-and-siii-reconciliation` — canal/FCM, inbox/outbox y conciliación.
8. `fix/v17-ux-accessibility-pilot-release` — UX completa, CI, APK y aceptación física.

**Regla de merge:** cada PR parte de `main` vigente, sin copiar lógica entre APK, con test negativo para cada nueva autorización y sin modificar secretos ni SIII producción. Integrar solo con CI y revisión, no asumir éxito por abrir PR. Las fases se reajustan contra trabajo ya fusionado para evitar duplicidad.
