# Compañero V25 — UI visual maestra aprobada (DailyFlutterUI-inspired)
**Fecha:** 2026-10-31 · **Propietario:** Compañero de Clase · **Plataforma:** Android nativo Kotlin / Jetpack Compose
**Estado de este documento:** especificación aprobada de diseño y plan de ejecución. **No** certifica pantallas implementadas ni CI.
**Referencia visual:** [vista previa Git](./references/companero-v25-approved-preview.webp). El original adjunto de **1122 × 1402 px**, SHA-256 `17846ec54fe528fd25c806a632b3bc1df639e04fdc7359f5c7dd9cc5c82b0737`, es la fuente de verdad del diseño. El WebP del repositorio es una copia reducida de 192 × 240 px para navegación rápida, **no** sustituye al original para comparación a píxel. Incorporar el PNG original bajo `docs/design/references/companero-v25-approved.png` antes del cierre visual.
**Inspiración externa:** DailyFlutterUI (Instagram @dailyflutterui_2001; portafolio `dailyflutterui-2001.github.io/my-portfolio/`; repositorio público `dailyflutterui-2001/Food-Delivery-App-UI`). Reutilizar **patrones** y componentes propios; no incorporar código/activos ajenos sin licencia comprobada.

## I. Mandato innegociable
1. Seguir la composición aprobada: tres pantallas conceptuales (splash, Hoy estudiante, detalle/control de asistencia), fondo marfil suave, contrastes carbón/borgoña, tarjeta dominante, acciones compactas, lista ligera y barra inferior flotante con píldora activa. **No sustituir por un diseño nuevo**.
2. Producción muestra **datos autorizados reales**; la imagen es un guion visual, no evidencia de que existan Ricardo, Olaf, 9A, horarios, aulas o 28/32 presentes. Esos textos solo se permiten en previews, QA sintética o documentación.
3. Mantener Android nativo + Compose + Hilt/Flows. No introducir Flutter, React Native, otra biblioteca de navegación, un tema paralelo ni un nuevo motor de animaciones.
4. **Primero reutilizar; después adaptar; crear nuevo solo si falta una primitiva.** Cada PR declarará qué componente reemplaza/reutiliza y qué se elimina.
5. El diseño no cambiará permisos, contratos de QR, autenticación, push, sincronización ni autoridad institucional. Compañero opera la experiencia; SIII administra lo académico y consume historial/indicadores autorizados.
6. No bloquear/mergear trabajo ajeno: al capturar el diseño, `main=9081df0ce9a8958f831724f52fbba1d28222fef1` y PR #243 está abierto, draft, y toca HomeScreen, TeacherHomeScreen, BottomBar y AttendanceScreen. **Rebasar tras #243** y resolver en vez de sobrescribir.

