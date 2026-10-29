# V20 — Bitácora única de integración y certificación

Fecha: 2026-10-28, America/Mexico_City. Base: `55a1b99780ddef2cb2e9add7f82f6d6124965628`.
Especificación: misión V20 aportada por el usuario. Ejecución continua autorizada, reutilizando Android Compose/Ktor/Mongo y los contratos existentes.

## Plan y dependencias

- [x] A: inspeccionar main, documentos locales y PR 219/220/232. Conservar todos los untracked previos sin incluirlos en commits.
- [x] B: portar 219/220 semánticamente: experiencia activa, Dirección Management, capacidades mínimas y autorización viva por institución. Responsable: revisión/implementación roles. Archivos: navegación, MainActivity, auth/perfil, contratos roles/administración, coordination/admin y sus pruebas.
- [x] C: cerrar Android FCM: acción explícita tras deep link, canal y tag comunes, errores de registro, historial persistente desde servidor. Responsable: implementación notificaciones/Android. Archivos: feature/core attendance y app notifications; no MainActivity.
- [x] D: añadir historial propio limitado y autorizado en Ktor/Mongo. Contrato: GET /attendance/records/mine → ApiResponse<List<AttendanceRecordResponse>>, sin selector de alumno, máximo 100, más recientes primero, identidad/tenant/inscripción actuales. Responsable: backend historial.
- [x] E: integrar regresión 232 y corregir replay HMAC con reloj adelantado; completar payload FCM y corregir workflow inválido. Responsable: agente principal; no cambiar secretos.
- [x] F: validación local completa: Kotlin/shared, Mongo aislado, lint/build, Compose API30 y cadena real de laboratorio con lectura SIII. API31/35 remotas aprobaron el bloque anterior; la nueva revisión requiere CI de su SHA.
- [ ] G: revisión independiente del diff final; commits funcionales, push, PR y checks. Merge únicamente tras checks aprobados. Confirmar SHA de main y generar APK/hash desde ese SHA.
- [ ] H: publicar artefacto y matriz Poco X7 Pro; distinguir emulación, hardware y dependencias externas.

Dependencias: B/C/D/E → F → G → H. C consume endpoint de D; E define canal/tag compartidos con C. Solo el agente principal ejecuta Gradle y modifica Git. Se conserva identidad UPTlax, controles de QR institucional y estado REVIEW_REQUIRED; confirmación no certifica presencia.

## Evidencia inicial

- 219 abierto, conflictivo: portar guardas de experiencia sin perder deep link V19.
- 220 abierto: no fusionar carrusel antiguo (símbolos inexistentes/when incompletos/403 Dirección). Reutilizar componentes de main y conectar capacidad explícita.
- 232 abierto: test HMAC válido, CI API falla en ktlintTestSourceSetCheck. Android PR run 40537439418 success.
- Informe local de activación SIII del 25/oct acredita firmas/cron, pero entonces había cero secciones/alumnos/docentes fuente. No acredita 9A ni recepción FCM física actual.
- Los informes locales anteriores no son evidencia nueva y no se modifican credenciales ni datos productivos para fabricar aceptación.

## Revisión y límites

Revisar especialmente cambio de cuenta/rol, repetición/reinicio, revocación de grupo, reloj adelantado y entrega duplicada. Toda desviación y resultado se registra aquí. Estado de liberación: AMARILLO por Firebase/datos9A/dispositivos pendientes. Los SHA, CI y artefactos finales se registran en la entrega verificable; no existe certificación física.

## Cambios y evidencia verificada

