# Auditoría visual y funcional V8 en emulador

Pedido del 7/8 de octubre de 2026. Base remota verificada: `9445d2487c0b0b682b98420ba4266df134d342ff`, rama `codex/v8-emulator-audit-20261007`. Las cinco referencias adjuntas son el contrato visual. **La fidelidad completa y la validación escolar real siguen pendientes; no se recomienda merge como V8 terminada.**

## Diagnóstico y correcciones

| Severidad | Hallazgo comprobado | Causa y corrección |
|---|---|---|
| P1 | Alumno con solo tres destinos, canal sin acceso principal | `RoleExperienceResolver` omitía QR y Perfil. Cinco destinos y acceso a canal desde Clases; prueba de roles y recorrido ADB. |
| P2 | Texto oscuro sobre fondo universitario | Esquema claro heredado por pantallas V8. Paleta V8 oscura limitada al alumno; otros roles conservan apariencia y barras acordes. |
| P2 | Login genérico, campos sin iconos, encabezado desproporcionado | Integración de `V8AcademicField`, logo vectorial propio, jerarquía blanca/roja, vidrio y botón con gradiente/borde/sombra roja. Validación, sesión y teclado permanecen reales. |
| P2 | Semana era una lista, no cuadrícula proporcional | `WeeklyAgendaGrid` usa minutos reales, duración, columnas por día y carriles por grupos de conflictos. Nombres completos y metadatos en detalles desplazables. |
| P2 | Superposición en primer prototipo semanal | Hermanos dentro de `AnimatedContent` compartían posición. Se agrupan en `Column`; captura intermedia no representa resultado final. |
| P2 | Arrastre semanal omitido y destinos incorrectos con carriles | Conectado a propuesta/confirmación existente; destino según límites de columnas, no media anchura del carril. Conflictos recalculados contra agenda completa incluso con filtro activo. |
| P2 | Sábado podía aparecer dos veces; franjas tardías cortadas | Lista única de días, fin de semana según datos; rango horario ampliado en semana y día. Pruebas de bloque de 15 minutos a las 23:30. |
| P2 | Badge escolar aceptaba objeto no nulo o reloj del móvil | Exige QR/red/estado/vigencia. Timestamp opcional del servidor y ancla monotónica almacenada en ViewModel, con ticker de expiración. Sin timestamp no se declara verificado. |
| P2 | Canal podía perder el feed con fuente grande | Encabezado, selector, pestañas, publicaciones y acciones dentro de una sola `LazyColumn`. Avisos lee; Chat habilita exclusivamente respuestas autorizadas. |
| P2 | Etiquetas de navegación se superponían a fuente200% | Texto activo con ancho limitado y dos líneas; destinos inactivos conservan descripción accesible. Captura posterior sin cruce de etiquetas. |
| P2 | Alto contraste sobrescrito | Preferencia preservada en paleta V8 y borde de tarjetas; escala tipográfica y movimiento heredados. |
| P1 | PDF en lista no producía clases | Reconstructor OCR trataba encabezados verticales como columnas y excluía todas las líneas con horas. Ahora exige encabezados distribuidos en una misma fila, con fallback al texto OCR. |
| P2 | “Ingles” se recortaba a “QA” | Regex identificaba “Ing” dentro de una palabra. Títulos sin punto requieren fin de palabra; prueba de regresión. |

No se sustituyeron pantallas por mockups ni se añadieron frameworks. Se buscaron consumidores antes de retirar backdrop duplicado, tarjetas V8 de comunicación sin integración, progreso de asistencia sin datos, tarjeta semanal huérfana y paneles Home sin uso. Los componentes conservados tienen consumidores reales.

## Diferencias visuales pendientes

