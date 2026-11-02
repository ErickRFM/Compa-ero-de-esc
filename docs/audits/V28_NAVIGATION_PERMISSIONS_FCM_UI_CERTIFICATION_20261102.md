# Compañero V28 — Auditoría técnica transversal y criterios de certificación

**Fecha:** 2026-11-02. **Alcance:** Android Académica y Gestión, Ktor, MongoDB, FCM, navegación, permisos, identidad, diseño V25. **Estado:** diagnóstico y remediación parcial en PR #266. Este informe **no certifica** CI verde, APK release, push real ni despliegue productivo.

## 1. Fuente de verdad y resultados

- Diseño aprobado: `docs/design/V25_VISUAL_MASTER_PLAN.md` y `docs/design/V25_VISUAL_ACCEPTANCE_MATRIX.md`. Se conserva Compose, barra flotante carbón, borgoña y componentes V8, en lugar de introducir un segundo tema.
- Navegación: `MainActivity.kt`, `AcademicRoutePolicy.kt`, `RoleExperience.kt`, `CompaneroScaffold.kt` y `TopLevelNavigation.kt`.
- Permisos: `ChannelAccessPolicy`, `AttendanceAccessPolicy`, `AttendanceAdministrationPolicy`, `ClassroomService` y `InstitutionScopePolicy`. La visibilidad de botones en Compose no concede permisos.
- Push: `PushDeviceRegistrar`, `CompaneroFirebaseMessagingService`, `FcmHttpV1Gateway`, `PushOutboxService`, `AttendancePushEligibility`.
- Persistencia: `MongoChannelTaskRepository`, `AttendanceRepository` y `ClassroomRepository`.
- Render: producción `Compa-ero-de-esc` estaba live desde el commit `db71e84b` de `main`. Los commits V28 no estaban en ese despliegue al revisar. No se encontró confirmación de entrega física FCM en los logs consultados.

## 2. Defectos identificados y correcciones ya en PR #266

| Riesgo | Evidencia | Cambio en la rama | Validación pendiente |
|---|---|---|---|
| **P0:** nota de tarea desaparece tras recargar | El modelo admitía `score`, pero `MongoChannelTaskRepository` omitía serializarlo | Persistencia de nota e historial, caso de integración Mongo | Ejecutar test real Mongo y verificar lectura desde app |
| **P0:** prueba de Gestión cae al renderizar Clases | `InstitutionalClassesUiTest` registró `No ActivityResultRegistryOwner` | Selector de documento condicional cuando el RegistryOwner existe | API 31 y 35 en ambas variantes |
| **P1:** notificación Canal abre destino fuera del perfil actual | MainActivity consumía intent sin resolver rol/variante | `AcademicRoutePolicy.channelExperience`, pruebas de invitado, docente pendiente, roles combinados | Cold start, sesión revocada, sin cuenta, Gestión/Académica |
| **P1:** Canal en segundo plano sin click_action explícito | Gateway FCM solo especializaba asistencia/cambios SIII | Intent y manifest `OPEN_CHANNEL` + test payload | FCM real cerrado/segundo plano en ambos teléfonos |
| **P1:** importar archivo mientras se cambia de clase mezcla estados | Selector retenía estado de importación y borraba datos antes de respuesta | `pickerClassId`, indicador cargando, retención de borrador para reintento | Cambiar de clase, archivo corrupto, denegado, rotación, doble clic |
| **P1:** pasar lista manual requería ID opaco | Roster original devolvía solo registros, no todos los alumnos elegibles | `eligibleStudents` autenticados y acciones por fila, sin inferir presencia | Alumno revocado, otro tenant, sesión cerrada, corrección concurrente |
| **P2:** cinco pestañas pequeñas en shell Académico | V25 prioriza cuatro destinos visibles | Cuatro destinos para alumno/docente; Clases y Perfil accesibles contextualmente | Capturas 360/390/430 dp, 200% texto, modo oscuro y back |
| **P2:** padrón extenso dentro de tarjeta | Renglones de clase sin contenedor acotado | Vista limitada en tarjeta y diálogo desplazable para lista completa | TalkBack, 200% texto, teclado y orientación |

## 3. Matriz de permisos indispensable

| Actor | Canal | Asistencia | Listas | Tareas / notas |
|---|---|---|---|---|
| Alumno activo y verificado | Solo sus aulas vigentes | Confirmar pase propio si sesión y presencia cumplen regla | No puede importar membresías | Solo entregas y nota propia |
| Docente activo, asignado | Publicar en su aula vigente | Abrir/revisar/marcar de forma manual en sesión propia nativa, con auditoría | Añadir cuentas verificadas en misma institución; NO matrícula SIII | Crear y revisar su tarea |
| Docente pendiente | Sin publicación docente | Sin sesión docente ni marcación | Sin importación | Sin revisión docente |
| Administrador institucional | Solo ámbito y capacidad vigente | Facultades administrativas según política del recurso; no falsificar QR | Operaciones institucionales conforme RBAC, no usurpación de docente | Lectura/revisión según política de aula |
| Invitado | Solo lo explícitamente concedido | Sin asistencia de clase | Sin importación | Sin datos privados |
| Otra institución / sesión revocada | Prohibido | Prohibido | Prohibido | Prohibido |