## II. Anatomía exacta de la imagen (fidelidad visual)
### Pantalla A — bienvenida / entrada
- Canvas claro casi blanco con muchísimo aire. Ilustración académica pequeña centrada en tercio central (birrete sobre libros), título Compañero y subtítulo de una o dos líneas. Progreso lineal discreto cerca del pie.
- Mantener este diseño para bienvenida / onboarding, **no crear un splash falso de varios segundos ni bloquear el inicio real**. El splash Android de sistema debe cerrar cuando corresponda; mostrar progreso solo si hay carga comprobable. Eliminar animación que aparenta avance sin dato.
- Logo/ilustración: preferir marca vectorial del proyecto (`core/designsystem/brand/UptlaxBrand.kt` y `res/drawable/uptlax_logo.xml`) y nuevos recursos originales mínimos; el póster no es un asset para incrustar como pantalla completa.
### Pantalla B — Hoy / Estudiante (la PRIORIDAD)
- Header: saludo discreto «Hola,» + nombre dominante + grupo/institución secundarios en una sola fila con chips pequeños; campana y avatar a la derecha. Mostrar avisos no leídos reales, sin punto rojo ficticio.
- Hero borgoña: eyebrow «Próxima clase» o «Clase actual» según hora real; materia dominante; docente, hora y aula; estado de asistencia/aviso autorizado. Altura contenida, radio generoso y superficie **sólida** (sin gradiente brillante). Ilustración de laptop solo si no compite con texto y hay licencia/origen propio.
- Accesos rápidos 4 en fila cuando caben: Horario, Asistencia, Avisos, Tareas. Ícono uniforme, contenedor pequeño, label debajo. En 360 dp y 200% font debe adaptarse a 2×2, scroll u otra composición **sin clipping**.
- Sección «Esta semana»: filas claras con icono en chip pálido, materia, fecha/hora/aula y chevron. No usar tabla ni panel enorme. Tap abre clase o calendario real; sin CTA muerto.
- Barra inferior flotante carbón: contorno de cápsula, selector interior claro «Inicio» y tres íconos inactivos. **Las rutas autorizadas siguen** Student: Hoy · Agenda · Clases · Perfil. Profesor: Hoy · Clases · Agenda · Perfil. La imagen con estadística es composición, no permiso para cambiar arquitectura de navegación.
- Datos/fallback: distinguir institucional publicado, personal importado, cached/stale, 401/403, vacío y error; jamás presentar horario personal como clase oficial.
### Pantalla C — Asistencia / clase activa
- Header borgoña con clase y docente, estado verde de «Clase iniciada» solo si la sesión real está abierta.
- Hoja inferior blanca con handle gris, título, explicación breve, área central QR, CTA borgoña principal y resumen de presentes.
- **Ajuste deliberado de lógica:** el póster mezcla lector, generador y confirmación. En la app separar:
  1) Docente con clase asignada inicia sesión mediante la comprobación/QR de aula que aplique, ve QR dinámico de la sesión solo si lo generó el servidor autorizado; rota antes de 15 s, TTL máximo 25 s.
  2) Alumno escanea evidencia QR de presencia si está habilitado. El escaneo no marca asistencia por sí solo.
  3) Con sesión abierta y alumnos elegibles, FCM ofrece «Pasar lista». Alumno confirma en un toque; el servidor valida identidad, inscripción, sesión, idempotencia, evidencia/tiempo; docente revisa lo que corresponda.
  4) «Confirmada», «Pendiente de revisión», «No disponible», «Sin conexión» o «Rechazada» se muestran solo desde estado confiable. FCM recibido ≠ asistencia confirmada.
  5) Docente ve lista/estado, no expone información de otros grupos; nunca serializar secreto HMAC QR ni mostrar el QR ilustrativo del póster como válido.
- QR en imagen de referencia es decorativo y **no** sirve como credencial.

## III. Sistema de diseño / tokens propuestos (ajustar con screenshot completa)
- **Canvas:** crema casi blanco; superficies: blanco/rosado ínfimo; cabeceras: carbón #232226; primario: borgoña muestreado en imagen aprox. #A91337 a #831328. La guía usa tokens existentes; **nada de HEX copiados en todas las pantallas**.
- **Jerarquía:** nombre 26–32sp bold; sujeto hero 21–26sp semibold/bold; section 18–20sp; body 14–16sp; metadata 11–13sp respetando escala accesible.
- **Espaciado:** 4/8/12/16/20/24/32 dp; gutters laterales 16–20dp; ritmo entre bloques 20–24dp; prohibir márgenes improvisados y padding doble.
- **Radio:** tarjetas hero 22–26dp; tiles 18–22dp; chips 12–16dp; nav 28–36dp; botones primarios pill o 18–22dp.
- **Elevación:** shadow tenue y realista en claro; en oscuro, contraste por superficie, sin outline luminoso. Sin blur excesivo, reflejos gruesos, halos ni glass sobre cada bloque.
- **Tipografía:** utilizar Typography.kt actual, refinando pesos/tamaños. No introducir un segundo kit de fuentes arbitrario. Iconos de familia y trazo homogéneos.
- **Temas:** Día igual al póster como base; Noche carbón de alto contraste; variante rojo institucional existente. Tema no parpadea al iniciar; respeto preferencia de usuario y accesibilidad.
- **Acento:** rojo como acción/hero, verde únicamente para estado semántico confirmado, no colorear todas las tarjetas.
- **Semántica:** contrastes ≥4.5:1 en texto normal cuando aplique, ≥3:1 en elementos UI; 48dp mínimo táctil; contenido con TalkBack; 200% font; movimiento reducido.