| Pantalla | Diferencia legítima de datos | Diferencia de diseño pendiente |
|---|---|---|
| Login | Estado vacío deshabilita envío; errores/submitting dependen de auth | Fotografía de corredor distinta del edificio de referencia, decoración inferior/libros/laptop ausente; proporciones y glow no idénticos. Recuperación aún carece de endpoint. |
| Inicio | Nombre, fecha, materias y clases vienen de API/agenda personal | Falta fuente de foto de perfil, avisos agregados, asistencia porcentual y pendientes; se muestran métricas disponibles. Composición más corta y timeline menos elaborado. |
| Horario | Materias, horas, aula y fin de semana reflejan datos | Sin selector de periodo porque el contrato cargado no suministra periodos. Cabecera/controles consumen más alto; cuadrícula horizontal cuando texto/carriles requieren espacio. Iconografía/glow diferentes. Los bloques de15min conservan duración proporcional y el texto no cabe; detalles/lista contienen el nombre completo. P2 pendiente para su presentación visual. |
| Canal | Publicación marcada QA, profesor/grupo reales del proveedor local | No se simulan likes/emoji/reacciones, avatares, contadores o fechas que el contrato no ofrece. Tarjetas y densidad diferentes; links dependen de recursos válidos del docente. |
| Pase de lista | Jornada pendiente, ubicación no utilizada, sin cifras inventadas | Símbolo QR decorativo no es un token; la cámara se abre con acción real. Progreso/cohorte y actividad dependen de registros reales; sin datos la composición difiere. |

Estas diferencias no se convierten en PASS por tener CI verde. P2 de composición/fuentes faltantes requieren producto/backend y activos adecuados; P3 de glow/espaciado permanece visible en comparativas.

## Evidencia y método

`v8-evidence/before` contiene baseline ejecutado; `intermediate` conserva hallazgos de iteraciones y no se presenta como final. `after` contiene estados posteriores y `responsive` configuraciones adicionales. Capturas ADB nativas y XML generado en ruta única por lectura. Las capturas de launcher o mal identificadas se excluyeron. El selector externo no se presenta como pantalla propia.

Comparativas: `v8-evidence/comparisons/<pantalla>/comparison.png`, cuatro paneles (referencia, Android, superposición 50%, diferencia RGB absoluta amplificada ×3). Referencias recortadas a `(40,30,824,1788)` para retirar el marco y normalizadas a 390×874. El Android 1080×2400 tiene relación ligeramente distinta: normalización para análisis global, no métrica de equivalencia exacta. No se publican porcentajes de fidelidad. Hora/barras/datos variables permanecen sin máscara y deben distinguirse manualmente de defectos de diseño.

Capturas anteriores de canal/asistencia en baseline no disponibles porque no eran destinos accesibles del alumno; esa ausencia es evidencia de navegación faltante, no PASS visual.

## Cambios concurrentes

Durante el cierre, main avanzó a `75b436daef3f7c6035e4d5bf380dc83051961c1c` (#83, #85–90 y #93). Se reconcilió dentro de esta rama conservando tutorías, revisión de justificantes, confirmación V9, padrón y autorización de servidor. Tres conflictos de UI se resolvieron conservando el scroll único del canal y navegación con texto grande; se reutilizaron los tabs nativos de #93 con altura flexible. La primera compilación detectó una unión incorrecta de ChannelIdentity y un import duplicado; se corrigieron antes de validar. La revisión detectó además que V9 demoraba el ancla del tiempo escolar hasta terminar consultas adicionales; se capturó inmediatamente al recibir presencia.

PR #96 propone otra implementación de Home/fondo/cuadrícula y #97 Perfil. Se inspeccionaron, sin fusionar automáticamente trabajo abierto ni duplicar una segunda cuadrícula en esta rama. Las propuestas requieren reconciliación de producto antes de integrar ambas.

## Comparativas principales

| Pantalla | Referencia / ejecución / overlay / diff | Ejecución final |
|---|---|---|
| Login | [Cuatro paneles](v8-evidence/comparisons/login/comparison.png) | [Login](v8-evidence/after/login/initial-390.png) |
| Inicio | [Cuatro paneles](v8-evidence/comparisons/home/comparison.png) | [Inicio](v8-evidence/after/home/final-390.png) |
| Horario | [Cuatro paneles](v8-evidence/comparisons/schedule/comparison.png) | [Semana](v8-evidence/after/schedule/final-week-390.png), [día](v8-evidence/after/schedule/day-classes-390.png) |
| Canal | [Cuatro paneles](v8-evidence/comparisons/channel/comparison.png) | [Avisos](v8-evidence/after/channel/final-notices-390.png) |
| Pase | [Cuatro paneles](v8-evidence/comparisons/attendance/comparison.png) | [Panel](v8-evidence/after/attendance/final-no-session-390.png) |
