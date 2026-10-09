# Contrato de seguridad y API — perfiles V10, invitado y eventos

> Diseño propuesto, **no endpoint disponible**. Baseline `main` = `841c8a31d891090a5aba593de9509664bf17d01b`. El rol `GUEST_INSTRUCTOR` todavía NO existe en `UserRole`. Nunca aplicar este contrato como cambio de producción sin tests y revisión de migraciones.

## 1. Principio de autorización

Permitir si y solo si se cumple **identidad activa**, **rol/capacidad**, **recurso concreto**, **institución autorizada**, **asignación vigente**, **periodo/evento** y **estado del objeto**. Denegar por defecto. Las claims JWT son indicios de identidad; roles/grants revocados se consultan en fuente autoritativa o se invalidan mediante versión de sesión; cache autorizada tiene TTL corto y claves por usuario/scope. Auditoría registra actor, sujeto, acción, recurso, scope, resultado, momento y requestId, nunca token ni expedientes completos.

Separar:
- `UserRole`: identidad/capacidad general (STUDENT, TEACHER, TUTOR, COORDINATOR, ADMIN, SUPER_ADMIN, propuesto GUEST_INSTRUCTOR, propuesto WORKSHOP_PARTICIPANT).
- `AppExperience`: presentación Android elegida (sin transferir privilegios).
- `ScopedGrant`: asignación por `institutionId`, `eventId` o `academicGroupId`; estado y vigencia. Un usuario puede tener varias experiencias sin sumar permisos sobre otro contexto.
- `Permission`: operación concreta con lógica central; `requirePermission(actor, action, resource)`, verificación institucional y estado. Uso de 404 para ocultar existencia solo cuando la política de seguridad lo justifique.

## 2. Matriz de capacidades — decisión funcional

Leyenda: S = propio, G = grupo/ámbito asignado, I = institución asignada, P = plataforma/configuración, E = evento autorizado, – = no permitido por defecto, D = delegación explícita auditada. La matriz es intención de producto; cada celda sensible requiere pruebas en API.

| Acción | Estudiante | Docente | Tutor académico | Coord. | Admin | Super Admin | Invitado facilitador | Participante externo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Ver horario académico | S | G | G lectura | I/D | I | —/D | — | — |
| Abrir QR/pase de CLASE | — | G | — | — | gestión/D | — | — | — |
| Gestionar entrada QR ESCUELA | — | — | — | I/D | I | política/D | — | — |
| Crear/editar grupos institucionales | — | — | — | D | I | política/D | — | — |
| Evaluación y notas oficiales | S lectura | G escritura | — | —/D lectura | auditoría/D | —/D | — | — |
| Leer nota TUTOR_INTERNAL | — | — | G | derivación/D | acceso excepcional D | acceso excepcional D | — | — |
| Validar justificantes | propia solicitud | — | G | I/D | I | —/D | — | — |
| Nombrar/revocar tutor | — | — | — | D | I | D | — | — |
| Promover admin / cambiar política auth | — | — | — | — | — | P con MFA | — | — |
| Proponer taller/evento | S propuesta opcional | G/I | —/D | I | I | política | E borrador propio | — |
| Aprobar/publicar eventos | — | — | — | I/D | I | política/D | — | — |
| Impartir/materiales/avisos del evento | — | E si grant | — | E/D | E/D | — | E | — |
| Consultar inscritos del taller | — | E mínimos | — | E/D | E/D | —/D | E datos mínimos | S inscripción |
| Inscribir/cancelar participación | S | S | S | S | S | S | S | S |
| Emitir constancia | — | — | — | D | I | política | E solo bajo grant explícito | — |
| Verificar constancia por código | token verificable | idem | idem | idem | idem | idem | idem | idem |
| Auditoría técnica/sistema | — | — | — | alcance/D | I agregada | P controlada | solo acciones E | — |

**Nunca** transformar automáticamente entrada escolar en asistencia a taller, asistencia a taller en presencia escolar, ni constancia de taller en calificación/crédito oficial.

