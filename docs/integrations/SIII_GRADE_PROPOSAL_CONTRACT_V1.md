# Compañero → SIII / Propuestas oficiales V1

Esta entrega instala únicamente el contrato compartido serializable `companero.grade_proposal.v1`, validación de campos y pruebas Kotlin JVM. **NO** habilita el gateway de publicación `UnavailableGradeSyncGateway` ni modifica el libro de calificaciones.

El SIII debe desarrollar un inbox **servidor a servidor** con un secreto dedicado por institución, firma HMAC sobre el cuerpo y antirreplay, mapeo de identidades vigente, recepción idempotente, estado de revisión humana, permisos institucionales `grades.manage` y `grades.publish`, y auditoría. El número `gradeHundredths` usa centésimas exactas (875→8.75) y no Double.

El estado `RECEIVED` significa recepción, no nota aplicada. `VALIDATED` no sustituye revisión. `REQUIRES_REVIEW` debe bloquear publicación. Solo `APPLIED` tras aprobación institucional puede corresponder a calificación oficial y acuse emitido por SIII. Cualquier `UNAVAILABLE` del gateway actual debe mantenerse tal cual.

No entregar secretos en el APK, no derivar identidades oficiales de códigos de unión nativos, no publicar alumnos de otras instituciones, y no enviar calificaciones directamente al endpoint web `/grading`.
