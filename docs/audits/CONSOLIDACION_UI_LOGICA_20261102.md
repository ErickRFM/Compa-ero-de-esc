# Consolidación Git y auditoría UI/lógica — 2 noviembre 2026

**Base:** `cd721f1dedb48138b485d73654b5389b7866ba9c` (PR #266). **Rama de trabajo:** `codex/consolidacion-auditoria-20261102`.

Este corte reemplaza las conclusiones de estado del README y documentos históricos V5/V10. La implementación es mucho más amplia que esas descripciones, pero **no toda la lógica de producto está terminada ni toda la interfaz está certificada visualmente**. Las correcciones de este lote están verificadas localmente; los pendientes de producto se detallan por separado.

## 1. Consolidación y preservación

- Árbol inicial limpio en `main@db71e84b`; `fetch --all --prune` y avance rápido a `cd721f1d` sin reescribir historial.
- PR funcional #266 ya fusionado. Diez PR abiertos corresponden a dependencias/tooling y se mantienen fuera del cierre funcional.
- Se revisaron 343 referencias locales/remotas, contando duplicados: 104 ancestros directos, 158 asociados por nombre con PR fusionados, 10 dependencias, cuatro backups/recovery y 67 históricos sin correspondencia directa. Estos últimos **no representan 67 funciones faltantes**; no se realizó comparación semántica exhaustiva de cada generación antigua.
- No se borraron ramas, backups ni worktrees. `git cherry` no basta para detectar pendientes cuando hubo squash.

| Rama o grupo | Evidencia | Decisión |
|---|---|---|
| V28 manual rollcall/classroom | #266; ancestro de main | Integrada |
| Consolidación Gestión V27 | #264 → `db71e84b` | Integrada |
| iOS/KMP foundation | #127 → `0849469f`; archivos propios idénticos al corte fusionado | Integrada por squash; conservar referencia |
| Recuperación Mongo TLS | #246 → `3c725690`; ocho archivos iguales al corte fusionado | Integrada por squash |
| V24 asistencia E2E | #243 → `a02fe200`; implementación central conservada | Integrada por squash |
| Gemini V26 y backups visuales | #248/#249/#251 y endurecimiento IME posteriores presentes | Histórica; fusionarla reintroduciría código superado |
| Home tasks / HMAC dual-key | Cambios equivalentes presentes, varios reforzados después | Sin merge adicional |
| V12 gradebook/persistencia/guest | #184–188 y evolución posterior | Preservar refs; evitar reintegrar backups |
| Auth WIP local | Mezcla cambios antiguos y formato con autoridad ya evolucionada | Preservar; no fusionar completa |
| `f906392a` / `e7487ac2` | Revalidación del operador después de consumir MFA ausente en la base | Recuperar semánticamente con regresiones |
| Dependabot | Diez PR de actualizaciones | Validar en su propio ciclo |

## 2. Mapa funcional contrastado en código

Las rutas de esta tabla son relativas a `apps/android/feature/` y `services/api/`. «Implementado» describe código conectado; no certifica un despliegue ni cuentas institucionales reales.

| Área / rol | Flujo conectado | Estado y límite |
|---|---|---|
| Acceso y sesión | Login/Registro/MFA → SessionViewModel/repositorios → auth/admin/security → cuentas/sesiones Mongo | Implementado; claims y distribuciones gobiernan destinos. Revocación durante consumo MFA requiere la corrección de este corte |
| Hoy alumno/docente | HomeScreen/TeacherHomeScreen → repositorios académicos/asistencia → academic/attendance → proyecciones, horarios y sesiones | Implementado; separa dato institucional, manual y personal |
| Horario y Clases | Schedule/Classroom → repositorios → academic/classroom → snapshots/grupos/membresías | Lectura, edición autorizada y espacios nativos presentes; edición manual no equivale a publicación SIII |
| Asistencia | Scanner/confirmación/Pase docente → AttendanceRepository → AttendanceRoutes/ReviewService → Mongo/outbox | QR firmado, cola offline, elegibles y decisiones manuales auditadas; CAS y permisos presentes |
| Padrón | Selector CSV/TXT/PDF/XLSX → NativeRosterImportReader/XlsxReader → ClassroomRepository → importMembers/listMembers | Solo identidades verificadas; límites y validación XML/ZIP. Sin OCR escaneado ni alta provisional por nombre |
| Canal y tareas | ChannelScreen → ChannelViewModel/Repository → ChannelTaskService/file routes → Mongo/private files | Creación, entregas privadas, revisión, nota 0–10 e historial implementados |
| Calificaciones | Gradebook/StudentGrades → repositorio → GradebookService → Mongo | Libro nativo presente; publicación oficial SIII **no disponible** (`UnavailableGradeSyncGateway` inyectado) |
| Tutoría | TutorHome/Groups/Requests/Representatives → repositorios → tutoring/representatives → Mongo | Alcance asignado y privacidad presentes; requiere aceptación por cuentas/roles reales |
| Coordinación/Dirección/Gestión | Destinos resueltos por RoleExperience/AppDistribution → coordinación/admin/classroom → policies/MFA → Mongo | Rutas existentes; Dirección comparte superficie de coordinación. No hay consola independiente de Dirección ni auditoría administrativa inmutable completa |
| Notificaciones | Ktor → PushOutbox → FCM → servicio/intent Android → política de experiencia → Canal/Pase | Transporte y reautorización presentes. Aceptación FCM no significa entrega/lectura física; intención negocio+outbox no es atómica |
| Apariencia/accesibilidad | Perfil/Apariencia → preferencias → tema/motion compartidos | Claro/oscuro/alto contraste/texto/movimiento reducido; falta aceptación visual completa por rol |
| SIII | Adapters/projection/outbox/historial autorizado | Integración limitada presente; propuestas oficiales de horario y publicación de notas no deben presentarse como terminadas |
| iOS | `iosApp`/`multiplatform` y CI propios | Fundación preservada; fuera de la certificación visual Android de este corte |

## 3. Defectos demostrados y remediación

| ID | Gravedad | Problema de la base | Corrección / evidencia al cierre |
|---|---|---|---|
| SEC-01 | P1 | Revocar al operador durante consumo de prueba MFA deja un snapshot autorizado obsoleto antes de aprobar/suspender | Revalidación después de MFA; dos regresiones que exigen target sin cambios |
| UI-01 | P1 | Hojas de composición/detalle de tarea sin scroll; acciones inaccesibles con listas largas, teclado o texto ampliado | Contenedores desplazables y regresiones Android del alcance de acciones de creación, revisión y entrega al 200%; no certifican envío real ni teclado físico |
| UI-02 | P2 | Docente no ve nota guardada al pasar entrega a REVIEWED | Mostrar nota persistida junto con la revisión; regresión Android |
| UI-03 | P2 | Porcentaje de categoría derivado directamente de Double impide borrar o completar un decimal | Borradores de texto y validación antes de guardar/publicar |
| UI-04 | P2 | Pestañas de Canal de ancho fijo partían palabras con texto al 200%, comprobado en captura | Ancho de contenido y desplazamiento horizontal con selección visible; regresión de una línea completa |
| PERF-01 | P1 | Importación de gradebook lee archivo completo sin límite antes de validar y procesa en hilo UI | Lectura acotada y trabajo de archivo/parser fuera de UI; regresiones de límites |
| DATA-01 | P1 | Tras guardar una revisión, editar pesos o importar otro archivo deja publicar la revisión anterior aunque la UI muestra datos nuevos | Marcar cambios sin guardar y exigir guardar de nuevo antes de publicar; regresión ViewModel |
| NAV-01 | P2 | Push de tarea de clase B abre primer/último canal A porque se descarta channelId | Propagar identidad del canal y seleccionar solo entre canales autorizados vigentes |
| CI-01 | Bloqueo de validación | El corte integrado falla ktlint en FCM, roster y otros imports/expresiones de los módulos revisados | Formato del repositorio en los módulos afectados, sin relajar reglas ni cambiar aserciones |

## 4. Pendientes reales que no resuelve el emulador

| Prioridad | Pendiente y evidencia | Criterio de cierre |
|---|---|---|
| P1 | `ChannelTaskService` confirma la escritura de tarea/entrega/revisión antes de encolar y captura errores de enqueue. Un fallo previo a insertar outbox puede perder la alerta permanentemente | Intención de aviso persistida atómicamente con negocio o transacción + reconciliador; probar fallo de enqueue, reinicio, reintento, deduplicación y revocación |
| P1 | Publicación oficial de notas usa `UnavailableGradeSyncGateway` en ApplicationModule | Contrato/adaptador SIII autorizado, reconciliación y E2E; no confundir notas privadas de tareas con calificación oficial |
| P1 | Propuesta/aprobación oficial de horarios `schedule:propose` ausente; editor actual produce `ADMIN_MANUAL` | Flujo aprobado en SIII, respuesta firmada y reconciliación; ver cierre Gestión del 2026-11-01 |
| P1 | FCM Gestión requiere configuración específica y entrega física en ambos paquetes | Dispositivo/cuenta QA autorizados, foreground/background/app cerrada, token rotado, permiso denegado y revocación |
| P2 | `ClassroomService.listMembers` usa membresía directa; autorización docente también reconoce alumnos heredados del grupo | Definir y unificar padrón/conteo sin inventar fecha de incorporación; prueba alumno por grupo sin membership directa |
| P2 | PDF roster no incorpora OCR ni altas provisionales; lectura PDF no comprueba cancelación entre todas las páginas | No anunciar formatos/identidades no soportados; añadir cancelación cooperativa y flujos de reconciliación si se implementan |
| P2 | Avisos de vencimiento, contador no leído durable y recibos de entrega/lectura no completados | Eventos persistidos y UI derivados del servidor, sin badge simulado |
| P2 | Diseño V25 original PNG no está incorporado; miniatura insuficiente para fidelidad de píxeles | Comparación visual con referencia completa y capturas reales por estado/rol |
| P2 | Canal/Tareas y Calificaciones contienen numerosos textos en español directamente en Compose | Externalizar copy y comprobar cambio de idioma; no certificar cobertura EN completa a partir del selector de idioma |

## 5. UI/UX revisada y siguiente pasada visual

Se inspeccionaron acceso/registro/MFA/onboarding, Hoy alumno/docente, Horario, Clases, Canal/Tareas, Pase, Perfil/Apariencia/Integración, Tutoría y Administración/Coordinación. Se conserva Compose, tema V8/V25 mate, borgoña, navegación por experiencia y primitivas comunes.

La revisión estática comprueba estructura, scroll, estado, roles y uso de componentes; **no acredita** contraste medido, TalkBack real, clipping de todos los textos ni fidelidad visual. Las pruebas sintéticas de UI no son cuentas reales ni integración productiva.

Secuencia de afinado posterior:

1. Académica y Gestión, cuentas alumno/docente/tutor/admin y docente pendiente; sesión nueva y restaurada.
2. 360/390/430 dp, claro/oscuro/rojo, texto normal/200%, teclado y rotación.
3. Hoy → Horario → Clases → Canal/Tareas → Pase → Perfil, vuelta atrás y cambio de rol sin conservar datos ajenos.
4. Vacío/cargando/offline/error/401/403/409/429/5xx; reintentos y mensajes de confirmación solo tras ack.
5. Listas largas de alumnos/entregas, importación inválida, selección de archivo lenta, cambio de clase durante I/O.
6. Notificación de clase distinta, arranque frío/caliente y sesión caducada.
7. Dispositivo físico para cámara, presencia, notificaciones, segundo plano y conectividad; el emulador no sustituye esas verificaciones.

## 6. Verificación y estado de entrega

La última comprobación global terminó con `BUILD SUCCESSFUL`, 1364 tareas y salida 0. Se reutilizaron resultados válidos de Gradle; se ejecutaron de nuevo las regresiones y tareas afectadas por las últimas modificaciones. Registro local: `build/audit-delivery-verified-20261102.log`.

| Grupo | Casos registrados | Ejecutados sin fallos | Omitidos |
|---|---:|---:|---:|
| API con Mongo real local | 694 | 691 | 3 |
| Shared | 28 | 28 | 0 |
| Bibliotecas Android | 357 | 357 | 0 |
| App Académica unit | 7 | 7 | 0 |
| App Gestión unit | 7 | 7 | 0 |
| Layout Tareas, emulador API 30 | 4 | 4 | 0 |
| **Total** | **1097** | **1094** | **3** |

También pasaron `ktlintCheck`, `lint` y la compilación de ambas APK debug. La revisión independiente de los diffs de seguridad, estado, navegación, UI y runner CI terminó sin hallazgos pendientes dentro del lote corregido. Los problemas de producto de la sección 4 permanecen abiertos.

Comando reproducible, con JDK 21 y `V12_TEST_MONGODB_URI=mongodb://127.0.0.1:27017` apuntando exclusivamente al Mongo desechable local:

```powershell
.\gradlew.bat :apps:android:feature:grading:testDebugUnitTest :services:api:test :shared:contracts:test :shared:models:test :shared:validation:test testDebugUnitTest :apps:android:app:testAcademicDebugUnitTest :apps:android:app:testManagementDebugUnitTest ktlintCheck lint :apps:android:app:assembleAcademicDebug :apps:android:app:assembleManagementDebug :apps:android:app:connectedAcademicDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=org.companerodeescuela.ChannelTaskLayoutUiTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --continue --console=plain --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

Las nuevas cuatro pruebas son obligatorias en CI para **Académica/Gestión × API 31/35**, sin omisiones, con exportación de tres PNG validados. El estado de CI y SHA de integración se consultan en el PR asociado a esta rama; no se hereda el CI verde de otro commit.

### Evidencia visual local

Capturas Android directas, 1080 × 2340, emulador API 30, texto al 200%, transporte y usuarios **sintéticos**. Se inspeccionaron los PNG; la captura inicial reveló palabras partidas en las pestañas y motivó la regresión adicional. Las capturas enlazadas son posteriores a esa corrección.

- [Revisión docente y acciones alcanzables](evidence/20261102/teacher-review-200.png).
- [Entrega del estudiante tras instrucciones largas](evidence/20261102/student-submit-200.png).
- [Composición de tarea y pestañas con palabras completas](evidence/20261102/task-composer-200.png).

Estas pruebas verifican alcance por scroll y renderizado de nota/etiquetas; no ejecutan publicación, revisión ni entrega real y no acreditan IME/TalkBack ni toda la matriz visual de la sección 5.

### APK locales de QA

Sin distribución productiva ni prueba física. Las rutas se mantienen bajo `apps/android/app/build/outputs/apk/` y los binarios no se incorporan a Git.

| Variante | Ruta relativa | SHA-256 |
|---|---|---|
| Académica debug | `academic/debug/app-academic-debug.apk` | `ca3271d6cab46b9a4686c04c7dbe8a1fcf8e2838f60bc774ed1fd99e6cfb3eb1` |
| Gestión debug | `management/debug/app-management-debug.apk` | `2fb3985a1c87936089b877fc8414df6bf38ffcf0a72af33dbf151c554e353cbb` |

- Exportador de evidencia: 10 pruebas Python pasan; el primer intento en sandbox falló por permisos de temporales, repetición con acceso normal satisfactoria.
- Configuración Firebase Académica: prueba PowerShell acepta Academic y rechaza Management/sender discordantes sin imprimir configuración.
- Base `cd721f1d`: CI API falló y se reprodujeron localmente defectos ktlint; [la matriz Android previa](https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/41510386490) terminó con sus cinco trabajos satisfactorios. Esa evidencia no sustituye el CI del nuevo commit.
- Un daemon Gradle terminó inesperadamente durante compilación; comprobaciones posteriores limitan workers/memoria y conservan el fallo ambiental en los logs locales.
- API con Mongo local: 694 casos registrados, 691 ejecutados sin fallo y tres omitidos. Las omisiones son `SiiiLiveContractTest`, `SiiiPushLiveContractTest` y `SiiiFullChainLiveContractTest`: requieren un laboratorio Next/PostgreSQL SIII de loopback y sus variables sintéticas, no configurados en esta ejecución. No se sustituyeron por éxito ni se deshabilitaron pruebas.
- Las dos regresiones de revocación durante consumo MFA pasaron en la suite API completa; antes de la corrección ambas fallaban porque la operación se completaba.