- PR219 portado conservando deep links; PR220 adaptado sin su carrusel incompatible. PRINCIPAL solo en Management y supervisión con capacidad explícita, sin gestión implícita.
- GET `/attendance/records/mine`: estudiante actual, máximo100, sesiones cerradas, orden estable, no-store y autorización vigente por institución/inscripción. Mongo con índice para alumno/fecha.
- Cobertura de fase D: se añadió el puente de consulta desde SIII. POST `/integration/v1/attendance-history` exige HMAC y `attendance:read`, resuelve matrícula dentro del tenant firmado, revalida identidad y autoridad fuente y devuelve máximo100 registros sin identidad nativa, notas, revisores ni evidencia de dispositivo. La credencial de escritura no puede leer; la de lectura no puede enviar eventos. SIII expone GET `/api/v1/institutions/{institutionId}/companion-attendance/{studentId}` a lectores institucionales con `academic.manage`, revalidando alumno, inscripción, versión y ocurrencias después de la consulta y auditando solo su alcance y cantidad.
- Incremento lector verificado: API635 casos,632 ejecutados sin fallos y3 optativos; estilo global aprobado. SIII1005 unitarias y8HTTP nuevas sin fallos, typecheck/lint/build aprobados. Cadena real aislada:2/2 sin skips, fuente SIII `a1c29dfae53876e00f2e8dd7eb71a17c18205a40` más incremento lector → PostgreSQL/outbox → Ktor/Mongo → pase y cierre → GET SIII200 → retiro de inscripción → historial404 y lectura propia403 antes de entregar la proyección. Envío a sesión cerrada409 con código exacto y registro sin cambios. Gateway push falso; esta evidencia no certifica FCM físico. Revisión independiente del puente sin defectos P0/P1/P2 confirmados.
- Historial Android independiente del refresco del pase; cancelación y verificación de usuario/session_id/modo impiden publicar una respuesta antigua. Error transitorio conserva el historial; revocación lo retira.
- Notificación abre contexto y exige el botón de envío. Estado REVIEW_REQUIRED permanece diferenciado de presencia validada por docente. QR de clase sigue como contingencia.
- Tag por sesión y canal compartidos para SDK Firebase/background y servicio foreground. Registro de dispositivos distingue rechazo, sesión y fallo transitorio; Perfil muestra diagnóstico y acceso a ajustes.
- Indicadores institucionales reales: pases abiertos y revisiones pendientes, muestra de hasta1000 sesiones recientes explícita; campos ausentes no se presentan como cero.
- HMAC: nonce retenido hasta timestamp+skew+1. Regresión RED→GREEN demuestra que un reloj adelantado no permite replay al caducar prematuramente la reserva. Coexistencia de claves, revocación y firma con clave equivocada antes de consumir nonce.
- Historial SIII: prueba RED registró903 lecturas para100 sesiones; contexto por solicitud y revalidación final pasan la regresión de <=15 lecturas, retiro de autoridad y fallo del proveedor. No existe caché compartida entre solicitudes.
- Sondas de lectura autorizadas 28/oct: projection-status firma válida200/contrato correcto, inválida401; autoridad SIII referencia inexistente404/SOURCE_IDENTITY_UNAVAILABLE, inválida401. Sin escrituras académicas ni secretos en salida. Primer intento agotó tiempo; reintento tras salud200 pasó.
- Escáner de secretos PASS y sus23 controles negativos PASS. Helper Firebase acepta configuración sintética Academic, rechaza Management y sender inconsistente, sin imprimir valores.
- Revisión independiente final de código: ningún defecto P0/P1/P2 confirmado. Cobertura adicional posible: mismo alumno con solo session_id renovado durante respuesta histórica tardía; código incluye guarda, pruebas actuales cubren cambio de cuenta y modo.

Incidencias del entorno: el formateo global alteró archivos ajenos. Se retiraron68 cambios exclusivamente cosméticos, comprobando hashes y con revisión independiente; no se restauraron archivos funcionales V20 ni documentos del usuario. El auto-review rechazó la primera reversión genérica; aprobó la alternativa acotada con esa evidencia. Los siguientes formatos se ejecutan con la CLI sobre rutas explícitas. Un daemon Gradle ocioso retenía classes.jar en Windows; se cerró y la ejecución funcional se limitó a un build a la vez.

## Roles y alcance

Validación local del bloque funcional `76dcd8a20351b47efdd6b6bb909f1fb4c77b0e43`: build integral SUCCESS, 628 casos API (625 ejecutados, tres optativos pendientes), 618 ejecuciones unitarias Android sin fallos/skips; lint Academic/Management con0 errores y43 avisos cada uno. Ambos APK Debug y ambos APK de instrumentación compilan. Los avisos no se desactivaron. Compose API30 local, API31/35 remoto y cadena SIII aislada se registrarán separadamente en la evidencia final.

| Rol | APK / experiencia | Alcance de servidor |
| --- | --- | --- |
| Alumno | Academic | Sus grupos vigentes, pase, historial propio, perfil y avisos autorizados |
| Docente | Academic | Grupos asignados, abrir/revisar/cerrar pase, contingencia QR y avisos |
| Dirección | Management | Institución actual y DIRECTIVE_SUPERVISION para indicadores; no MANAGE_USERS/APPROVE_STAFF implícitos |
| Coordinación | Management | Supervisión de la institución vigente y gestión ya autorizada por sus contratos |
| Tutor | Academic | Únicamente alumnos/casos vinculados y vigentes |
| Invitado | Público/legado explícito | Sin acceso académico concedido por una tarjeta ni por roles acumulados |
| Administración | Management | Institución exacta, capacidades vigentes y MFA para operaciones sensibles |
| Superadministración | Management | Capacidades administrativas explícitas, identidad vigente y MFA; sin eludir auditoría |

El alta de una identidad Dirección y su capacidad requiere provisionamiento institucional autorizado; este cambio no concede roles a cuentas reales ni agrega un atajo de asignación. SIII conserva pagos, calificaciones e historial académico oficial. Los datos de pruebas automatizadas residen en fixtures o bases desechables locales.

## Firebase pendiente: pasos exactos

El lector SIII necesita una credencial institucional autorizada con scope exclusivo `attendance:read` y su configuración privada `SIII_COMPANION_ATTENDANCE_TARGETS_JSON`, separada de los targets de escritura. No se concedieron scopes ni se cambiaron credenciales productivas. Ausencia de configuración devuelve503; no se presenta como un historial vacío. El formato y operación se documentan en el repositorio SIII. La consulta de expedientes reales9A continúa pendiente de esa habilitación y de identidades autorizadas.