## IV. Motion grammar — estudiar DailyFlutterUI sin copiar efectos gratuitos
- Tap/chip/select: 120–220 ms; transición navegación 220–320 ms; sheet con scrim: 280–400 ms; entrada focal hero: ≤450 ms. Usar `CompaneroMotion` y `LocalCompaneroMotionPreferences`.
- Reusar la lógica «acción → estado → animación»: un cambio visual solo tras confirmación válida, o un estado pending explícito. Para aviso asistencia: pending visual sin prometer éxito, respuesta backend, confirmación/corrección.
- Entradas stagger pequeñas 40–75 ms solo tras cambio real de ruta; sin reanimar por cada recomposición. Nada de transición de 1 s en listas académicas.
- Bottom nav: selector desliza suave; conservar posición al cambiar tabs si el Navigation controller ya lo soporta, sin recrear estado ni descargar datos innecesariamente.
- Pull refresh y actualización de contador: transiciones limitadas, sin sacudidas/zoom; animaciones cancelables si cambia cuenta o rol.
- Reduced motion: saltar desplazamientos/parallax y mantener feedback instantáneo. Sin loop decorativo continuo.
- Conservar hit regions, scroll, foco y teclado durante cada animación.

## V. Inventario de REUSO obligatorio
| Función | Código actual (ruta base `apps/android/`) | Acción |
|---|---|---|
| Shell y tabs | `core/navigation/CompaneroBottomBar.kt`, `CompaneroScaffold.kt`, `TopLevelNavigation.kt` | Refinar selector y elevación; no introducir otra barra |
| Paleta/semántica | `core/designsystem/theme/{Color,Theme,Shape,Typography,ExpressiveTokens}.kt` | Ajustar tokens sin duplicar theme |
| Tarjeta hero | `core/ui/component/HeroAcademicCard.kt`, `core/designsystem/v8/V8StudentDashboard.kt` | Componer estado curricular, no hardcodear clase |
| Filas de clases | `core/ui/component/{AcademicClassCard,AcademicTimelineItem}.kt`, `core/designsystem/v8/V8StudentComponents.kt` | Compactar, reutilizar íconos/estados |
| Estados | `core/ui/component/{ContentStateHost,StatusNotice,CompaneroSurface}.kt` | Loading/empty/offline/error centralizados |
| Movimiento | `core/motion/{MotionTokens,MotionPolicy,MotionPreferences}.kt` | Única fuente de motion |
| Hoy estudiante | `feature/home/HomeScreen.kt`, `HomeViewModel.kt`, `TodaySchedule.kt` | Solo Compose/presentación; conservar autoridad del VM |
| Hoy docente | `feature/attendance/{TeacherHomeScreen,AttendanceViewModel}.kt` | Tarjeta sesión activa y controles docentes |
| Asistencia | `feature/attendance/{AttendanceScreen,AttendanceQrScanner,AttendanceUiPolicy}.kt` | QR evidencia + FCM un-tap separados |
| Agenda | `feature/schedule/{ScheduleScreen,ScheduleViewModel}.kt` | Layout mejorado sin cambiar fuentes |
| Avisos/canal | `feature/channel/{ChannelScreen,ChannelViewModel}.kt` | Reutilizar componentes de lista y badges |
| Perfil | `feature/profile/ProfileScreen.kt` | cabecera/atributos, respetar rol y permisos |
| Seguridad/roles | `core/navigation/{AppDistribution,RoleExperience,AcademicRoutePolicy}.kt` | INTTOCABLE autoridad; solo presentación |
| Verificación QA | `app/src/androidTest`, `core/designsystem/src/test`, `core/navigation/src/test` | expandir suites y golden screenshots |

## VI. Flujos reales y responsabilidades (no «demo que se ve bonita»)
- **SIII** gobierna institución, estudiante, docente, materia, sección/grupo, adscripción, horario académico, evaluación oficial y la consulta autorizada del historial/indicadores; nunca administrará el chat, QR, FCM ni el pase de lista móvil.
- **Ktor/Compañero** controla sesión de clase, pertenencia autorizada, evidencias de presencia, QR firmado, aviso FCM, confirmación un-tap, revisión docente, notificaciones y canales; genera historial trazable para consulta institucional autorizada.
- **Android** observa estados de ViewModel/Repository, muestra valores, lanza comandos por rutas existentes y maneja estados offline de modo explícito. No hace concesiones de rol ni inventa verificaciones.
- **Control de acceso**: un docente no crea la clase ni alumnos; tutor y visitante tienen superficies limitadas; admin y superadmin operan sus propios módulos, sin copiarlos en home Estudiante.
- **Canal docente:** contenido y avisos de grupo solo para grupos autorizados; no convertir el QR de presencia en un código público de ingreso al chat.
- **Compilaciones:** Academic y Management conservan su distribución, packageId, flags, notificaciones y destinos permitidos; roles múltiples se resuelven desde claims autorizados.

