# Compañero V21 — auditoría integral y criterios de entrega (2026-10-29)

**Referencia de integración:** PR #237, `codex/companero-v21-integral-20261029` sobre `main`. Se consolidan los trabajos de #235 (Invitado, Inicio y conectividad) y #236 (asistencia/UX) sin cambiar permisos institucionales desde el cliente. Este documento es una matriz de verificación, no una certificación de producción.

## Bloque A: identidad, autorización e Inicio

- El carrusel **académico** permite Alumno, Docente, Tutor e Invitado; gestión conserva sus roles administrativos y no expone registros privilegiados a Invitado.
- `/auth/me` sigue siendo el punto autoritativo de identidad tras restaurar sesión. 401/403 deben bloquear rutas; red/5xx transitorios mantienen acceso protegido mientras se reintenta.
- `HomeViewModel` recupera el horario personal de Room ante error recuperable o si el servidor responde sin ninguna materia académica. Cuando hay cursos institucionales, estos **tienen prioridad**; los datos personales nunca se etiquetan como asignaciones de SIII.
- Eventos personales con `recurrence=ONE_TIME` se muestran solo en `effectiveDate`; no se repiten cada lunes/semana. Regresiones en `TodayScheduleTest`.
- En caso de 401 no debe enseñarse un calendario perteneciente a una sesión anterior. `PersonalScheduleRepository` delimita el almacenamiento por identificador del usuario del token.

## Bloque B: asistencia y avisos

- Solo **un** acceso al QR institucional de jornada; un pase de clase proviene de la sesión docente. QR de clase es **contingencia secundaria**.
- FCM es aviso, no prueba de asistencia. El alumno confirma de manera explícita; la revisión y disposición de presencia/retardo/ausencia corresponden al servidor y docente.
- `AttendanceSessionService` limita `closesAtEpochSeconds` al mínimo entre duración solicitada y fin programado de clase. El servicio valida identidad, docente asignado y estado de sesión; una segunda sesión no puede sobrescribir una existente ajena.
- Historial: filtros por `AttendanceDisposition` con evidencia de revisión docente, cinco registros iniciales y expansión bajo demanda. Las filas locales `SYNCED` no se cuentan como presencia humana.
- Pruebas actualizadas `AttendanceTruthUiTest`, `AttendanceUiPolicyTest`, `AttendanceServiceTest`; el workflow requiere ocho pruebas distintas de verdad de asistencia.

## Bloque C: seguridad, infraestructura y alcance institucional

| Riesgo / incidencia | Estado demostrado por cambios V21 | Cierre requerido para producción |
| --- | --- | --- |
| Invitado se presenta pero no tiene asignación | Acceso visual restringido al APK académico | Invitación autorizada en backend, vínculo institucional y pruebas de denegación entre roles/tenants |
| Cuenta sin materias oficiales | Inicio ya permite agenda importada y separa origen de información | Crear inscripciones/referencias **reales** en SIII, sincronizar SIII → Ktor → Android y probar bajas/revocaciones |
| Notificación de pase | Flujos de app y outbox FCM existentes; evento docente encola por identidad actual | Envío real en primer plano, segundo plano y app cerrada, token rotado/revocado, botón de deep link y fallback sin permiso |
| QR institucional sin internet | La contingencia de QR docente no equivale al QR de pared offline | Issue #125: evidencia local durable, reconciliación y prueba física con dos teléfonos, sin marcar presencia automática |
| Administración de cuentas y permisos | Sin escalada desde cliente | Issue #140: reconcilación de aprobaciones de tutor y migración segura; autorización por institución |
| Horarios y clases | Reglas de prioridad, fechas y visualización verificables con tests | Validar parser PDF de 9A y ocurrencias docentes contra fuente oficial, importación idempotente |
| Distribución | CI genera variantes debug | Firma release real, actualización de APK, notificaciones, permisos, accesibilidad y dispositivos físicos |
| SIII / HMAC | Se conserva integración preexistente, sin crear datos ficticios | Validar migraciones, credenciales HMAC sucesoras, entregas/reconciliación y expiración con pruebas E2E |

## Gate técnico antes de fusionar a `main`

1. API/Ktor: compilar, ktlint, pruebas y smoke con dependencias; corregir cualquier regresión del SHA exacto del PR.
2. Android: compilar ambas distribuciones, unitarias JVM, ktlint y APK; instrumentación académica y de gestión en API 31 y API 35. Revisar especialmente los tests MFA y solicitudes de tutor que han fallado por timeout al pulsar controles.
3. Confirmar `mergeable=true`, cabeza estable y checks exigibles en **success**, sin suponer que un trabajo en cola o cancelado es verde.
4. Tras merge verificar checks de `main`, artefactos y SHA. No atribuir al merge una prueba física que no se ha hecho.

## Casos imprescindibles en piloto físico y SIII (NO-GO hasta verificar)

- Estudiante 9A con/ sin inscripción: Inicio → Horario → Pase; docente de Programación Móvil asignado al mismo grupo; invitado no lee calificaciones, asistencia ni expediente.
- Un solo QR institucional por jornada; después de abrir pase docente, dispositivo del alumno recibe aviso; confirmar, docente revisa, historial se actualiza en app y consulta autorizada en SIII.
- Sin conectividad se muestra agenda personal; no se inventa presencia ni sincronización. Recuperada la conexión, `/auth/me` reconcilia estado. Sesión revocada o usuario cambiado no accede a datos anteriores.
- Probar alto contraste, 320dp, tablet, TalkBack, teclado/MFA, rotación, permisos Android 13+ y app apagada.
- Datos reales protegidos entre instituciones; salida de docente, cancelación de clase y cambios de periodos revocan autorización y notificaciones futuras.

**Política de verdad:** Un PR abierto, CI parcial o SDK Firebase configurado **no** prueba entrega FCM, datos institucionales completos, APK lista para distribución ni pase presencial. No fusionar si el SHA exacto no está validado.
