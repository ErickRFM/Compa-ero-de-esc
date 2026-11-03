# Diseño borgoña y movimiento — 2 noviembre 2026

Base `fb2f539e`, rama `codex/diseno-motion-borgona-20261102`. Continúa la auditoría de perfiles conservando la identidad elegida por el usuario.

## Cambios

- Perfil: tarjeta de identidad borgoña, avatar, experiencia activa y grupos con iconos. Con texto grande se apilan identidad y estado; las filas recuperan ancho ocultando iconos decorativos.
- Inicio: cuatro tarjetas con icono, descripción y dirección. Conservan los callbacks de Horario, Asistencia, Avisos y Tareas. Dos columnas en móvil, cuatro desde 600 dp y una al ampliar texto desde 160%; las alturas se igualan por fila.
- Sistema compartido: acento en encabezados, borde discreto en tarjetas enfatizadas y respuesta de pulsación a escala 0.985 durante 120 ms. Se comparte la fuente de interacción del control; no se añade un detector de gestos.
- Navegación: fundido de entrada de 220 ms con desplazamiento de 6 dp y salida de 120 ms. Con movimiento reducido se eliminan transiciones de pantalla, interpolación espacial de indicadores y escala de pulsación.

Se reutilizan Material 3, V8 y `core:motion`, sin nueva dependencia externa. No cambian APIs, almacenamiento, permisos ni resolución de experiencias. La revisión independiente de fuente final no encontró defectos materiales accionables.

La inspección de la captura al 200% detectó un resaltado rectangular que excedía las esquinas de los nuevos accesos. Se añadió recorte con el mismo radio V8 antes de `clickable`, conservando la fuente de interacción y sus callbacks.

## Accesibilidad y alcance

La identidad predeterminada usa blanco sobre `#8A1538`, contraste calculado **9.35:1**. Alto contraste usa superficie/texto del tema y borde sólido; color dinámico conserva el par contenedor/texto correspondiente. Este cálculo no certifica todas las paletas ni estados. Se conservan encabezados semánticos, rol de botón y mínimos táctiles de 48 dp existentes.

Los casos sintéticos verifican callbacks, navegación y presentación de perfiles; no representan una sesión institucional real. No se midieron FPS ni se certificaron TalkBack o hardware físico. Permanecen los límites funcionales de [la consolidación anterior](CONSOLIDACION_UI_LOGICA_20261102.md), incluidos publicación oficial SIII y aceptación física.

## Verificación de movimiento

Los cinco casos de `MotionDesignUiTest` comprueban:

1. Cambio real de escala 100% a 200%, observado dentro de la composición, con los cuatro destinos alcanzables y un callback por destino.
2. Escala al mantener pulsado, restauración al cancelar sin callback y conservación de un toque en el borde y otro clic.
3. Control inmóvil con movimiento reducido, conservando el callback.
4. Navegación y regreso sin retener la pantalla saliente al reducir movimiento.
5. Indicador alineado con la pestaña seleccionada sin animación espacial al reducir movimiento.

El reloj de pruebas permite hasta seis frames de bookkeeping (96 ms), sin atravesar los 120 ms de la animación más corta. No es una medición de latencia física. Se corrigió el tag para medir después del offset, y se sincronizan las capturas de Perfil con el destino RESUMED y el árbol Compose renderizado. No se aumentaron timeouts ni se retiraron aserciones.

El control actualizado sobre la implementación anterior produjo dos fallos esperados de navegación/indicador (`build/design-motion-baseline-red-20261102.log`); se restauró exactamente la fuente final. La suite nueva pasó sus cinco casos (`build/design-motion-green-final-20261102.log`); el sondeo adicional de toque en el borde también pasó (`build/design-motion-edge-probe-20261102.log`). CI exige cinco casos distintos y exporta cuatro PNG de diseño en ambas distribuciones.

## Verificación global y capturas

