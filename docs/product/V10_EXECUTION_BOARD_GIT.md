# Tablero operativo Git V10 — Compañero de Clase

> Versión del plan: V10.0.0-plan. Baseline `main@841c8a31`. Estado: documentación propuesta, NO cierre funcional. Este tablero es el control para implementar en PRs independientes.

## 0. Qué se conserva

- `apps/android/core/designsystem`: toda la paleta V8, V8 glass, cabeceras, chips, botones; `core:motion`, `core:navigation`, `core:ui`.
- `apps/android/feature/auth/home/schedule/attendance/channel/classroom/grading/profile/settings/tutoring` tal como se integraron a main; refactors pequeños y trazables, no pantallas nuevas porque sí.
- `services/api`: auth nativo, providerRegistry, attendance y school presence, academic groups, classroom, tutoring, excuses, grading contracts, channel/events; preservar ownership de dominios.
- Contratos existentes con compatibilidad y migraciones. `docs/product/TUTORIAS_V1_MASTER_PLAN.md` queda histórico; anotar avances reales en nuevos documentos en lugar de reinstalar propuestas antiguas.

## 1. Secuencia de PR y Definition of Done

| Orden / rama | Cambios específicos | Pruebas bloqueantes | Dependencias |
|---|---|---|---|
| 00 `docs/v10-master-roles-guest-20261008` | Plan, roles, matriz, backlog, referencias | enlaces/rutas auditadas; no cambios runtime | main@841c8a31 |
| 01 `fix/v10-release-gates` | 409 asistencia específicos, botones para pases caducos, sábado/PDF/Inicio, placeholders de admin, navegación por rol | regresión alumno-docente, UIA/E2E + CI | 00 |
| 02 `feat/v10-authz-scopes` | permission policy, role/grant por recurso, invalidar sesiones, audit, contract backwards compatible | cross-user/group/institution/event, 401/403/404, revocación concurrente | 01 |
| 03 `feat/v10-tutor-complete` | tutor ficha, notas, seguimiento, citas, avisos, reportes; usa servicios existentes | ciclo expediente, revocación y notas privadas, filtros offline | 02 |
| 04 `feat/v10-admin-operations` | personas/roles con alcance, gestión grupos/horarios, approvals de tutores, reportes reales | admin aprueba/revoca, bulk import, trazabilidad y UX | 02 |
| 05 `feat/v10-superadmin-control` | separa controles institucionales, admin assignment, policy/integraciones, MFA step-up | no autoelevación, grant revocado, break-glass/audit | 02 + 04 |
| 06 `feat/v10-guest-invitations` | proposal/review, identity, invite/grants, estados, API tests | token unico/TTL/hashed/replay, denegación datos escolares | 02 + 04 |
| 07 `feat/v10-workshops-core` | eventos, sesiones, enrolamientos, waitlist, materiales, QR evento | concurrencia cupos, timezones, cancelaciones y leakage | 06 |
| 08 `feat/v10-workshops-android` | UI facilitador + participante, Compose V8, DI + nav + lifecycle | emulador 360/390/430/tableta/fuente200/TalkBack | 07 |
| 09 `feat/v10-event-certificates` | certificado verificable, emisión controlada, revocación; evaluación futura OpenBadges | forged verifications, no equivalencia curricular, revocación | 07+08 |
| 10 `test/v10-rc-e2e` | pruebas reales de 8 experiencias, QA físico, release notes, APK y plan rollback | 0 P0, CI o equivalencia local demostrada, firmas y hashes | 01–09 |

Cambios de modelo o schema con cliente antiguo: estrategia expand→migrate/backfill→switch→contract; nunca borrar dato productivo durante PR visual. Coordinar ramas creadas siempre desde `main` al inicio de fase; PR hacia main, no merge recursivo de features viejos.

## 2. Inventario de experiencia por módulo (criterios mínimos)

| Rol | Rutas principales existentes | Funcionalidad V10 a añadir/terminar | Dueño |
| --- | --- | --- | --- |
| Alumno | Inicio, Horario, QR, Clases, Perfil | catálogo/Mis talleres contexto secundario | Android home + workshops |
| Profesor | Hoy, Horario, QR, Clases, Canal | evaluación contextual, estados 409, salud de sync | Android attendance/grading |
| Tutor | TutorHome, TutorGroups, TutorRequests | StudentDetail, FollowUp, Appointments, Notices | feature:tutoring |
| Coordinación | CoordinatorHome, Horario, QR, Canal | delegaciones/roster autorizado, reportes | feature:home + API |
| Admin | AdminHome, Horario, QR, Canal | Usuarios, periodos, roles, grants, panel métricas real, propuestas invitadas | nuevo feature:administration en Android, API application |
| Super Admin | AdminHome (actual, compartido) | SuperAdminHome, seguridad, administradores, integraciones, políticas | nuevo feature:administration con pantalla propia |
| Invitado instructor | no existe | Invitation, GuestHome, Events, Roster, Session, Materials | nuevo feature:workshops |
| Participante externo | no existe | workshop-only enrollment, agenda, materials, constancias | feature:workshops con experiencia limitada |