Se buscaron archivos ocultos/ignorados en el repositorio completo, google-services.json y equivalentes, variables de proceso y nombres de variables/secretos de GitHub. No se encontró configuración reutilizable. El entorno GitHub android-release devolvió404; la firma productiva y sus valores no quedan acreditados. La arquitectura permite APK de diagnóstico con FIREBASE_CONFIGURED=false y diagnóstico visible; esta APK no recibe FCM.

1. En [Firebase Console](https://console.firebase.google.com/), abrir el proyecto institucional correcto → engrane → Configuración del proyecto → General → Tus apps → app Android cuyo paquete sea exactamente `org.companerodeescuela`. Si no existe, registrar ese paquete; no reutilizar `org.companerodeescuela.gestion`.
2. Descargar `google-services.json`. Instrucciones oficiales: https://firebase.google.com/docs/android/setup y https://support.google.com/firebase/answer/7015592.
3. Guardarlo localmente en `apps/android/app/google-services.json` (ignorado por Git), o fuera del repositorio indicando `-ConfigPath`.
4. Ejecutar `./infrastructure/scripts/Build-AcademicFirebase.ps1 -ValidateOnly`. Después ejecutar el mismo comando sin `-ValidateOnly` para generar Academic Debug con los cuatro valores de proceso temporales. El helper comprueba paquete, formato y coincidencia sender/appID; no imprime la configuración. `-BuildType Release` genera la release interna, sujeta al modo de firma ya existente.
5. En GitHub → Settings → Secrets and variables → Actions → Variables, configurar `COMPANERO_FIREBASE_APP_ID`=client coincidente/client_info/mobilesdk_app_id; `COMPANERO_FIREBASE_PROJECT_ID`=project_info/project_id; `COMPANERO_FIREBASE_SENDER_ID`=project_info/project_number; `COMPANERO_FIREBASE_API_KEY`=client coincidente/api_key/current_key. Los workflows Android ya consumen esas cuatro variables. Las compilaciones sin las cuatro quedan diagnosticadas; configuración parcial se rechaza.
6. El backend requiere su cuenta de servicio FCM del mismo proyecto bajo la configuración segura existente. No colocar esa clave privada en Android, JSON de app, Git ni logs. Management permanece sin FCM hasta disponer de su propia app Firebase.

## Instalación Poco X7 Pro y matriz física

Usar APK Academic identificado por SHA Git, SHA256 y certificado en la entrega; Management es coinstalable bajo `org.companerodeescuela.gestion`. Transferir el APK al Poco, abrirlo desde Archivos y permitir instalación desde esa fuente cuando Android lo solicite. Por USB con depuración autorizada: `adb install -r <apk>`. Si la firma instalada es distinta, no borrar datos automáticamente: usar un dispositivo de QA o conservar copia/plan de migración.

Preparación necesaria: Firebase QA configurado, docente/alumno de prueba autorizados, grupo9A y periodo/inscripciones reales autorizadas en fuente o laboratorio aislado. Nunca fabricar estos datos en producción para obtener evidencia.

| Caso físico | Resultado esperado | Estado |
| --- | --- | --- |
| Docente abre pase9A y alumno abre Asistencia sin aviso | Sesión persistida y visible aun si FCM no entrega | Pendiente dispositivos/datos autorizados |
| Primer plano / segundo plano / proceso terminado | Un aviso por sesión, canal correcto; tocar abre el contexto sin enviar | Pendiente Firebase y Poco |
| Alumno pulsa enviar | REVIEW_REQUIRED o rechazo real; nunca presencia verificada automática | Pendiente dispositivo |
| Docente revisa y cierra | Lista actualizada, decisión auditada e historial tras reinicio | Pendiente dispositivo/datos |
| Repetición, sesión caducada/cerrada, otro grupo/tenant | Sin duplicados; rechazo autorizado sin éxito ficticio | Automatización + físico pendiente |
| Sin red y reconexión | Estado pendiente/error; contingencia QR/outbox recuperable | Automatización + físico pendiente |
| Denegar permiso y restaurarlo en Ajustes | Diagnóstico visible y reintento de registro al volver | Físico pendiente |
| Cambiar cuenta/rol mientras carga historial | Sin filas ni acciones heredadas de otra cuenta/experiencia | Automatización + físico pendiente |
| Texto200%, pantalla pequeña, oscuro/claro, TalkBack, movimiento reducido | Lectura/acciones sin recortes ni dependencia de animación | Compose/QA visual + físico pendiente |

La evidencia física debe registrar modelo/Android/flavor, SHA Git, SHA256, fecha CDMX y resultado, sin tokens ni datos personales. La revisión estática, un emulador, un gateway falso o un build no certifican entrega FCM ni presencia física. La sincronización real9A y lectura móvil SIII siguen sin acreditarse con las sondas HMAC.