**Contrapruebas obligatorias:** HTTP 401/403; modificar `studentId`, `sessionId`, `classroomId`, `taskId`, `submissionId`, `institutionId`, rol y origen `NATIVE/INSTITUTIONAL`; revocación posterior a encolar FCM; idempotencia y CAS de nota/asistencia; no acceso cruzado por caché al cambiar de usuario.

## 4. FCM real, no solamente pruebas de mocks

El sistema implementa **transporte potencialmente real**, pero `PushDeliveryResult.ACCEPTED` significa aceptación del gateway, **no recepción ni lectura de Android**.

- Confirmar `FIREBASE_CONFIGURED`, coincidencia de proyecto Firebase, package/flavor Academic y Management, canales Android, permisos POST_NOTIFICATIONS y token registration de un dispositivo identificado de pruebas.
- Verificar flujo Ktor -> Mongo outbox -> FCM v1 -> token de dispositivo -> `FirebaseMessagingService` o recepción de notificación del SO -> click -> destino autorizado -> consulta API -> estado del recurso.
- Probar app abierta, segundo plano, terminada, sesión caducada, permiso denegado, token rotado, dispositivo desinstalado, sin Wi-Fi, doble push y usuario que cambia de rol o institución.
- Las notificaciones específicas de **tarea publicada, devolución, entrega evaluada y plazo** no están aún conectadas a `ChannelTaskService`. Abrir incidencia propia con idempotencia, privacidad, destinatarios por aula y revalidación al entregar.
- Incorporar un diagnóstico administrativo seguro de eventos en cola/aceptados/reintento/rechazados, sin exponer tokens en logs ni prometer “entregada” cuando FCM solo aceptó el mensaje.
- La pantalla de notificaciones y contador de no leídos requiere fuente durable del servidor, no el número de FCM recibidos.

## 5. UX, alineamiento y navegación

Revisar **Hoy, Horario, Clase, Canal, Tareas, Pase, Perfil y Administración** como un único flujo por rol. Criterios:
- Encabezado y barra inferior únicos, sin navegación secundaria duplicada; back coherente de detalle -> lista -> inicio.
- Una acción primaria por pantalla, secundarios agrupados, sin diez botones uniformes; formularios importantes preferentemente en sheet, con borrador conservado al error.
- Componentes/tokens V8 ya compartidos: `V8ScreenHeader`, `V8GlassCard`, `V8RedPrimaryButton`, `StatusNotice`, `CompaneroSpacing`, `CompaneroMotion`. Corregir nuevas vistas que mezclen botones Material crudos sin intención.
- Estados **cargando/vacío/denegado/sin red/reintentando/éxito real** por operación. Una UI no puede mostrar “Listo” antes de un ack de Ktor.
- A11y: contraste, 48dp de objetivos, orden TalkBack, etiqueta de botón y campo, entrada con IME, 200% texto, reducción de movimiento, alto contraste. La revisión de código por sí sola NO sustituye capturas ni lector de pantalla.
- Fidelidad V25: comprobar PNG original, Día/Noche, 360/390/430dp, tablets, 200% letra, barra flotante, contenido bajo IME, animaciones respetando reduced-motion. La miniatura del repositorio no basta para certificar píxeles.

## 6. Trabajo bloqueante de madurez pendiente

1. **P0:** CI verde backend, unit, ktlint, instrumentación Académica y Gestión API 31/35, release APK; solucionar causas reales en logs, no silenciar suites. No fusionar PR #266 mientras haya fallos.
2. **P0:** E2E en QA real MongoDB de membresía, pase manual, historial, notas Mongo, aislamiento multiinstitución y doble request.
3. **P0:** prueba física FCM real en ambas APK, revalidación por actor y recurso; cobertura de tareas, canal y avisos.
4. **P1:** entrada de lista provisional solo por nombre, Excel XLSX, PDF escaneado/OCR y reconciliación segura con identidad verificada sin crear matrícula oficial.
5. **P1:** publicación de horarios compartidos y sincronización limitada con SIII; propuestas y autorización explícita, sin duplicar ERP.
6. **P1:** consolidar notas privadas de tareas en gradebook nativo, criterios/rúbricas, permisos y optar por exportar a SIII solo tras reconciliación.
7. **P1:** coherencia UI: capturas por rol, recortar tarjetas largas, tipografía/responsividad, jerarquía, estados, scroll/IME, dark, animaciones y TalkBack.
8. **P2:** trazas de entrega/métricas, navegación deep link de tarea y badge real no leído, QA de desempeño/cold start y permisos denegados.

**Criterio para merge:** todas las suites requeridas en verde en el último SHA; revisión de PR y regresión de privacidad; evidencia QA de los flujos con capturas y HTTP; sin simulación de datos oficiales; rollback preparado. **Criterio para producción:** merge con versión, build reproducible de ambas APK, deploy/health y pruebas FCM/identidad/autorización sobre el despliegue real.