## VII. Fases ejecutables sin duplicaciones
**F0 — Freeze/inspección (sin cambios funcionales).** Registrar `main` y PRs; definir capturas actuales en 360/390/430 dp para Día/Noche/rojo; inventariar composables equivalentes y dependencias; verificar licencias de arte. Salida: matriz antes/después, baseline de tiempos, problemas priorizados.

**F1 — Tokens, superficies y barra.** Ajustar Theme/Color/Shape/Typography y V8RedEdition sin crear un tema nuevo. Construir sobre CompaneroSurface, V8GlassCard y CompaneroBottomBar (refinamiento, no reemplazo global). Dar funcionalidad a cada tab. Salida: captura fiel de navegación y componentes catálogo, dos temas.

**F2 — Hoy Estudiante fiel.** Header+avatar+chips; hero dinámica (próxima/en curso), cuatro acciones, «Esta semana», barra; estados vacío/cached/denied/loading y soporte 200% font. Actualizar solo HomeScreen y componentes necesarios; no meter red en Composables. Salida: screenshots y tests; nada hardcodeado en runtime.

**F3 — Hoy Docente propio.** Respetar mismo lenguaje pero acción prioritaria «Iniciar clase / Pase de lista», grupo/asignatura/horario reales y recursos docentes. La sesión debe pertenecer al docente; combinar con agenda/canal. Salida: interacción operacional y tests por rol.

**F4 — Pantalla de clase y asistencia.** Sheet y hero fieles, pero dividir generación QR docente vs scanner alumno vs deep link FCM y confirmación. Mostrar estados pendientes y revisión; no contadores ficticios. Salida: tests que separan presencia, push, confirmación y historia.

**F5 — Extensión visual.** Agenda Día/Semana, Clases y Canal, Avisos, Perfil, ajustes y guest; no cambiar APIs salvo defecto demostrado; reusar el mismo conjunto de tokens y componentes, y eliminar duplicaciones V8 obsoletas solo si no tienen consumidores.

**F6 — End-to-end/QA.** CI green unit + ktlint + instrumentación API 31/35, compilar Academic/Management, capturas 360/390/430 y 200% font, Día/Noche, TalkBack, landscape/keyboard, expiración QR, denied FCM, proceso terminado, rotación, offline, cambios de cuenta, 401/403, revocación de matrícula y grupo vacío. Dispositivo real sin inventar resultado.

**F7 — Cierre ordenado.** Rebase tras #243 y otras PRs, integrar por PR atómico, comparar pixeles/semántica, eliminar versiones sobrantes, actualizar docs, entregar HEAD exacto y evidencias. Fusionar solo con condiciones cumplidas; no cerrar trabajos de sincronización no verificados.

## VIII. Criterios de aceptación medibles
- `R-01` **Composición**: la jerarquía del póster se reconoce a primera vista (header, hero, 4 tiles, lista, barra). No se permite redistribución arbitraria.
- `R-02` **Visual**: comparación side-by-side contra PNG fuente a resolución/dispositivo equivalente; revisión por regiones, tipografías, alignments, espaciado y contraste. No declarar equivalencia 100% automática sin screenshot real.
- `R-03` **Navegación**: todas las acciones visibles abren destino real autorizado y back mantiene estado.
- `R-04` **Datos**: no se presentan textos de muestra en runtime salvo fixture QA claramente marcada; no se supone estudiante inscrito ni clase publicada.
- `R-05` **Asistencia**: QR evidencia != pase marcado; FCM != confirmación; fuente de verdad servidor, idempotencia y revisión.
- `R-06` **Roles**: matrículas, docentes, invitados, permisos y distributions no se amplían por UI.
- `R-07` **Accesibilidad**: 360 dp y 200% font no cortan CTA; TalkBack, 48dp touch, contraste claro/oscuro.
- `R-08` **Rendimiento**: scroll estable y sin coste de blur extremo, control de recomposición y métricas de tiempos antes/después.
- `R-09` **Calidad**: CI y Android instrumentation verde sobre SHA final; diferencias visuales registradas/justificadas; no regresión de API.
- `R-10` **Integración**: QA student/teacher con datos permitidos y traza SIII ↔ Ktor ↔ Android; distinguir simulación de prueba de producción real.

