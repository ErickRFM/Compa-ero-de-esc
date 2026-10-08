# Plan maestro de implementación visual — Docente V8.1

**Fecha de auditoría:** 2026-10-08  
**Línea base verificada:** `main@75b436daef3f7c6035e4d5bf380dc83051961c1c`  
**Estado:** PLAN DE EJECUCIÓN. No declarar imágenes generadas como capturas reales de la app ni funcionalidades completadas sin QA.

## 0. Objetivo

Reproducir visualmente en **Android nativo (Kotlin + Jetpack Compose)** las cinco referencias de diseño acordadas para **Inicio docente, Asistencia, Mis clases, Evaluación y Canal**, usando exclusivamente el sistema V8 Red Edition ya existente, sin cambiar la lógica funcional de control escolar, sin crear componentes duplicados y sin hacer merge a ciegas.

Las cinco referencias de imágenes se entregaron en la conversación; la especificación aquí es textual para que pueda ejecutarse desde Git sin depender de rutas temporales de imágenes. Sus nombres, cifras y fechas son ficticios de maqueta, nunca datos runtime.

## 1. Estado de Git comprobado

| PR | Contenido | Estado al revisar | Decisión |
| --- | --- | --- | --- |
| #90 | Asistencia V9 campus, pase en aula y roster | MERGED en main previamente | Conservar servicio y evidencia QR |
| #93 | Canal y QR V8 | MERGED en main, SHA `75b436da` | No recrear ni revertir |
| #94 | Mis clases + Evaluación con asignaciones de API | OPEN; CI API/Android SUCCESS | Integrar tras revisión de diff y nueva base; cerrar con QA visual |
| #95 | Inicio docente: pase activo + próxima clase | OPEN; CI API/Android SUCCESS | Integrar tras revisar #94 y QA visual |
| #96 | V8 fondo/Inicio/Horario y emulador | OPEN; CI API/Android SUCCESS | Integrar respetando design system |
| #97 | Perfil V8 | OPEN | No sobrescribir ni copiar |
| #99 | Login glassmorphism V8 | OPEN | No cambiar auth en este trabajo |
| #100 | Reconciliación V8 emulador, grid/OCR | OPEN; CI API/Android SUCCESS | Revisar primero; evitar repetir soluciones ya implementadas |

Otros PRs abiertos #89 y #91 pertenecen a Tutorías; no mezclarlos con diseño docente.

**Precaución:** resultados de CI descritos son válidos para el HEAD consultado el 2026-10-08. Revalidar cada SHA tras modificaciones y antes del merge.

## 2. Contrato visual obligatorio

No inventar otro sistema de estilos:
- Reusar `V8CampusBackdrop`, `V8BrandHeader`, `V8GlassCard`, `V8RedColors`, `V8RedPrimaryButton`, `V8DashboardStat` y `V8DailyClassRow`, según el contexto.
- La superficie debe permitir leer texto sobre fotografía: oscurecimiento global del fondo, vidrio negro translúcido, contornos claros discretos, crimson reservado para acción principal y actividad, sin fluorescencias constantes.
- Separaciones: 16dp horizontal en móviles pequeños, 8dp entre elementos íntimamente relacionados, 12–16dp en tarjetas y 20–24dp entre secciones; usar tokens de `CompaneroSpacing` en lugar de duplicar números.
- Texto: al menos 14sp en metadatos críticos, titulares sin corte, contrastes accesibles; 48dp de área táctil para acciones clave, insets seguros, scroll vertical, dimensiones flexibles y sin botones encimados.
- En 360/390/430dp, las tarjetas no deben necesitar tres columnas de botones largos en una sola fila. Preferir primary full-width + dos secundarios adaptativos o overflow; tablet con máximo ancho coherente.
- Bottom navigation: solo destinos principales soportados por `RoleExperienceResolver`; pantallas secundarias con Back. No crear una quinta pestaña que exceda el modelo actual, ni duplicar enlaces contextuales.
- Semántica de estados: rojo activo/CTA, verde confirmado, ámbar por revisar, gris sin registro. No codificar estado únicamente por color.
- Animaciones reducidas: transiciones breves y suaves, sin usar LayoutAnimation ni degradación de accesibilidad.

## 3. Diseño detallado por vista

### A. Inicio docente

**Fuente:** `apps/android/feature/attendance/.../TeacherHomeScreen.kt` con backend de asistencia V9 ya integrado.

Orden vertical:
1. Header V8 (marca, contexto del usuario) + saludo discreto.
2. Tarjeta prioritaria **Pase de lista**: clase/QR/sesión real en vivo, grupo, número de registros y CTA gestionar; cuando no hay pase, estado vacío + CTA iniciar según permisos.
3. **Siguiente clase** desde ocurrencias reales del horario: excluir sesiones finalizadas por fecha/hora del recinto; si no hay próxima, estado vacío honesto.
4. KPI en 2x2: clases hoy, pases activos, registros recibidos, por revisar. Evitar presentar cero cuando la consulta falló.
5. **Entrada escolar** (evidencia independiente de clase) con resumen y abrir padrón; sin mostrar matrículas en portada.
6. Accesos rápidos a Mis clases, Mi horario, Canal y Evaluación; CTA solo navega a destinos válidos.
7. Agenda compacta de tres eventos, con fecha/hora/aula. El horario no se edita aquí.