**Límite de UI:** bottom nav 3–5 tabs por rol. Vistas operativas secundarias desde tarjetas/secciones y back, no barra con 8+ destinos. Mantener estilos Red Edition sin rediseñar ni copiar una pantalla a otro módulo.

## 3. Escenarios e2e de aceptación (obligatorios)

1. Admin crea grupo + asigna docente, horario y tutor → cada rol ve solo el recurso asignado → estudiante ve agenda coherente (incluye sábado).
2. Tutor abre justificante autorizado, adjunto permitido, modifica estado y crea nota interna → estudiante ve respuesta publicada, no la nota interna.
3. Docente inicia pase por clase, ve alumnos con evidencia del QR escuela separada y puede gestionar retardo según permiso; 409 no se muestra como «no pudimos actualizar» sin contexto.
4. Admin revoca tutor durante sesión: siguiente API falla y el dispositivo limpia datos restringidos; no queda consulta cacheada accesible.
5. Super Admin nombra admin con MFA → auditoría se registra → no hay acceso cruzado a estudiante sin autorización contextual.
6. Externo solicita impartir taller → admin aprueba → invitación single-use acepta → grant sobre evento E → publica material sin leer lista escolar.
7. Alumno y visitante externo se inscriben en E, cupo/concurrencia y lista de espera funcionan; facilitador gestiona sesión y QR EVENTO, no QR escolar.
8. Finaliza evento; emite constancia solo con criterio cumplido, pública verificable con datos mínimos, revoca y deja audit trail.
9. Sesión caduca, invitación se reutiliza, cambio de institución/evento, offline, rotación, modo oscuro/claro, AA/200%: sin filtraciones ni bloqueo de navegación.
10. No integración SIS: registro normal, eventos y datos propios funcionan; calificaciones oficiales y sincronizaciones se indican como no disponibles.

## 4. QA reproducible — comandos Windows orientativos

Desde la raíz del repo, con JDK/SDK y Gradle Wrapper configurados:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
.\gradlew.bat :services:api:test :apps:android:core:navigation:testDebugUnitTest :apps:android:app:assembleDebug
.\gradlew.bat :apps:android:app:lintDebug
adb devices
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

No asumir que los tasks están correctos sin validarlos con `./gradlew tasks` y el CI del commit actual. Instrumentación requiere emulador/device disponible; screenshots locales y logs redacted, sin IDs de personas reales.

## 5. Política de versionado real

- Nombre del plan = `V10`; versión de app actual = `0.1.0` (`versionCode=1`) a baseline. No saltar a `10.0.0` sin razón.
- En PR 01 decidir un único origen versionName/versionCode (Gradle) y generación de BuildInfo, con `commitSha`, `buildTime`, `apiContractVersion`, `environment`, sin secretos.
- Primera alpha funcional de V10 = `0.2.0-alpha.1` con `versionCode` incrementado; posteriores builds deben crecer. RC = `0.2.0-rc.1`, release = `0.2.0` **solo tras cierre integral**.
- Conservar changelog + notas de migración + release assets APK checksums. Tag del release solo cuando APK/CI/QA verificados y hash coincide. Un plan documentado no equivale a binario nuevo listo para probar.
- Auditorías existentes de V8 tienen fechas e hitos históricos; cada nueva auditoría cita SHA/capturas con fecha y origen.

## 6. Revisión obligatoria antes de merge

Checklist por PR: [ ] base/HEAD actual [ ] scope y policy negative tests [ ] compatibilidad y migración [ ] ViewModel/DI/rutas [ ] pruebas compiladas/ejecutadas [ ] visual comparisons V8 [ ] no botones/hardcode fake [ ] offline/refresh [ ] security/privacy [ ] evidencia emulador/físico cuando toque [ ] rollback [ ] QA owner [ ] SHA y CI exactos.

## 7. Riesgos de dependencia

- Si servidor Render/API escolar no está configurado, no presentar mock como integración real.
- La inscripción de externo no concede permisos de estudiante ni membresía a grupos; requiere identidad con perfil workshop-only.
- El «tutor» aquí es académico, no padre/tutor legal; otro producto y consentimiento.
- No exponer notas tutorías a super admin por defecto; auditar acceso excepcional.
- Offline guardar borradores permitidos, no copiar roster/tokens caducados a caches generales sin expiración.
- No certificar compatibilidad LTI/Open Badges sin implementarla explícitamente.
