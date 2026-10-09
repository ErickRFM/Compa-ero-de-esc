# V10.1 — Matriz de trazabilidad y aceptación (QA + seguridad + UX)

> Fecha de baseline: 2026-10-08 hora México. `main@5f678fdcd6bbf423c7c217fa0d1f73ded2054974`. **Plantilla operacional, no reporte de pruebas ejecutadas.** Cada PASS requiere logs/capturas del commit exacto y fecha; CI anterior o PR redactado no es evidencia de esta corrida.
>
> Plan rector: [V10.1 Master Hardening](../product/V10_1_MASTER_HARDENING_EXECUTION_PLAN_20261008.md). Épico base: [#113](https://github.com/ErickRFM/Compa-ero-de-esc/issues/113).

## 1. Estados y severidad

- `NOT_RUN`: no hay prueba ejecutada en HEAD. `PASS`: evidencia con SHA y entorno y aserciones completas. `FAIL`: comportamiento no cumple. `BLOCKED`: infraestructura/API escolar/datos reales/SDK indisponibles; registrar el bloqueo sin convertirlo en PASS. `NOT_APPLICABLE`: justificación aprobada.
- **P0:** alteración/falsificación de asistencia verificada, permisos cruzados, filtración de datos, producción/mock, autenticación crítica o corrupción de datos. **P1:** operación de clase/tutor/admin bloqueada, PDF incompleto y navegación básica rota. **P2:** fidelidad visual menor, optimización de UX o mejora posterior.
- Todo escenario de riesgo debe probar al menos **caso positivo + denegación + concurrencia/replay/offline** cuando aplique; escenario `NOT_RUN` no certifica requisito.

## 2. Matriz de aceptación por ID

| ID | Prioridad | Rol/recorrido a verificar | Resultado esperado | Issue/PR | Estado inicial |
| --- | --- | --- | --- | --- | --- |
| GIT-01 | P0 | Rebase de #124 sobre main actual | Sin conflictos silenciosos; API/Android/CI completos | #124, #114 | NOT_RUN |
| GIT-02 | P0 | `check-whitespace.sh` #124 | Corregida línea en `AdminHomeScreen.kt:160`; no otros errores; jobs reejecutados | #124 | FAIL en HEAD observado de #124 |
| GIT-03 | P1 | #112 y #123 reconciliados por archivo | Sin perder F0; política QR de review preservada | #112, #123 | NOT_RUN |
| AUTH-01 | P0 | JWT emitido antes de quitar rol docente | Toda escritura/lectura de clase ajena denegada inmediatamente tras revocación | #115 | NOT_RUN |
| AUTH-02 | P0 | Sesión revocada, cuenta desactivada, token generación antigua | `401/403` según contrato y limpieza de cache/contexto; ninguna ruta privada responde datos | #115 | NOT_RUN |
| AUTH-03 | P0 | A alumno intenta consultar matrícula/expediente de B | Respuesta denegada, sin metadatos privados ni diferencias enumerables | #115 | NOT_RUN |
| AUTH-04 | P0 | Tutor grupo A → expediente grupo B y notas INTERNAL | Denegación; solo `STUDENT_VISIBLE` llega al alumno dueño | #115, #116 | NOT_RUN |
| AUTH-05 | P0 | Admin de escuela A solicita datos de escuela B | Denegación por institución, sin bypass por ID en URL/cuerpo | #115 | NOT_RUN |
| AUTH-06 | P1 | Login, registro, refresh y recuperación bajo carga/NAT | Limitador de abuso compartido sin bloquear a toda una escuela ni permitir bypass trivial | #115 | NOT_RUN |
| AUTH-07 | P1 | Promoción admin / roles / grants | Solo actor autorizado con step-up; auditoría; no autoelevación | #118 | NOT_RUN |
| AUTH-08 | P1 | Logout, reset/cambio de contraseña, cambio de cuenta | Refresh/session invalidados, no reutilización de datos privados offline | #115 | NOT_RUN |
| QR-01 | P0 | Entrada escuela online QR + red | Política aplica doble filtro y evidencia diferenciada; servidor decide | #114 | NOT_RUN |
| QR-02 | P0 | Cliente manipula SSID/BSSID en request | Evidencia declarada no se trata como ubicación independientemente atestiguada | #115, #125 | NOT_RUN |
| QR-03 | P0 | QR pared capturado sin Wi-Fi y app reiniciada | `PENDING/REVIEW_REQUIRED`, outbox escolar idempotente, jamás VERIFIED automáticamente | #125 | NOT_RUN |
| QR-04 | P0 | Docente pierde Wi-Fi durante sesión | Rotación/pase anticipadamente firmado funciona dentro de vigencia; capturas quedan para revisión | #123 | NOT_RUN |
| QR-05 | P0 | Reloj manipulado, QR copiado, expirado, revocado o de otra clase | Rechazo o revisión conforme política; audit trail, sin presencia válida inventada | #123, #125 | NOT_RUN |
| QR-06 | P0 | Profesor corrige retardo/ausencia | Cambia decisión final, conserva evidencia original, actor/motivo/fecha | #114 | NOT_RUN |
| QR-07 | P1 | WorkManager sync duplicado, red oscilante, logout | Un único intento canónico; no fuga entre usuarios; reintentos acotados | #123, #125 | NOT_RUN |
| UX-01 | P1 | Estudiante `Hoy→Horario→Hoy` | Un solo Home correcto, una pulsación efectiva, sin overlay | #114 | NOT_RUN |
| UX-02 | P1 | Docente `Hoy→Clases→Canal→Atrás` | Contexto grupo asignado restaurado, no Canal persistente de otra clase | #101 | NOT_RUN |
| UX-03 | P1 | Tutor y Admin regresan desde secundaria | Home correcto de su experiencia, tabs/Back sin salto a estudiante | #116, #117 | NOT_RUN |
| UX-04 | P1 | Cambio de experiencia y logout/login | Navegación/drafts/cache sin contaminación de rol anterior | #115 | NOT_RUN |
| UX-05 | P1 | PDF digital 9° A desde Document Picker real | PDFBox→geometría→7 días→materia/docente correctos; sábado 07–09; review editable antes guardar | #112, #74 | NOT_RUN |
| UX-06 | P1 | PDF escaneado, texto arbitrario, archivo corrupto y sin headers | OCR fallback explícito o error útil; no guardar datos mezclados ni preview vieja | #112 | NOT_RUN |
| UX-07 | P1 | Horario con conflictos/edición/movimiento | Días no se superponen; cambios persisten con cancelación/undo y origen | #74 | NOT_RUN |
| UX-08 | P1 | 360/390/430dp, tabletas, 200% y TalkBack | Botones 48dp, lectura/foco lógico, controles visibles y reduced motion | #101 | NOT_RUN |
| UX-09 | P1 | AdminHome con API offline / sin métricas | Jamás muestra «Servicios OK», «0 alertas» ni «0 alumnos» inventados | #124, #117 | NOT_RUN |
| TUTOR-01 | P1 | Asignación/revocación tutor en mitad de sesión | Borrado o inaccesibilidad inmediata de scopes y notas privadas | #116 | NOT_RUN |
| TUTOR-02 | P1 | Solicitud justificante→revisión→nota interna/publicación | Solo respuesta permitida llega al alumno; estado auditado | #116 | NOT_RUN |
| TEACH-01 | P1 | Esquema 100%, CSV/XLSX, duplicados, calificación inválida | Previsualización/corrección; no enviar antes de confirmar; errores claros | #101 | NOT_RUN |
| TEACH-02 | P1 | API escolar de calificaciones no integrada | Estado `UNAVAILABLE` honesto; ningún «sincronizado» falso | #101 | NOT_RUN |
| GUEST-01 | P0 | Invitado sin grant intenta leer clase, notas y QR escolar | Denegación; no hereda TEACHER/ADMIN | #119 | NOT_RUN |
| GUEST-02 | P1 | Invitación expirada/reutilizada/cancelada | Token opaco hash single-use; sin reactivación ni enumeración | #119 | NOT_RUN |
| EVENT-01 | P1 | Taller cupo final con inscripciones concurrentes | Sin sobrecupo ni inscripciones duplicadas; waitlist coherente | #120 | NOT_RUN |
| EVENT-02 | P1 | Invitado pierde asignación a evento | Pierde roster/materiales restringidos y cache correspondiente | #119, #120 | NOT_RUN |
| EVENT-03 | P1 | Constancia verificada/revocada | Datos públicos mínimos, no declara crédito oficial, revocación visible | #121 | NOT_RUN |
| CI-01 | P0 | CI API/Android del SHA candidato | Compile, pruebas realmente ejecutadas, lint, minified release, instrumentación y secret scan | #114, #121 | NOT_RUN |
| CI-02 | P1 | Vulnerabilidades en dependencias, Actions y SBOM | SCA, version pinning/renovaciones, bloqueo de critical sin excepción firmada | #115, #121 | NOT_RUN |
| RC-01 | P0 | APK RC en equipo real y segundo teléfono para QR | Firma/endpoint/hash SHA, flujos estudiante/docente, backup y rollback definidos | #121 | NOT_RUN |

## 3. Pruebas negativas de seguridad imprescindibles

### Identity/authorization

1. Tokens con `roles` de hace 14 minutos tras revocación de tutor/docente.
2. Cambiar únicamente `groupId`, `studentId`, `courseId`, `institutionId` y `eventId` en requests válidas de otro usuario.
3. Cuenta multirol que selecciona experiencia tutor pero llama endpoints docentes sin asignación; pasar de TEACHER_PENDING a TEACHER por manipulación del cliente.
4. Concurrencia en `refresh`, replay de refresh rotado y logout mientras una petición privada sigue en curso.
5. Consultas de tutor después de revocación con Room/cache previo y tras reiniciar proceso.

### Asistencia/evidencia

1. Copiar fotografía del QR de pared en otra ubicación y modificar SSID/BSSID en JSON.
2. Escanear código de otro plantel; token antiguo, reloj adelantado, clase cancelada, ventana cerrada.
3. Varios dispositivos envían misma `operationId` o variantes con misma evidencia; revisar idempotencia.
4. Suspender app durante sync, reconectar, cambiar usuario y abrir caso tutor; no filtrar información anterior.
5. Profesor cambia ausencia a presente sin borrar la evidencia original ni falsear la verificación de campus.

### Datos/archivos

1. PDF digital de tabla, PDF escaneado, imagen, archivo no admitido, más páginas/tamaño que límite, rotación, celdas superpuestas y caracteres unicode.
2. CSV/XLSX con filas extensas, datos duplicados, fórmulas inesperadas, celdas mal formadas; límites de memoria/tamaño.
3. Logs/capturas y respuestas 4xx/5xx revisadas: nunca tokens, contraseñas, notas internas ni expedientes completos.

## 4. Dispositivos, ambientes y matriz UX

| Entorno | Mínimo | Evidencia |
| --- | --- | --- |
| JVM/API | tests aislados, Mongo de prueba, conflicto/concurrencia | reportes y `requestId` redactado |
| Compose unit tests | ViewModel, roles, loading/empty/offline, backstack policy | JUnit con conteo real |
| AVD API 30/31 | 360×800, 390×844, 430×932; 768×1024 y 1024×768; 200% texto | screenshots + UI hierarchy + SHA |
| Teléfono Android real | estudiante, docente, permisos, DocumentsUI, batería, regreso Inicio | video/capturas sanitizadas, versión/SHAs |
| Dos dispositivos Android | QR docente, estudiante offline, Wi-Fi caído, servidor recuperado | estados finales backend y auditoría |
| Render development | `APP_ENV=development` con mocks solo de desarrollo; Mongo, auth, timeouts | health/ready + contract test |
| Preproducción/piloto | providers auténticos o estado explícito `BLOCKED`, datos autorizados, firma release | acta de aceptación y riesgos |

## 5. Evidencia mínima adjunta al PR

```text
Issue:
Rama/base SHA:
Head SHA:
API build/version:
Android versionCode/versionName:
Entorno y dispositivo:
Comandos reproducibles:
Tests ejecutados / fallos / skipped:
Escenarios QA IDs y estados:
Logs y capturas redactados:
Migración y rollback:
Riesgos residuales:
Revisor QA:
Decisión: READY_FOR_REVIEW | CHANGES_REQUESTED | BLOCKED
```

- **Sin evidencia = NOT_RUN**. Un documento de arquitectura no convierte una función en implementada; un snapshot del emulador no demuestra que el backend actualizó Mongo.
- GitHub Actions cancelado por carreras de pushes/billing, o artefactos no subidos, **no** equivale a `PASS` ni a fallo funcional; registrar la causa.
- Para RC exigir SHA actual de `main`, checks de *ese mismo SHA*, APK firmado y manifiesto hash, QA de dispositivo y revisión de seguridad.

## 6. Referencias de trabajo existentes

- [#113 Épico V10](https://github.com/ErickRFM/Compa-ero-de-esc/issues/113), [#114 F0](https://github.com/ErickRFM/Compa-ero-de-esc/issues/114), [#115 scopes](https://github.com/ErickRFM/Compa-ero-de-esc/issues/115), [#116 Tutor](https://github.com/ErickRFM/Compa-ero-de-esc/issues/116), [#117 Admin](https://github.com/ErickRFM/Compa-ero-de-esc/issues/117), [#118 Super Admin](https://github.com/ErickRFM/Compa-ero-de-esc/issues/118), [#119 invitado](https://github.com/ErickRFM/Compa-ero-de-esc/issues/119), [#120 talleres](https://github.com/ErickRFM/Compa-ero-de-esc/issues/120), [#121 RC](https://github.com/ErickRFM/Compa-ero-de-esc/issues/121).
- [#112 PDF digital](https://github.com/ErickRFM/Compa-ero-de-esc/pull/112), [#123 contingencia QR clase](https://github.com/ErickRFM/Compa-ero-de-esc/pull/123), [#124 asistencia/Admin](https://github.com/ErickRFM/Compa-ero-de-esc/pull/124), [#125 QR escuela offline](https://github.com/ErickRFM/Compa-ero-de-esc/issues/125).