**No** duplicar consulta API ni empotrar datos ficticios.

### B. Asistencia

**Fuente:** `feature:attendance/AttendanceScreen.kt`, V9 ya integrado.

- Live card QR de aula: mostrar código del servidor *solo* mientras la sesión es válida; renovación automática; estado de caducidad y reconexión.
- Dividir **registro de entrada escolar** y **asistencia en clase**. No reutilizar un único número para ambos.
- Tabs/filtros: Recibidos, Por revisar y Entrada escolar; los números deben derivarse del backend y respetar grupo/sesión.
- Lista de alumnos: nombre desde roster real si está autorizado, hora, confirmación, tardanza y revisión por docente con controles de permisos.
- Cambiar rojo→verde por excepción docente solo si la API registra decisión/autor/fecha/motivo; el cambio visual nunca modifica pruebas QR originales.
- Manejar 0 alumnos, 500 alumnos (paginación o listas lazy), sin red, permisos denegados y cierre de sesión.
- Las imágenes de referencia son guía de composición; el QR generado es un *placeholder visual*, no un QR operativo.

### C. Mis clases

**Fuente:** `feature:classroom/ClassroomScreen.kt` y lógica ya implementada en PR #94.

- Resumen compacto (cantidad de materias activas/grupos distintos, calculados de clases reales).
- Tarjeta por materia/grupo con nombre, salón y aula (no inferir hora si la clase no proporciona horario: resolver ocurrencias del servicio académico).
- Acciones: Pase de lista → Asistencia, Canal → canal autorizado, Evaluar → libro de calificaciones de **esa** clase, sin seleccionar otra por accidente.
- El admin asigna clases. Docente **nunca** crea grupos, materias o matrículas.
- Cuenta multirrol: experiencia docente filtra por identidad/autorización real; no heredar permisos admin por conmutación UI.
- 360dp: acciones apilables; no forzar tres botones grandes en horizontal.

### D. Evaluación

**Fuente:** `feature:grading/GradebookScreen.kt`, cambios de PR #94.

1. Materia y periodo: selector de clases asignadas por la API; prohibir entrada manual de ID; al cambiar de clase invalidar borrador e importación de la anterior.
2. Esquema de evaluación: proyectos, examen, tareas, participación, etc. configurables con porcentajes; total exactamente 100%, evitar overflow visual a 320–360dp.
3. Importar y revisar: XLSX/CSV, encabezados, validación de matrícula, celdas vacías, resumen de errores por fila; preview verificable antes de enviar.
4. Botón de sincronización no debe declarar éxito cuando `UnavailableGradeSyncGateway` sigue activo; estado sin integración institucional.
5. Dar feedback de loading, error y confirmación; no simular guardado server-side.

### E. Canal

**Fuente:** `feature:channel/ChannelScreen.kt`, base corregida por PR #93.

- Chips horizontales por grupo autorizado, con scroll sin encimar flechas/botones.
- Feed cronológico con diferencias de Aviso, Material, Recordatorio; el estilo usa componentes V8 existentes.
- Profesor puede publicar a grupos propios; alumno solo puede leer y enviar respuestas predeterminadas, **sin chat libre**.
- Compositor plegable si es compatible con la solución integrada; CTA `Redactar aviso`, link HTTPS opcional, restricciones de upload existentes.
- No representar un PDF descargable ni conteos de lecturas si el backend no ofrece adjunto/reacciones verificables. En ese caso mostrar acción de enlace o estado no disponible.
- Evitar duplicar trabajo de #93 y adaptarse a sus componentes ya mergeados.

## 4. Estrategia de Git — PRs pequeños, sin conflictos

**Fase 0 — Reconciliación de PRs actuales** (prioridad máxima)
- [ ] Capturar HEAD de `main` y HEAD de cada PR #94, #95, #96, #97, #99, #100; comparar changed files.
- [ ] Revisar CI en commit exacto, PR mergeable, permisos/contratos. No asumir verde si cambia HEAD.
- [ ] Construir matriz de solapamiento: #95 modifica TeacherHome; #96 modifica Home/Horario; #100 corrige V8; #94 modifica Classroom/Grading. Preservar cada dueño de archivo.
- [ ] Ordenar merge por dependencias e impacto, empezando por fixes base/visual que no colisionen. Resolver PR contra `main` más reciente; ejecutar nuevamente CI.
- [ ] No fusionar un PR solo por estar “verde” si carece de datos reales, compatibilidad o accesibilidad.
- [ ] No cerrar el epic si se pierde fidelity de imagen generada o fallan recorridos.