## 3. Modelo de eventos y propuestas

- `WorkshopProposal`: id, institutionId, proposedById, title, summary, learningOutcomes, kind (COURSE|WORKSHOP|CONFERENCE|SPECIAL_SESSION), visibility, capacity, mode (ONSITE|REMOTE|HYBRID), targetAudience, scheduleDraft, venue, materialRequirements, status, reviewerId, decisionReason, version, createdAt, updatedAt.
- `WorkshopEvent`: id, institutionId, approvedProposalId, ownerAdminId, facilitatorGrantIds, status, startsAt/endsAt con offset UTC y timezone IANA, registrationStartsAt/EndsAt, capacity, waitlistPolicy, audience, eventScope, version.
- `WorkshopSession`: id, eventId, startsAt, endsAt, attendancePolicy, host, resourceLinks; no reusar `ClassOccurrence` institucional.
- `WorkshopEnrollment`: id, eventId, participantId, status, enrolledAt, cancellationReason, source, consentVersion, version; índice único (eventId, participantId).
- `ExternalInstructorInvitation`: id, targetEmailHash, invitedBy, proposalId/eventId, role, status, tokenHash, expiresAt, acceptedAt, revokedAt, consumedAt, attempts.
- `ScopedGrant`: id, principalId, capability, institutionId, eventId, startsAt, expiresAt, revokedAt, grantedBy, version. Rol externo solo si grant vigente.
- `WorkshopMaterial`: id, eventId, ownerId, storageRef privado, mime, size, audience, publishedAt, version.
- `WorkshopAttendance`: id, eventId, sessionId, participantId, status, evidenceType, submittedAt, verifiedAt, verifiedBy, operationId y auditoría; namespace separado de escuela/clase.
- `WorkshopCertificate`: id, eventId, participantId, type (PARTICIPATION|COMPLETION), issuerId, criteria, verificationId opaco, status, issuedAt, revokedAt, revocationReason. No equivalencia curricular por defecto.
- `AuditEvent`: id, time, actor, action, resourceType/id, institutionId, scope, outcome, requestId, before/after redacted y version.

## 4. Máquinas de estado canónicas

Propuesta: DRAFT → SUBMITTED → UNDER_REVIEW → NEEDS_CHANGES → SUBMITTED / APPROVED / REJECTED / WITHDRAWN. Solo ADMIN autoriza publicación; transiciones CAS en servidor, sin doble aprobación.

Invitación: CREATED → SENT → ACCEPTED → ACTIVE → EXPIRED / REVOKED; también CREATED/SENT → EXPIRED / REVOKED. Tokens se consumen atómicamente, nunca visibles después de la emisión.

Evento: DRAFT → APPROVED → PUBLISHED → IN_PROGRESS → COMPLETED → ARCHIVED. Desde PUBLISHED permitir CANCELLED y notificación a inscritos; al cambiar horario se notifica y audita.

Inscripción: REQUESTED → CONFIRMED / WAITLISTED / DENIED; CONFIRMED → CANCELLED / ATTENDED / NO_SHOW. No exceder cupo bajo concurrencia; promoción waitlist transaccional.

Constancia: DRAFT → ISSUED → REVOKED; el verificador público nunca filtra correos o matrículas completas.

## 5. API propuesta (Ktor; base path sujeta a versión)

Toda mutación requiere idempotency key y contexto; respuestas `ApiResponse` o `ApiError` con requestId. Opcional ETag/If-Match para concurrencia. Paginación limit/cursor, orden estable, validación MIME y URL.

