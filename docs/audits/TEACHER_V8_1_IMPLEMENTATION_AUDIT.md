# Docente V8.1 — implementación y verificación

Fecha: 2026-10-08. Proyecto Android nativo Kotlin/Compose. Las cinco imágenes suministradas son referencias de composición, no fondos de pantalla ni fuentes de datos.

## Integración Git

Checkout inicial `dfc1db323ed9a2159d03970719c24cfb4f0100a0` (#100). Main actualizado e integrado: `d91b57b` (#102), incluyendo #94, #95, #97, #99 y #96. También se integró la cabeza `c3f46b3` de #103. Los PR #104/#105 y #89/#91 pertenecen al trabajo de tutoría y no se incorporaron por esta tarea.

Rama de ejecución: `codex/teacher-v8-1-integration-20261008`. Se conservaron los cambios locales previos de superficies compartidas; existe una copia adicional y el stash `teacher-v8-1-preserve-preexisting`. No se descartaron los cambios del usuario. Los conflictos de Login se resolvieron conservando el diseño actualizado de main y reutilizando `V8CampusBackdrop`.

Commits: `006c6fe` (contexto de clase y auditoría), `cbaafb9` (main), `3dd24b9` (#103), `81acaa1` (JSON, aislamiento, importación y pestañas) y `6e25c7d` (contraste de tarjetas/scaffold e indicadores compactos). Fuente de APK y matriz visual: `6e25c7d50c828738ebae3e6478fc2b09205620c1`. Los commits posteriores de evidencia no cambian el código de la app.

## Comportamiento implementado

| Vista | Comportamiento y fuente de datos |
|---|---|
| Inicio | Pase vigente prioritario, siguiente clase que todavía no termina, métricas consultadas, padrón, accesos y agenda del horario autorizado. Sin datos recibidos muestra un estado pendiente, no un cero inventado. |
| Asistencia | Sesión real y QR firmado con caducidad; filtros independientes; filas lazy; datos de entrada escolar separados del resultado de clase; revisión con motivo y autor asignado por servidor. |
| Mis clases | Solo asignaciones activas administrables del docente autenticado. No permite crear clases. Acciones transportan `TeacherClassContext` y conservan materia/grupo. |
| Evaluación | Clase autorizada, periodo, esquema de actividades que suma 100%, importación XLSX/CSV, columnas y filas detectadas, incidencias y confirmación previa. Cambiar clase limpia el borrador anterior. |
| Canal | Publicación del docente en el ID nativo autorizado, compositor plegable, avisos/materiales y respuestas predeterminadas. Cambiar o perder la asignación limpia borradores y publicaciones. |

Se mantiene la navegación de cinco pestañas del contrato docente existente: Hoy, Horario, QR, Clases y Canal. Evaluación sigue siendo un destino secundario con Volver. La restauración de stacks dejó de abrir Canal al tocar Clases; una pestaña retirada vuelve a su raíz y reinicia su scroll. Una pestaña todavía activa conserva su ViewModel.

Las peticiones de un grupo anterior se cancelan y sus respuestas se descartan. Un usuario con ambos roles no pierde el contexto docente por una respuesta tardía del modo alumno. La sincronización bloquea edición y doble envío mientras está en curso. Todos los cuerpos de los recorridos docentes usan `Content-Type: application/json`.

## Evidencia y revisión administrativa

`originalStatus` y `originalReasonCode` conservan el resultado recibido. Cada revisión agrega autor, fecha del servidor, motivo y resultado a `reviewHistory`; Mongo serializa estos campos y los documentos antiguos admiten valores ausentes. Una decisión administrativa de presente/tarde/ausente no fabrica ni reemplaza evidencia de Wi-Fi, ubicación o QR. La excepción docente sigue siendo una decisión trazable.

El backend rechaza reabrir la misma ocurrencia si su pase está cerrado o caducado. El cliente retira QR vencidos, deja de mostrar un pase caducado como activo y no reutiliza un código anterior cuando pierde conexión.

## Importación y sincronización

Se rechazan encabezados duplicados, matrícula ausente, archivos sin columnas de calificación, extensión incompatible y CSV con comillas sin cerrar. Se informa de matrículas duplicadas/vacías, celdas vacías, filas de ancho incorrecto y notas no numéricas, no finitas o fuera de 0–10. CSV acepta BOM, comillas escapadas, CRLF y campos multilínea. XLSX mantiene el parser XML seguro existente.

Un archivo fallido elimina la previsualización anterior. Las incidencias y los esquemas incompletos impiden enviar. La pantalla muestra hasta 20 filas e indica el total; `.xls` antiguo no se admite. La sincronización solo comunica éxito si el servicio lo confirma. La integración institucional de notas instalada es `UnavailableGradeSyncGateway`: el resultado correcto es integración no disponible; no se publicaron notas en un sistema escolar real.

## Verificación técnica

126 pruebas Android, 166 pruebas API y 23 shared: cero fallos, errores o pruebas omitidas en los XML de las tareas Gradle. Se verificaron aislamiento, cuenta multirrol, retirada de asignación, importación fallida, encabezados/CSV, esquema inválido, envío pendiente, permisos, auditoría y caducidad. La regresión de pase caducado falló antes del fix y pasó después. Gradle reutilizó tareas sin cambios; no se afirma que todas se ejecutaran sin caché.

Fuente final: debug/tests/lint/AndroidTest correctos en 3m46s y release R8 en 4m29s. Instrumentación final tras cerrar sesión por el perfil: `LoginV8SmokeTest`, **OK (2 tests)**, 19.675s. Total contabilizado: 317. El SHA-256 de `base.apk` obtenido del AVD coincide con el APK local: `3AF65C56803C7B443CA62253EB54D799596AA2D5D8F1037569C9A1ABBFA5AEA4`.

Comando completo utilizado en Windows:

```powershell
$env:COMPANERO_API_BASE_URL = 'http://10.0.2.2:8080/'
$env:COMPANERO_RELEASE_API_BASE_URL = 'https://compa-ero-de-esc.onrender.com/'
.\gradlew.bat testDebugUnitTest :shared:contracts:test :shared:validation:test :services:api:test :apps:android:app:lintDebug :apps:android:app:assembleDebug :apps:android:app:assembleRelease :apps:android:app:assembleDebugAndroidTest --no-daemon --no-configuration-cache --max-workers=2
```

Lint: cero errores y ocho advertencias (`OldTargetApi`, `ObsoleteSdkInt`, `RedundantLabel`, `UnusedResources`). Persisten avisos de deprecación de Gradle. Los builds debug, release con R8 y AndroidTest terminan correctamente. La instrumentación de Login usa textos del diseño actual; la primera versión de las comprobaciones dependía de una cadena multilínea retirada por main.

## QA visual real

Capturas locales en [v8-evidence/teacher-v8-1](v8-evidence/teacher-v8-1). Cada PNG tiene XML y metadatos técnicos. Los PNG/XML permanecen fuera de Git: la revisión automática rechazó publicarlos por posible exposición de nombres/identificadores. Se usa AVD API 30 con ADB, sin inyección de sesión ni imágenes simuladas. El helper rechaza jerarquías obsoletas y exige la cabecera antes de capturar. El PR registra los metadatos y resultados; las imágenes originales se entregan desde el workspace.

La cuenta de desarrollo Elena se autentica por la interfaz. Control Escolar crea dos asignaciones en **localhost** a partir del proveedor académico de desarrollo: Álgebra Lineal 101-A e Historia Contemporánea 204-B. Un intento del alumno de desarrollo queda como evidencia `likely / identity_session_time`; no se simula una entrada física validada. Estos datos no son defaults añadidos a la app ni se enviaron a producción.

El campus y el logo son los assets reales del repositorio; difieren del edificio y retrato de las referencias. Se priorizan contraste, jerarquía y controles accesibles. Los teléfonos usan scroll natural para la agenda, las filas y los pasos de importación; no se reduce la fuente para encajar todo en una imagen.

La revisión detectó texto oscuro heredado dentro del pase. `V8GlassCard` y el scaffold ahora proporcionan color claro de contenido. La evidencia final incluye ese fix y los indicadores horizontales que admiten etiquetas multilínea.

| Tamaño dp | Fuente | Vistas | Clases → Canal → Clases |
|---|---:|---:|---|
| 360 × 800 | 1.0 | 5 | Correcto |
| 390 × 844 | 1.0 | 5 | Correcto |
| 430 × 932 | 1.0 | 5 | Correcto |
| 768 × 1024 | 1.0 | 5 | Correcto |
| 1024 × 768 | 1.0 | 5 | Correcto |
| 360 × 800 | 1.3 | 5 | Correcto |

La revisión manual cambió el registro de desarrollo a Presente, conservó `Probable · identity session time` y mostró `Último ajuste: T-0001 · QA_revision_administrativa_local`. Captura adicional: `flows/attendance-reviewed.png`. Es una revisión administrativa local, no una validación física.

En Evaluación se introdujo periodo y actividad 100% y se abrió el selector con un CSV real. DocumentsUI mostró el archivo y MIME soportado `text/comma-separated-values`, pero no completó la selección mediante ADB. **La selección/importación completa y el intento de sincronización por UI quedan pendientes de comprobación física**. Parser, limpieza de preview y respuesta UNAVAILABLE se verificaron con pruebas de servicio/ViewModel, que no sustituyen una prueba manual completa.

Un revisor independiente examinó el diff final sin bloqueantes de código o seguridad. Su revisión estática no verifica persistencia remota ni publicación institucional.

## Archivos y componentes

Inventario completo del código respecto a main: `v8-evidence/teacher-v8-1/changed-files.txt`. Incluye trabajo auditado de #100/#103 y superficies compartidas previamente modificadas por el usuario.

| Componente | Cambio |
|---|---|
| `MainActivity`, `TeacherClassContext` | Argumento tipado nuevo para conservar identidad autorizada y retorno correcto a pestañas. |
| `V8Glass`, `CompaneroScaffold` | Componentes existentes reutilizados; contraste explícito e indicador compacto opcional. |
| `TeacherHomeScreen`, `AttendanceScreen`, `AttendanceViewModel` | Inicio conectado, caducidad, cancelación y revisión trazable. |
| `TeacherClassesScreen` | Asignaciones y acciones autorizadas, sin crear clases. |
| `ChannelScreen`, `ChannelViewModel` | Compositor plegable, aislamiento y publicación JSON. |
| `GradebookScreen`, `GradebookViewModel`, parser | Importación, previsualización y borradores separados. |
| Contratos/servicios/repositorios de asistencia | Autor, motivo, evidencia original persistida y rechazo de pase vencido. |

Se reutilizan logo, campus, glass, scaffold, navegación y repositorios existentes. No se agregó motor UI, chat libre ni proveedor institucional ficticio.

## Límites de la validación

La QA local usa repositorios en memoria; no prueba una conexión Mongo de producción ni la integración institucional ausente. Falta la validación en un teléfono físico y en la red escolar real. El release ensamblado está sin firma de distribución; el candidato instalable usa la firma debug y se entrega para validación, no para una tienda.

El endpoint HTTPS respondió HTTP 200, pero `/health` a las 21:41:34 UTC informó `status: degraded` y `mongodb: down / unreachable`. HTTP 200 no significa backend sano. No se modificaron Render, Atlas ni variables de producción; el conector exige confirmar workspace para su diagnóstico. **Aceptación final y validación física contra ese backend bloqueadas hasta resolver Mongo y comprobar los recorridos pendientes**.

Los APK son artefactos de pruebas con SHA, no una declaración de release aprobado. Instrucciones: [TEACHER_V8_1_WINDOWS_VALIDATION.md](../ui/TEACHER_V8_1_WINDOWS_VALIDATION.md). Manifiesto: `v8-evidence/teacher-v8-1/BUILD_PROVENANCE.json`.

El APK instalable de validación utiliza el endpoint HTTPS configurado del proyecto. El APK local para emulador utiliza `10.0.2.2`; ambos deben identificarse por hash y no confundirse. No se incluyen claves, sesiones, contraseñas ni fixtures privados en el repositorio o en el APK.
