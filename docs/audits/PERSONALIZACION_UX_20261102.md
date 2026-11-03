# Auditoría de personalización, UI y UX

Base `56411d0a`. Se conserva la identidad borgoña inicial y las preferencias existentes; el objetivo es que todas sus variantes funcionen con la misma calidad.

## Investigación y comparación

Referencias públicas consultadas y sus imágenes inspeccionadas:

| Referencia primaria | Patrón observado / utilidad para Compañero | Límite |
|---|---|---|
| [Student Scheduling, Zubair Akhter / Dribbble](https://dribbble.com/shots/27696305-Student-Scheduling-Mobile-App) | Una tarjeta protagonista para el momento actual, listas cortas y una jerarquía constante en agenda/tareas/perfil | Es un concepto, no un producto validado con usuarios |
| [MyStudyLife, recorrido oficial](https://mystudylife.com/tour/) | Dashboard centrado en lo próximo y organización de clases/tareas; personalización como parte del producto | Se revisó material público, no una sesión pagada ni rendimiento real |
| [Now in Android](https://github.com/android/nowinandroid) y [Compose Samples](https://github.com/android/compose-samples) | Colores dinámicos, componentes reutilizables y distribución según el espacio disponible | Aplicación de referencia técnica, no competidor académico directo |
| [Android: progreso](https://developer.android.com/develop/ui/compose/components/progress) y [adaptación](https://developer.android.com/develop/ui/compose/build-adaptive-apps) | Carga vinculada al estado y layout que cambia de estructura según espacio/configuración | Guías de implementación; no certifican nuestra aplicación |

Instagram se intentó mediante búsqueda y acceso público; no devolvió contenido verificable. No se declara inspección de publicaciones. El navegador integrado tampoco pudo inicializarse en este ambiente. Las imágenes de referencia se usaron únicamente para inspección interna; no se incorporan assets ni layouts propietarios al producto.

Evaluación del agente: Compañero tiene una base visual coherente y flujos institucionales diferentes de un planner genérico, pero aún había fallos de contraste, adaptación y ruido textual que impedían considerar afinada su personalización. La mejora se evalúa por contratos y capturas concretos; no se afirma superioridad comercial sin un estudio de usuarios comparable.

## Hallazgos de fuente y reproducción

| ID | Prioridad | Hallazgo | Corrección requerida |
|---|---|---|---|
| PERSONAL-01 | P2 | Primary pastel oscuro combinado con blanco; ámbar ~1.67:1 y esmeralda ~1.92:1 | Tinta de contraste y pares completos para seis acentos |
| PERSONAL-02 | P2 | V8 copia primary/container dinámicos sin sus on-colors; Inicio/botón oscuro usan el acento guardado | Preservar parejas dinámicas y consumirlas en superficies y acciones |
| LAYOUT-01 | P2 | Tres chips en Row fija desbordan 320 dp al combinar Android 200% con Grande 120% | Opciones que fluyen a varias líneas y siguen alcanzables |
| LAYOUT-02 | P2 | `fillMaxSize` anulaba el límite del panel de Ajustes en ventanas amplias | Contenedor centrado y límite efectivo de 720 dp |
| A11Y-01 | P2 | Switch y etiqueta son hermanos sin nombre semántico compartido | Fila con rol switch, nombre y un solo callback |
| LOAD-01 | P2 | Arranque muestra spinner aislado; Inicio puede mostrar “Sin clases próximas” antes de tener datos | Carga identificable y placeholder real; conservar datos al refrescar |
| DENSITY-01 | P3 | Perfil repite título/descripción/estado; accesos repiten explicaciones | Identidad compacta y acciones breves sin quitar datos operativos |
| LOAD-02 | P2 | La rama saliente de Crossfade recibía el nuevo estado de reintento y afirmaba vacío durante la transición | No componer contenido vacío si vuelve a cargar sin agenda |
| LOAD-03 | P2 | En uso aislado sin Surface padre, la marca de carga heredaba negro sobre fondo oscuro propio | Tinta explícita `onBackground`, comprobada en layout y captura |

La revisión independiente de fuente confirmó PERSONAL-01/02, LAYOUT-01 y A11Y-01. La autoridad por rol permanece en resolver/servidor.

## Implementación

Se comparten parejas de acento en `AccentColors.kt`. Las superficies de identidad consumen contenedor/tinta; las acciones consumen primary/onPrimary. El resolvedor V8 conserva ambas parejas dinámicas y elige tinta con contraste sobre acentos personalizados. Los seis acentos, temas y preferencias permanecen disponibles.

Ajustes usa FlowRow para opciones, un panel centrado con máximo 720 dp, filas de switch con nombre/rol/48 dp y un único callback. Se eliminó el botón de ejemplo que no hacía nada. Perfil conserva nombre, experiencia activa, roles, herramientas y cierre de sesión; elimina cabecera, estado y ayudas redundantes. Gestión mantiene una etiqueta breve dentro de la identidad. Los cuatro accesos de Inicio conservan sus destinos y muestran icono/nombre sin párrafos.

La carga de apertura depende de `session.checking`, con entrada finita de 220 ms y escala 96%→100%; al reducir movimiento permanece estática. La actividad de carga normal es indeterminada, sin porcentajes ficticios ni tiempo mínimo. El fondo nativo sigue día/noche del sistema y Compose aplica las preferencias leídas. Inicio usa placeholder hasta tener datos, conserva agenda durante refresco y distingue fallo de vacío. Su Crossfade respeta movimiento reducido y descarta la rama saliente falsa durante reintento. Los títulos secundarios largos permanecen en una línea con el texto completo disponible semánticamente.

## Evidencia de regresiones

`build/personalization-red-20261102.log` reprodujo tres fallos unitarios de parejas/contraste y tres de UI: chips fuera del ancho, switch sin nombre compartido y carga sin estado textual. `build/personalization-agenda-red-20261102.log` reprodujo el falso vacío inicial. `build/personalization-layout-red-20261102.log` reprodujo el límite de ancho inefectivo; también detectó una fixture que esperaba alternar perfiles con un único rol, corregida a una cuenta mixta sin cambiar permisos.

La pasada focal `build/personalization-green-20261102.log` aprobó ocho casos y unitarias de diseño/Inicio/Ajustes. Después, la revisión independiente encontró LOAD-02: se reprodujo con reloj detenido a dos frames, sin avanzar los 220 ms (`build/personalization-retry-red-20261102.log`), y se añadió el guard de estado conservando la aserción. La misma revisión confirmó el arreglo sin otros hallazgos materiales.

La primera global aprobó API, shared, unitarias, lint/ktlint y ambas APK, con 40/41 casos de UI: el único fallo exigía la identificación “Perfil de Gestión” eliminada al compactar la cabecera. Se recuperó como caption, conservando la prueba original. La inspección de la captura aislada de carga midió negro sobre `#191A1C`, **1.206:1** (`build/loading-contrast-red-20261102.log`); se corrigió a `onBackground` y la prueba inspecciona además el color efectivo del TextLayoutResult. Estos estados intermedios no se cuentan como aprobados finales.

## Verificación final

La pasada global `build/personalization-global-final-20261102.log` terminó correctamente con **1,134 casos aprobados, 3 omitidos y ningún fallo**, incluyendo 41 pruebas de UI en emulador API 30. Los tres omitidos son contratos opt-in que requieren el laboratorio SIII. Pasaron lint, ktlint y ambas APK debug. Tras el último ajuste del título de Ajustes se repitieron las 41 pruebas, unitarias y lint de Ajustes y ambas APK con `BUILD SUCCESSFUL` (`build/personalization-final-affected-20261102.log`).

La prueba de carga verifica el color efectivo del título y compara capturas con movimiento reducido; la medición de píxeles pasó de 1.206:1 a **14.009:1** para ese par concreto. Los cuatro casos de Perfil/selector pasaron adicionalmente en un emulador configurado a 430 dp; se restauraron tamaño y densidad originales. La matriz cubre seis acentos en claro/oscuro, alto contraste, texto combinado al 240% en 320 dp y una ventana lógica de 840 dp que se reduce a 320 dp conservando la selección. La ventana ancha es un harness lógico, no una certificación en tableta física.

Las capturas usan identidades y callbacks sintéticos: [perfil borgoña](evidence/20261102-personalizacion/profile-crimson-dark.png), [perfil azul](evidence/20261102-personalizacion/profile-cobalt-light.png), [alto contraste](evidence/20261102-personalizacion/profile-contrast-dark.png), [texto al 240%](evidence/20261102-personalizacion/profile-large-240.png), [Ajustes oscuro](evidence/20261102-personalizacion/appearance-compact.png), [carga](evidence/20261102-personalizacion/startup-reduced.png) y [agenda cargando](evidence/20261102-personalizacion/home-loading.png). Las dimensiones de las 24 capturas locales están en [el manifiesto](evidence/20261102-personalizacion/manifest.json). Color dinámico se verifica en CI API 31/35; API 30 no lo soporta.

La revisión independiente confirmó los cambios y el guard del reintento. La aceptación con cuentas institucionales reales, TalkBack, hardware físico y medición de FPS sigue pendiente; la comparación de referencias no constituye una medición comercial de competitividad. Los límites de integración institucional anteriores siguen en [la auditoría de consolidación](CONSOLIDACION_UI_LOGICA_20261102.md).