| Verbo y ruta | Autoridad | Respuesta / guardia |
| --- | --- | --- |
| GET /me/capabilities | autenticado | roles + grants efectivos mínimos con scope, expiry/version |
| GET /admin/users | ADMIN ámbito | cuentas paginadas minimizadas |
| PATCH /admin/users/{id}/roles | SUPER_ADMIN o delegación definida | MFA/step-up, no auto-elevación, audit |
| POST /admin/tutors/{id}/assignments | ADMIN ámbito | reutilizar `/tutoring/assignments`; adaptador/compatibilidad |
| POST /workshops/proposals | cuenta habilitada | borrador propio, valida campos |
| GET /workshops/proposals/mine | proponente | solo propios |
| GET /admin/workshops/proposals | ADMIN ámbito | lista paginada y filtros |
| PATCH /admin/workshops/proposals/{id}/decision | ADMIN ámbito | transición válida, versión y reason |
| POST /workshops/{eventId}/invitations | ADMIN/host delegado | token opaco, rate limiting, auditoría |
| POST /workshops/invitations/accept | cuenta verificada | consume token hash atómicamente; evita replay |
| POST /admin/workshops/{eventId}/grants/revoke | ADMIN ámbito | revocación real, invalida cache |
| GET /workshops | público/autenticado | solo eventos PUBLISHED visibles, datos mínimos |
| GET /workshops/{id} | público/autenticado scoped | reglas por visibility |
| POST /workshops/{id}/enrollments | cuenta verificada + consentimiento | cupo, inscripción única, waitlist |
| GET /workshops/{id}/participants | facilitador con grant E / admin | roster minimizado paginado |
| POST /workshops/{id}/sessions | admin/host con grant específico | sesión de taller no escolar |
| POST /workshops/{id}/sessions/{s}/attendance | grant E y/o participante según flujo | QR evento independiente, server time |
| GET/POST /workshops/{id}/materials | roles E | lectura/publicación restringidas, archivos privados |
| POST /workshops/{id}/certificates/issue | admin o grant E explícito | criterios verificados server-side |
| GET /certificates/verify/{opaqueId} | público limitado | validez, emisor y evento; datos mínimos |
| POST /admin/certificates/{id}/revoke | admin ámbito | auditable, no borrado silencioso |

## 6. Seguridad crítica y casos negativos

- Solicitud pública nunca acepta `roles=ADMIN` ni `GUEST_INSTRUCTOR`; registro libre solo concede cuenta básica. Promoción requiere aprobación y persistencia auditable.
- JWT firmado sin revalidación de grants NO es suficiente tras revocación; controlar sesiones, invalidación y caches por user+scope. Cambiar a experiencia docente no concede permisos de taller ni admin.
- Probar usuario A intenta GET/PATCH event B, alumno A ficha tutor B, tutor grupo A ficha grupo B, facilitador evento A roster B, invitación consumida/revocada/vencida, email distinta, IDs falsificados, doble inscripción y last-seat races.
- Probar tiempos de server (UTC) y zona del evento; expiraciones, fecha de presentación y recurso archivado. No exponer número total de usuarios/sesiones si métrica no fue consultada.
- Límite de acceso desde enlaces, CSV/PDF de alumnos, QR, archivos, push y notificaciones. No enviar tokens de aceptación en analíticas/crash/logcat.
- Un invitado externo sin institución asociada requiere asignación explícita a evento de una institución anfitriona; no crear institucional tenant a partir de correo libre.
- MFA/step-up para roles superadmin y acciones riesgosas, logs de auditoría, limitaciones de export y evaluaciones periódicas de grants.
- QA contractual: 401 no autenticado, 403 rol/scope insuficiente, 404 para recurso no visible, 409 transición incompatible, 422 validación semántica cuando corresponda; mensajes de UI específicos y accionables.

## 7. Integración institucional y constancias

V1 independiente: identidad plataforma + base Ktor, talleres propios y permisos internos. Las integraciones con un SIS o LMS real son opcionales. No prometer calificaciones sincronizadas: `ApplicationModule` usa actualmente `UnavailableGradeSyncGateway`. Cuando se disponga de LMS, evaluar LTI 1.3, Names and Role Provisioning y Deep Linking **como adaptadores**, con feature flag y pruebas de seguridad. Para constancias, soportar primero emisión/verificación/revocación básica; si se necesita reconocimiento portátil, evaluar Open Badges 3.0, no fingir compatibilidad.