La pasada global terminó con `BUILD SUCCESSFUL`, salida 0 y 1364 tareas (`build/design-motion-global-20261102.log`). Pasaron API/Mongo local, shared, unitarias Android, `ktlintCheck`, `lint`, ambas APK debug y 33 casos de UI en API 30. Gradle reutiliza resultados válidos y ejecuta de nuevo los afectados.

| Grupo | Registrados | Aprobados | Omitidos |
|---|---:|---:|---:|
| API / Mongo local | 694 | 691 | 3 |
| Shared | 28 | 28 | 0 |
| Bibliotecas Android | 357 | 357 | 0 |
| App Académica unit | 7 | 7 | 0 |
| App Gestión unit | 7 | 7 | 0 |
| UI Académica / API 30 | 33 | 33 | 0 |
| **Total** | **1126** | **1123** | **3** |

Cero fallos. Los tres omitidos son los contratos opt-in de SIII que requieren su laboratorio Next/PostgreSQL local; no se omiten pruebas nuevas. Los 33 casos incluyen movimiento (5), perfiles (4), navegación de perfiles (7), navegación por rol (7), administración (6) y tareas (4).

El pequeño ajuste de recorte se añadió durante esta pasada después de la compilación; por ello se verificó aparte el código final con unitarias/ktlint/lint de Inicio, recompilación de ambas APK y repetición de esos 33 casos. Esta segunda pasada también terminó con `BUILD SUCCESSFUL`, salida 0 y cero fallos (`build/design-motion-final-20261102.log`, 721 tareas). Los casos repetidos no se suman otra vez al total.

Los cuatro casos de Perfil/selector pasaron además con el emulador temporalmente configurado a 1290 × 2400 px / 480 dpi, permitiendo los topes de 360/390/430 dp sin compresión del anfitrión. El script restauró y comprobó tamaño/densidad originales (`build/ui-profiles-width430-20261102.log`). Esos casos tampoco se suman dos veces.

Capturas finales inspeccionadas, de componentes y perfiles reales con identidades/callbacks sintéticos. Las tres de Perfil miden 1290 × 2111 px y excluyen barras de sistema; los márgenes exteriores de las fixtures estrechas pertenecen al harness. Los accesos se capturan en API 30 con su ventana original: el estado al 200% muestra el final del scroll y el resaltado de Tareas ya recortado.

- [Perfil anterior](evidence/20261102-diseno-motion/before-profile-dark.png).
- [Estudiante, oscuro, 360 dp](evidence/20261102-diseno-motion/profile-student-dark.png).
- [Docente, claro, 390 dp, texto 200%](evidence/20261102-diseno-motion/profile-teacher-light-200.png).
- [Coordinación, oscuro, 430 dp, texto 200%](evidence/20261102-diseno-motion/profile-coordinator-dark-200.png).
- [Accesos de Inicio, texto normal](evidence/20261102-diseno-motion/shortcuts-normal.png).
- [Accesos desplazados hasta Tareas, texto 200%](evidence/20261102-diseno-motion/shortcuts-large-200.png).
- [Botón en reposo](evidence/20261102-diseno-motion/button-resting.png) y [mantenido pulsado](evidence/20261102-diseno-motion/button-pressed.png).

También pasaron sintaxis Bash, `git diff --check` y escaneo de secretos. El estado remoto del SHA final, incluidos los cuatro emuladores Académica/Gestión × API 31/35 y el APK test-release minificado, se sigue en el PR de esta rama; este informe registra la verificación local anterior a emitir ese commit.

## APK locales de QA

| Variante | Ruta bajo `apps/android/app/build/outputs/apk/` | SHA-256 |
|---|---|---|
| Académica debug | `academic/debug/app-academic-debug.apk` | `0785c760e98f14bf62da5f47e4eea179dfbbd6360b45a71349d7c7abb0438c59` |
| Gestión debug | `management/debug/app-management-debug.apk` | `7ef27d2b805210bcce2b5c3910395f81868b128266fceeff5c43309a06451d4f` |