## IX. No hacer
- No reemplazar app con pantallas Flutter, screenshots como fondos, demos falsas o layouts estáticos.
- No tapar errores de sesión o matrícula con nombres y materias de la imagen.
- No meter nuevo set de cards, barras, CSS web, librerías UI o mega-Gradle dependencies por estética.
- No pasar lista automáticamente por escanear QR, pulsar notificación o conectarse a Wi-Fi.
- No crear asignaciones docentes, inscripciones o credenciales de producción para «hacer que se vea bien».
- No declarar PR merged, CI verde, QA física o producción desplegada hasta comprobar.

## X. NUEVO ALCANCE APROBADO: tareas y entregas en el canal de clase (NO duplicar chat)
**Hallazgo verificado en `main`**: `shared/contracts/ChannelContracts.kt` ya define `ANNOUNCEMENT`, `MATERIAL`, `REMINDER`, `ChannelAttachment` y acuses; `ChannelService.kt` admite hasta 8 enlaces de recurso HTTPS por publicación y `ChannelScreen.kt` deja al docente publicar avisos/materiales y al alumno leer y responder con presets. **Eso NO equivale a un sistema de tareas entregables**: no se identificó contrato oficial de asignación, fecha de entrega, entrega estudiantil o devolución docente para este canal. Reutilizar este canal como origen de tareas; ampliar dominio de forma compatible con Mongo/Ktor y versiones Android anteriores, en lugar de crear un chat nuevo.

### X.1 Experiencia visual a entregar (misma UI aprobada)
- **Canal de clase**: header compacto de materia, grupo, docente; pestañas «Publicaciones», «Materiales» y «Tareas» en control segmentado existente. Mostrar tarjetas de publicación a lo DailyFlutterUI: avatar/docente, título, fecha, etiquetas sutiles y adjuntos en chips.
- **Crear tarea docente**: botón contextual «Nueva tarea» solo para clase asignada y rol válido. Abrir **bottom sheet** sólido blanco/carbón con título, instrucciones enriquecidas en texto simple (sin editor pesado), fecha/hora límite con zona, adjuntos y público destinatario = miembros autorizados de la clase/grupo. Draft/preview antes de publicar. No permitir seleccionar grupo ajeno.
- **Tarea alumno**: tarjeta con título, materia, fecha de entrega, estado «Sin entregar / Entregada / Devuelta / Vencida», badge semántico, botón «Ver tarea». Vista detalle con instrucciones, recursos del docente y **Enviar entrega** usando selector de documentos Android (Storage Access Framework), picker de fotos si permitido, y campo de comentario; progreso de carga real y opción cancelar/reintentar con seguridad.
- **Revisión docente**: lista de alumnos de **su** clase, entregas, marca de tiempo, adjuntos, comentario y acción «Revisar / Devolver para corregir / Marcar como revisada». Estados de revisión rastreables. Descargar archivos a través de autorización de servidor. No mostrar entregas de compañeros a alumnos.
- **En inicio**: tile «Tareas» y resumen «Pendientes» con número real, próxima fecha, atrasadas, enviadas y correcciones; no inventar pendientes en ausencia de enlace institucional. La agenda puede mostrar una tarea solo como evento personal/operativo, sin alterar horario institucional.
- **Notificación**: FCM al publicar/modificar/devolver una tarea; deep link con clase/tarea + revalidación en backend; badge no leído real. No garantizar entrega de push: al abrir app, reconciliar con API.
- **Adjuntos**: mantener los recursos HTTPS existentes como referencias; si «cargar archivo» exige subir binarios, implementar flujo autenticado backend → almacenamiento privado (p. ej. proveedor aprobado) → metadata inmutable/versionada → URL firmada efímera para ver/descargar. No asumir que Mongo guarda blobs, ni exponer buckets públicos o enlaces arbitrarios. Validar tipo, tamaño, MIME real, cuota, titular, ruta, malware cuando aplique, privacidad, expiración, borrado y reintentos. Nunca registrar contenido o tokens.
- **Calificaciones**: una revisión/comentario en Compañero **no** publica automáticamente nota oficial en SIII; si se desea calificar, requerir un contrato y aprobación institucional explícitos separados del flujo de entrega. Reusar `feature/grading` solo cuando su autoridad lo permita.
- **Sincronización**: SIII controla cursos/grupos/adscripciones; Compañero controla tareas colaborativas, archivos y comentarios. SIII podrá consumir **indicadores agregados/autorizados**, nunca asumir control del canal ni ver datos de estudiantes fuera de ámbito.

