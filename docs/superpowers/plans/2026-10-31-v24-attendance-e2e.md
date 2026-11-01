# V24 — cierre de código Compañero/SIII

**Objetivo:** completar ingreso institucional docente, apertura de clase y avisos limitados a alumnos elegibles; conservar una sola autoridad de asistencia.

**Base:** Compañero `5eba569084cf2742ae130ec15e457c7759d28254` (incluye PR #241); SIII `f19d8a0eacf37960aba95e9d09000ce6d4992f7f`.

**Alcance actualizado por el usuario:** «Solo trabaja en codigo, las pruebas las hago yo». No ejecutar suites ni piloto físico. Compilar y revisar estáticamente; preparar regresiones y comandos reproducibles para el usuario. No afirmar certificación E2E, hardware ni despliegue.

## Trabajo

- [x] Permitir registrar QR institucional a docentes y alumnos activos del mismo tenant, reutilizando `SchoolPresenceService` y sus rutas. Invitados y roles administrativos solos siguen denegados.
- [x] Exigir ingreso docente vigente antes de abrir una sesión nativa y filtrar avisos por matrícula vigente e ingreso del alumno. FCM y QR nunca escriben asistencia confirmada.
- [x] Reutilizar el escáner y tarjeta de jornada en la pantalla docente. Invalidar respuestas al cambiar cuenta/modo y conservar el servidor como autoridad.
- [x] Revisar cierre de pase, revisión e historial para que las ausencias sean persistentes y consultables desde SIII.
- [x] Conectar cambios/cancelaciones oficiales a la cola push real, conservar destinatarios tras tombstone/reintentos y no divulgar datos académicos en la notificación.
- [x] Adaptar contratos y fixtures de regresión sin ejecutarlos; actualizar SIII al SHA del receptor final y conservar validación sin skips en CI.
- [x] Compilar API y ambas variantes Android; revisar diff y dejar PR y matriz de entrega con estado real.

## Invariantes y revisión

- SIII conserva identidad académica, matrícula y asignaciones oficiales; Ktor conserva asistencia.
- No crear matrículas, roles, credenciales o registros productivos.
- El ingreso QR es evidencia declarada; `networkVerified=false` sigue vigente.
- Reintentos no crean otra sesión, registro o aviso para el mismo evento y dispositivo.
- Revisar revocación, tenant ajeno, alumno sin QR, docente sin QR y respuesta tardía tras cambio de cuenta.
- Pruebas y CI observadas sobre las bases no certifican los cambios V24.
