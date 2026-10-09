# Plan maestro V10.1 — endurecimiento, UX y evolución de Compañero de Escuela

> **Estado:** PLAN DE EJECUCIÓN / auditoría estática Git. **Fecha:** 2026-10-08 (America/Mexico_City). **Base auditada:** `main@5f678fdcd6bbf423c7c217fa0d1f73ded2054974` (PR #122 de documentación V10 integrado). **No implica cambios de runtime, CI verde del HEAD, APK físico validado ni funciones nuevas terminadas.** Actualizar la línea base al abrir cada PR. Las evidencias de CI que cambien con nuevos pushes se deben volver a consultar.
>
> **Documentos normativos previos:** [Plan V10 de perfiles y eventos](V10_MASTER_PLAN_ROLES_GUEST_20261008.md), [tablero Git V10](V10_EXECUTION_BOARD_GIT.md), [matriz de permisos V10](../architecture/V10_PERMISSIONS_GUEST_API_CONTRACT.md), [límites V15](../architecture/SERVICE_BOUNDARIES_V15.md). Este documento **complementa**, no reemplaza ni reinicia esos trabajos.

## 1. Mandato y criterios de producto

Convertir la aplicación existente en una plataforma académica comprobable, sin rediseño global ni reescritura: **Kotlin nativo + Compose + Navigation + Hilt + ViewModel/StateFlow + Room/WorkManager, Ktor + MongoDB, contratos compartidos**. Reutilizar V8 Red Edition, vidrio, fondos, tokens, animaciones y navegación actual. No introducir Expo, otro backend, un LMS paralelo, clases inventadas, datos escolares falsos, rutas sin consumidores ni módulos huérfanos.

Decisiones del producto: (a) cuentas nativas funcionan sin API institucional; sincronización escolar opcional, claramente degradada si no existe; (b) un Admin asigna clases/tutores, docentes no crean grupos institucionales; (c) un Tutor es **tutor académico** y sus notas no se heredan por el rol docente; (d) entrada QR **escolar**, asistencia QR **de clase** y QR **de taller** son tres dominios distintos; (e) los invitados y externos tienen grants de evento, nunca acceso escolar implícito; (f) **ninguna evidencia offline o SSID declarado por cliente equivale por sí sola a presencia verificada**.

Objetivo de cierre: recorridos de extremo a extremo demostrados por rol, autorización negativa por recurso, estados offline sin falsos positivos, UI accesible, pruebas API + Android + dispositivo y artefacto reproducible ligado a SHA. No declarar `READY_FOR_PILOT` por compilar APK.

## 2. Inventario Git del corte

| Elemento | Hallazgo verificable | Acción |
| --- | --- | --- |
| `main` | SHA `5f678fdcd6bb`; PR #122 documentación V10 integrado | Partir de esta base y revalidar antes de cada PR |
| #112, lector horario digital | Abierto; PDFBox/texto y geometría frente a OCR; cambia importador PDF | Validar CI, rendimiento, importación real de 9° A, edit/review y tamaño APK; rebase antes de merge |
| #123, contingencia QR docente | Abierto; QR prefirmado, lectura sin Wi-Fi y estado `REVIEW_REQUIRED` | Pruebas replay/tiempo/aislamiento, dos teléfonos y no autopresencia |
| #124, asistencia/Admin F0 | Abierto; horario de pase, estados 409 y métricas falsas | **Desbloquear CI:** `AdminHomeScreen.kt:160` línea vacía extra al EOF reportada en jobs API/Android; re-ejecutar **todos** los checks y QA |
| #125 | Issue de QR institucional de pared offline (separado de #123) | Implementar outbox de ingreso y revisión humana, no declarar presencia verificada |
| #113–#121 | Épico y backlog V10 (Tutor, Admin, Super Admin, invitados, talleres, release) | Reutilizar, no duplicar issues equivalentes |
| #98, #74, #92, #101 | Errores de canal, agenda, FCM y QA docente anteriores | Triage por reproducción contra `main`, no cerrar por antigüedad |
| `.github/workflows/ci-api.yml`, `ci-android.yml` | Verifican backend, unit tests, lint, APK, instrumentación y secretos | Completar gates de cobertura crítica, SCA/SBOM y pruebas por rol |

La causa **observada** del fallo de #124 en el corte fue el control de formato `check-whitespace.sh` (línea vacía final de `AdminHomeScreen.kt`). **No** equivale a demostrar que Kotlin compila: el pipeline no llegó a esas fases. Evitar que los jobs antiguos se presenten como prueba del SHA final.

### Orden recomendado de integración de PRs en curso

1. **#124 primero:** corrección mínima de whitespace, rebase contra HEAD actual, ejecutar API y Android completos, repro docente/admin y revisión de diff. Interseca rutas Android de asistencia.
2. **#112 en paralelo solo para QA**, integrar cuando pase su propia suite y el PDF digital original se valide en dispositivo; conflictos de horario se resuelven sin escoger código viejo por comodidad.
3. **#123 después de reconciliar con #124** en `AttendanceViewModel/Screen` y API; verificar política offline y revocación de sesión. Sin merge si algo queda en revisión.
4. **#125** como implementación **separada y posterior** del QR de pared y outbox escolar; no confundirla con la rotación QR del profesor.
5. Reconsultar `main`, PRs, checks y ramas después de cada merge, porque el corte cambia durante el trabajo.

## 3. Hallazgos priorizados: verificación de riesgo, no certificación de explotación

| ID | Severidad | Evidencia localizable | Riesgo / hipótesis para comprobar | Cierre exigido |
| --- | --- | --- | --- | --- |
| SEC-01 | P0 | `services/api/.../plugins/Plugins.kt`, `auth/AuthTokenService.kt` | JWT incluye `session_generation`, pero la validación observada verifica sesión/revocación sin comparar generación ni estado/roles actuales del actor en cada petición | Revocación inmediata de rol/cuenta/grant; pruebas token anterior y acceso a recurso después de revocar |
| SEC-02 | P0 | `presence/SchoolNetworkVerifier.kt` y `SchoolPresenceService.kt` | SSID/BSSID enviados por el cliente son evidencia declarativa, no prueba independiente de presencia física | Modelo de evidencia honesto y mecanismos verificables cuando existan; no permitir elevar solo por metadatos manipulables |
| SEC-03 | P0 | `presence/SchoolEntryQrVerifier.kt`, #123 y #125 | Copia/replay de QR fijo, corte de Wi-Fi, relojes alterados, doble procesamiento | Idempotencia, caducidad, firmas y revisión humana; distinguir `PENDING`, `REVIEW_REQUIRED`, `VERIFIED`, `REJECTED` por filtro |
| SEC-04 | P1 | `auth/LoginAttemptLimiter.kt` y `auth/AuthRoutes.kt` | Limitador in-process en login; registro/refresh no comparten ese mecanismo | Rate limit distribuido y límites para login, registro, refresh, recuperación, invitaciones; test de concurrencia y NAT escolar |
| SEC-05 | P1 | `auth/PasswordHasher.kt` | PBKDF2-HMAC-SHA256 con 210k iteraciones; evaluar política de fortaleza/rehash y cierre de cuentas | Perfil de hash documentado, rehash compatible al login, pruebas tiempo y errores sin enumeración |
| SEC-06 | P1 | `tutoring/TutorCaseService.kt`, #115/#116 | Notas y casos muy sensibles; un cambio de grupo/rol debe retirar acceso API y cache local | Tests horizontales, transversales, de visibilidad y revocación; mínimo de datos y auditoría sin contenido sensible |
| SEC-07 | P1 | `.github/workflows/`, `docs/security/SECURITY_MODEL.md` | Documentación de seguridad de fundación quedó obsoleta; faltan controles automatizados de dependencias y supply chain | Inventario de bibliotecas, SCA/SBOM, revisión de permisos Actions, docs sincronizados con HEAD y gates eficaces |
| UX-01 | P0 | #112, #74, `feature/schedule` | PDF real mezcla nombre/docente y omite o traslada días; sábado/solapes | 9° A validado con revisión por celda y snapshots de 7 días; no guardar importación ambigua sin confirmación |
| UX-02 | P1 | `core/navigation/TopLevelNavigation.kt`, `MainActivity.kt`, #101 | Regreso a Inicio, stacks secundarios, roles múltiples y pulsaciones bloqueadas por overlays | Matriz de navegación automatizada y recorridos en APK por todos los perfiles |
| UX-03 | P1 | `core/designsystem/v8`, `core/navigation`, V8 glass QA | Contraste, encabezados, densidad y fuente 200% no pueden romper targets | QA en 360/390/430dp, tableta, TalkBack, alto contraste y reduced motion |
| DATA-01 | P0 | #124, AdminHome | Indicadores estáticos podían simular servicios activos y cero alertas | Métricas con fuente, freshness/updatedAt, empty/degraded real y ausencia de falsos éxitos |
| INT-01 | P1 | `ApplicationModule.kt` usa `UnavailableGradeSyncGateway` | Sin API institucional de notas, la app no debe anunciar «sincronizado» | Sync solo con adaptador real y respuesta servidor; fallback explícito no disponible |
| OPS-01 | P1 | `docs/quality/RELEASE_READINESS.md`, #92 | CI/APK no cubre despliegue real, push FCM, dispositivo, políticas ni firma | Gates de release, protección de datos, trazabilidad, rollback y piloto autorizado |

**Rutas de referencia** anteriores usan el prefijo real `services/api/src/main/kotlin/org/companerodeescuela/api/`. Etiquetar cada hallazgo en el issue/PR como CONFIRMADO EN CÓDIGO, REQUIERE REPRODUCCIÓN o DEPENDENCIA EXTERNA. No inferir vulnerabilidad explotable ni marcar corregido sin evidencia de HEAD y prueba negativa.

## 4. Fases ejecutables, paquetes y criterios de aceptación

### F0 — Congelación funcional y control de integración (P0; primer paquete)

**Responsables:** integrador Git, Android docente/horario, QA.

- F0.1 Crear lista de PRs vivos (112/123/124), cambios solapados por archivo, cabeza/merge-base y matriz de pruebas; rebase *sin forzar* `main`, no copiar carpetas antiguas.
- F0.2 Corregir #124 `AdminHomeScreen.kt:160`, rerun CI completo; resolver 409 por ocurrencia, reloj institucional de referencia y listas incompletas sin mostrar `0` ficticio.
- F0.3 Validar #112: PDF nativo primero, OCR fallback, letras contiguas/profesor vs materia, 7 días, revisión antes de persistir, recuperación de un fallo y archivos no tabulares rechazados.
- F0.4 Reconciliar #123 con cambios de F0.2, luego fijar contrato para #125. No marcar `PRESENT`/entrada escolar por captura offline.
- F0.5 Actualizar baseline de docs `SECURITY_MODEL.md`, `RELEASE_READINESS.md` y auditorías históricas mediante sección «corte actual», sin borrar evidencia original.
- **Gate:** 0 defectos P0 abiertos de integridad, jobs verdes del SHA final, flujo alumno y docente en APK (incluidos sábados), métricas administrativas de origen confiable y bitácora de pruebas.

### F1 — Seguridad central y políticas de acceso (P0, #115)

**Responsables:** API/auth, security QA, Android cache/session.

- F1.1 Establecer un único `AuthorizationPolicy` con identity + account active + permission + institution scope + group/course/event + assignment/grant version + resource state; `deny-by-default`.
- F1.2 Revisar generación de sesión/claims, cambios de rol y revocación; no fiarse de `roles` embebidos en JWT hasta que caduque si el permiso fue revocado; limpiar experiencias y cache al 401/403 o revocación.
- F1.3 Endurecer login, registro, refresh y flujos futuros de reset/invitación con rate limits compartidos (p. ej. almacén central), políticas de contraseñas, verificación de correo donde corresponda, evaluación MFA/step-up para administradores.
- F1.4 Protección de PII: minimización, cifrado del cache autorizado cuando se justifique, expiración por rol, cuotas y paginación de listas, request size, redacción en logs y auditoría sin cuerpos de notas.
- F1.5 Seguridad de configuración: HTTPS real, CORS/headers revisados, secretos rotables, no mocks en staging/producción, dependencia/integración fallida explícita, revisión de hash de contraseñas.
- **Gate:** prueba negativa usuario A→datos B, docente otro grupo, tutor revocado, admin otra institución, guest sin grants, token/sesión revocados y concurrencia; 0 filtraciones de autorización reproducibles.

### F2 — Veracidad de asistencia y operación offline (P0, #114/#125)

**Responsables:** API presence/attendance, Android attendance, QA escuela.

- Definir contrato de evidencia: `SCHOOL_ENTRY` vs `CLASS_ATTENDANCE` vs `WORKSHOP_ATTENDANCE`; fuente, método, fiabilidad, autoridad revisora y resultado administrativo separados.
- QR gestionado: firma, nonce u operación idempotente, expiración, revocación, replay, bloqueo de clases cerradas y tolerancia temporal documentada. El cliente nunca recibe la clave maestra HMAC.
- QR pared sin red (#125): outbox escolar independiente, captura local, firma verificable cuando aplique, reconciliación al servidor y siempre `PENDING/REVIEW_REQUIRED` sin testigo de confianza.
- QR docente sin Wi-Fi (#123): rotación previamente autorizada; no abrir desde cero sin material válido; sesiones anticipadamente canceladas no se aceptan; revisión posterior y posibilidad de rechazo.
- Retardo/suplantación: políticas del servidor por ocurrencia, cambios docente/admin con autor/fecha/motivo; no mutar evidencia original, solo disposición final auditada.
- **Gate:** dos teléfonos + modo avión antes/durante/después de clase + reboot + reloj modificado + replay + sesión cerrada + revocación + cambio de cuenta; 0 presencias automáticamente verificadas solo por offline.

### F3 — Navegación, horario y UX V8 integral (P1, #74/#98/#101)

**Responsables:** Android Compose, UX, accesibilidad y QA.

- Un solo catálogo de destinos por experiencia; retorno `Hoy→Horario→Hoy`, `Clases→Canal→Atrás`, `Evaluación→Clases`, Tutor→Solicitudes→Tutor; logout/login o cambio de rol limpia estados ajenos.
- Horario: PDF digital vía coordenadas antes de OCR, validación de día/hora, materia y profesor, duplicados, conflictos, sábado, edición por día y movimientos con undo/confirmación persistida; aviso de fuente `USER_IMPORT` vs `SCHOOL_API`.
- UI: tokens V8, `V8FrostedGlassPanel`, `V8CampusBackdrop`, encabezados compactos, modales accesibles y feedback consistente; no copiar componentes ni añadir fondos falsos.
- Todos los controles hacen algo o están explícitamente deshabilitados; estados loading/empty/error/offline/retry/conflict y no números inventados.
- **Gate:** snapshots 360/390/430/768/1024, fuente 200%, TalkBack, contrast, reduced motion, scroll, orientación, al menos un teléfono físico; ruta de regreso correcta en cada rol.

### F4 — Tutorías, docentes y operación administrativa (P1, #116/#117/#118)

**Responsables:** API académico/tutoría, Compose roles, análisis funcional.

- Tutor: expediente mínimo por grupo autorizado, casos/observaciones privados, justificantes, citas, seguimientos, avisos y reportes. Bloquear publicación de notas internas; cache por identidad/grant.
- Docente: clases SOLO asignadas por Admin, QR de clase y retardo con revisión, canal, calificaciones por esquema 100%, CSV/XLSX validado y gateway escolar real solo si existe.
- Admin: flujo completo personas/roles, grupos/aulas/periodos, asignación docente/tutor, horarios revisables, QR escolar, auditoría y métricas respaldadas por API; sin construir segundo módulo de clases.
- Super Admin: experiencia propia, políticas/roles, delegación, integración, auditoría, MFA para cambios privilegiados; no acceso indiscriminado a notas tutoriales.
- **Gate:** matriz CRUD/lectura por cada rol y alcance; altas, traslados/revocaciones y sesión antigua no conservan acceso; backend y UI reflejan mismo estado.

### F5 — Invitados, talleres y cursos (P1/P2, #119/#120/#121)

**Responsables:** API workshops, Compose workshops, Admin.

- Propuesta→evaluación→invitación con token opaco de un uso y hash→grant por evento→publicación→inscripción con cupos/lista de espera→sesiones/materiales→cierre→constancia verificable/revocable.
- Invitado no adquiere rol `TEACHER` ni ve asistencia/expedientes escolares; participante externo no se convierte en estudiante institucional.
- QR/taller independiente, expiración del acceso y privacidad de roster; constancias de participación sin equivalencia académica implícita.
- LTI/Open Badges son integraciones FUTURAS, nunca casilla marcada por existir un diseño.
- **Gate:** 8 experiencias de V10 (estudiante, docente, tutor, coordinación, admin, superadmin, facilitador, participante externo); test cross-role y revocación a mitad de sesión.

### F6 — Observabilidad, seguridad de suministro y release (transversal/P0 final)

**Responsables:** infra/CI, QA, seguridad, producto.

- Registrar trazabilidad de actor/scope/acción/recurso/outcome/requestId (sin tokens/contraseñas/expedientes en logs); health/readiness y métricas verdaderas; excepciones 409/401/403 comprensibles.
- CI: exigir pruebas realmente ejecutadas, lint, instrumentación, API smoke, secret scan, matriz de permisos, generación de SBOM/SCA, dependabot o equivalente, revisar pin de GitHub Actions a SHA, firma de APK y checksums.
- Build desde HEAD limpio, versionCode monotónico, endpoint release explícito, proveedor escolar real o degradación segura, rollback de backend y migraciones expand→migrate→switch→contract.
- Validación móvil física por lo menos de alumno y docente con dos teléfonos; ampliar por roles antes del piloto institucional.
- **Gate RC:** 0 P0/P1 de integridad/acceso sin tratar, 100% de escenarios críticos documentados con PASS o excepción explícita aprobada, hash APK+SHA, UI QA, política de retención/privacidad y aceptación institucional.

## 5. Dependencias y ruta crítica

```text
F0 integración/QA (PR #124, #112, #123)
    ├─ F1 autorización/sesiones ─┬─ F4 tutor/admin/superadmin ─┐
    │                            ├─ F5 invitaciones/talleres ──┤
    │                            └─ F2 QR escolar/clase ───────┤
    └─ F3 navegación/PDF/UI ────────────────────────────────────┤
                F6 CI + seguridad + dispositivos + RC <─────────┘
```

F2, F3 y documentación pueden avanzar en paralelo en **ramas no solapadas**. PR #123 requiere resolver las modificaciones de attendance de #124. F5 requiere políticas de F1 y aprobación en Admin de F4. No hacer de una integración institucional externa una dependencia de login nativo ni de talleres.

## 6. Contrato Git y coordinación

- Rama documental de esta auditoría: `docs/v10-1-hardening-master-audit-20261008`, abierta desde `main@5f678fd...`, PR SOLO docs.
- Issues existentes: #113 épico general; #114 F0; #115 F1; #116 tutor; #117 admin; #118 superadmin; #119 invitado; #120 talleres; #121 constancias/release; #125 ingreso escolar offline; #74/#98/#92/#101 triage. Añadir subtareas a issues existentes, no abrir duplicados. Si hay un defecto nuevo independiente, crear issue con reproduccion, severidad, dueño y aceptación.
- Ramas por funcionalidad (derivadas SIEMPRE del `origin/main` más reciente): `fix/v10-f0-ci-gates`, `fix/v10-f0-schedule-pdf`, `feat/v10-authz-scopes`, `feat/v10-presence-offline-review`, `fix/v10-navigation-accessibility`, `feat/v10-tutor-complete`, `feat/v10-admin-operations`, `feat/v10-superadmin-security`, `feat/v10-guest-invitations`, `feat/v10-workshops-core`, `feat/v10-workshops-android`, `test/v10-rc-e2e`.
- **Prohibido:** push directo a main, force-push de main, mezclar documentación de plan con ejecución de 8 módulos, remergear ramas obsoletas, borrar carpetas sin comparación, autocerrar issues por compilar, merge de un PR con checks faltantes/fallidos, secretos/PII en capturas.
- Plantilla de PR: `Baseline SHA / issue / qué cambia y qué NO / UX antes-después / impacto rol API+Android / contratos+schema / migración y rollback / tests positivos+negativos / dispositivos y capturas / riesgos residuales / SHA final+checks`.
- DoD por módulo: ningún recurso huérfano según V15; rutas en composición, DI, service, repository, producer/consumer, tests, UX, privacidad, telemetría y permisos.
- Se recomienda squash merge tras review y checks verdes, actualizar issue, reconsultar MAIN SHA y reproducir la regresión antes de iniciar el siguiente paquete.

## 7. Matriz de validación y evidencia

Aplicar [matriz de aceptación V10.1](../quality/V10_1_TRACEABILITY_ACCEPTANCE_MATRIX_20261008.md). Para cada escenario: ID, rol, entorno, dataset *sintético*, SHA de commit, versión Android/API, pasos, resultado esperado/actual, captura/log redactado, estado `NOT_RUN/FAIL/PASS/BLOCKED`, issue/PR.

Capas obligatorias:
1. Unitarias de dominio y ViewModel (incluidos casos negativos y roles).
2. API integración con Mongo real de test, requests falsificadas, concurrencia y error envelopes.
3. Android instrumentación en emulador real; entradas, backstack y contraste.
4. Dispositivo físico/red intermitente y 2 móviles para QR cuando sea pertinente.
5. Compatibilidad cliente API anterior, invalidación de cache, restart del proceso y release configurado.
6. Revisión seguridad: OWASP API authorization, secretos, logs/PII, SCA y evidencia de permisos.

## 8. Indicadores y gobierno

| Indicador | Meta de aceptación | Evidencia |
| --- | --- | --- |
| P0 críticos abiertos antes de RC | 0 | issues + QA formal |
| Exposición entre usuarios/grupos/roles | 0 escenarios de bypass en batería definida | pruebas API negativas |
| Asistencias offline autopromovidas a VERIFIED | 0 | pruebas + DB audit |
| Rutas top-level/Back críticas por experiencia | 100% PASS en matriz | instrumentación/vídeo |
| Botones inertes / métricas inventadas | 0 en rutas de producción auditadas | inventario UI |
| Lectura horario 9° A | 7 días visibles; bloques esperados sin traslapes ni profesor/materia invertidos | fixtures redactadas y prueba PDF real |
| CI de SHA final | API, Android, lint, instrumentación y seguridad verdes | enlaces a jobs |
| Incidentes de privacidad expuestos en QA | 0 capturas/logs con credenciales o notas restringidas | auditoría de evidencias |
| Orfandad de módulos/rutas | 0 en código integrado | checklist V15 y build |
| QA real / release | RC ligada a SHA+hash+versión y evidencia de device | manifiesto RC |

**Criterio de salida del plan:** no basta completar documentos o dar merge. Cada fase tiene un owner funcional/técnico, evidencia verificable y aprobación; bloqueos externos se clasifican separadamente y producen un modo degradado honesto. La versión V10.1 del plan **no cambia** `versionName/versionCode` de la app sin PR específico.