### X.2 Contratos y modelo incremental propuesto
- Definir IDs estables de institución, aula/sección/grupo, tarea y autor; no duplicar catálogo académico.
- Tabla/colección de `ClassTask`: id, institutionalScope, channelId, teacherId, title, description, resources, dueAt UTC+tz, publishedAt, status (DRAFT/PUBLISHED/CLOSED/CANCELLED), revision, audit.
- `TaskSubmission`: id, taskId, studentId, version, text, file refs, submittedAt, state (DRAFT/UPLOADING/SUBMITTED/RETURNED/REVIEWED), reviewer, reviewedAt, feedback; clave de idempotencia y reglas explícitas para reenviar y entregas tardías.
- Proteger con actor autenticado y roles actuales; checks de adscripción/inscripción, estado de clase, periodo, límites y ownership en **cada endpoint**, lectura, descarga, mutación y webhooks.
- No confiar en client-supplied authorId, studentId, MIME ni adjuntos; deduplicación, revisión por versión/ETag y reglas de concurrencia al calificar/editar. Preferir `ETag/If-Match` o revision.
- Compatibilidad hacia atrás con `ChannelPost` y sus enums serializados: no reinterpretar `MATERIAL` como tarea sin migración o campo/discriminante aditivo. No romper apps instaladas ni sync de otras rutas.
- Si no hay proveedor de archivos seguro, permitir tarea solo texto/enlaces en QA y marcar carga binaria como **bloqueada**, sin simular subida exitosa.

### X.3 Criterios específicos E2E de tareas
1. Docente con clase asignada publica tarea válida (texto, fecha, recursos); otro docente recibe 403.
2. Alumno matriculado ve tarea y recurso; estudiante de otro grupo/invitado no puede verla ni acceder a URL.
3. Alumno adjunta archivo permitido, envía, recibe confirmación solo tras persistencia; offline/error no muestra «Entregada».
4. Intentos duplicados, reintentos y doble clic no producen entregas duplicadas.
5. Docente revisa y devuelve; estudiante ve retroalimentación y reenvía nueva revisión sin destruir la anterior.
6. Reasignación de grupo/expulsión/revocación invalida permisos del recurso y URLs previamente emitidas según política.
7. Tarea editada o cerrada, deadline pasada, reloj del cliente incorrecto y entregas tardías no elevan permisos.
8. FCM puede faltar o llegar repetida: feed autoritativo API mantiene estado de tareas.
9. Comprobar accesibilidad TalkBack, reducción de movimiento, 360/390/430 dp, texto 200%, tema claro/oscuro y pruebas de seguridad/almacenamiento.
10. El contador «Tareas» y las métricas de administración son **reales y agregadas**, con privacidad y aislamiento multiinstitución.

### X.4 Dónde entrar en el roadmap
- Tras F2/F3, añadir subfase **F4-Tareas (backend/API/contracts, Android UI y upload seguro)** en paralelo conceptual con F4-Asistencia, pero con PRs separados.
- Extender F5 para que «Tareas» en Hoy y en Canal correspondan al mismo estado; no crear pantalla y repositorio duplicados.
- Extender F6 con test de autoría, permisos, archivos, revisión, idempotencia, URLs firmadas y flujo Ktor ↔ Android. QA E2E no se marca verde hasta verificar carga/descarga de archivo desde dispositivo real.