**Fase 1 — Integración de vistas docente ya desarrolladas**
- [ ] Evaluar #94 y fusionar después de QA (Mis clases/Evaluación; corregir asignación de materia/canal destino).
- [ ] Evaluar #95 (Inicio) sobre `main` más reciente; fusionar al pasar QA.
- [ ] Retomar correcciones de #96/#100 que sean útiles sin duplicar.
- [ ] Registrar commits finales de `main` y capturas comparadas.

**Fase 2 — Pulido de composición Android** (`feat/teacher-v8-1-layout-audit`)
- [ ] Consolidar tamaños/tokens comunes y glassmorphism, bajar redundancia visual.
- [ ] Diferenciar home/Asistencia con densidad y prioridad real de docente.
- [ ] Diseñar soportes de 0/1/20/100 alumnos y desbordes de nombres, textos y botones.
- [ ] Ajustes de canal de acuerdo con #93, nunca copiar mocks.
- [ ] Pruebas Compose de responsive y navegación.

**Fase 3 — QA de datos y lógica** (`test/teacher-v8-1-journeys`)
- [ ] Alumno, docente, docente+alumno, pendiente, admin: menú y permisos correctos.
- [ ] Inicio → Mis clases → Asistencia: conserva contexto de clase/grupo.
- [ ] Mis clases → Canal y Evaluación: conserva asignación real; archivo importado no se cruza con otra materia.
- [ ] Recibido/tarde/revisión: respetar evidencia escuela vs clase.
- [ ] Cuenta sin conectividad: estados de retry, caché aislada y mensajes honestos.

**Fase 4 — QA visual físico y release** (`test/teacher-v8-1-visual-rc`)
- [ ] Capturas reales de emulador/dispositivo en **360/390/430/768 dp**, light/dark si soportado.
- [ ] Comparar con cinco referencias: header, fondo, radios, sombras, blur, espacios, altura CTA, scroll, navegación, ausencia de encimados.
- [ ] TalkBack, tamaño de fuente 1.3–1.5×, áreas táctiles >= 48dp, teclado, volver.
- [ ] Android unit + lint + API test + instrumentation + debug/release build, evidencias por SHA; semáforo de bloqueo.
- [ ] APK candidato compilado desde `main` final con SHA y changelog. Solo denominar READY_FOR_PHYSICAL_TEST cuando corresponda.

## 5. Dependencias y orden de trabajo

`#93 + #90 (mergeados)` → `#94 (clases/evaluación)` → `#95 (inicio)` → `PR nuevo pulido layout` → `PR pruebas recorrido` → `QA físico + APK`.

Los PR #96 y #100 de fondo/QA se revisan antes del pulido del layout para aprovechar componentes nuevos; los PR #97/#99 solo deben integrarse por su propio pipeline, sin mezclarse artificialmente con cambios docentes.

## 6. Criterios de aceptación y bloqueo

**Bloqueantes P0**
- Crashes, falla de compilación/lint/tests, rutas sin destino, sesión/rol mal aplicado, exposición de otro grupo, datos/horas inventados, QR escolar confundido con pase de clase, calificación marcada como sincronizada sin backend.

**Bloqueantes P1**
- Botón tapado, scroll cortado, composición que se encime en 360dp, texto esencial ilegible, contraste, exportar/adjuntar acciones sin servicio.

**P2 a corregir antes de release**
- Glow desmedido, glass demasiado opaco/transparente, microanimación pesada, sombras inconsistentes, redundancia de navegación.

**Definition of Done**
1. Todo vive en `main` desde PRs CI-green; ningún código huérfano ni feature rediseñado en duplicado.
2. Cinco vistas funcionan con datos reales y roles autorizados.
3. Capturas reales y tabla de diferencias respecto a las cinco imágenes.
4. Tests ejecutados, sin afirmar comprobaciones no corridas.
5. APK RC basado en SHA de `main` y listado de limitaciones externas (API escolar, FCM, etc.).

## 7. Comandos recomendados (PowerShell)

```powershell
git fetch origin --prune
git switch main
git pull --ff-only origin main
git status --short
git log -1 --oneline
gh pr list --repo ErickRFM/Compa-ero-de-esc --state open
gh pr checks 94 --repo ErickRFM/Compa-ero-de-esc
gh pr checks 95 --repo ErickRFM/Compa-ero-de-esc
.\gradlew.bat :services:api:test
.\gradlew.bat :apps:android:app:assembleDebug
```

Antes de usar una tarea Gradle no verificada, consultar `gradlew.bat tasks` y documentación CI. No hacer force push a main.

## 8. Elementos NO considerados terminados

El diseño generado con ImageGen es una **referencia visual**, no un screenshot real de Android ni prueba de que existe lógica para todos sus elementos. No hay evidencia todavía de que las cinco pantallas reproduzcan fielmente las imágenes a nivel de pixels ni del resultado en dispositivo físico. La asistencia V9 y la política de roles son fuente funcional; no se reemplazan por estados ficticios mostrados en maqueta.
