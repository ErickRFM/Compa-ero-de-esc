# Auditoría de animaciones, UX, lógica y perfiles — 25 noviembre 2026

Base `main` @ `4540edf9`, rama `ux/motion-loading-profile-audit-20261125`. Guías aplicadas: skill de proyecto `companero-android-qa` (motion en Compose nativo, movimiento reducido, reutilizar `core:designsystem`/`core:motion`, evitar tarjetas duplicadas) y revisión de diseño/accesibilidad (estados de carga anunciados, coherencia visual, peor caso).

## 1. Inventario de movimiento

| Módulo | Pantallas | Archivos con animación | Respeta movimiento reducido |
|---|---:|---:|---|
| auth | 7 | 2 | Sí (birrete animado → icono fijo) |
| home | 4 | 2 | Sí (`Crossfade` → `snap`) |
| attendance | 2 | 1 | Sí |
| schedule | 1 | 1 | Sí (sin desplazamiento) |
| channel | 1 | 1 | Sí |
| core:ui / navigation / designsystem | — | 8 | Sí (todos usan `LocalCompaneroMotionPreferences`) |
| **classroom, grading, profile, settings, tutoring** | **10** | **0** | — |

No se encontró ninguna animación que ignore la preferencia de movimiento reducido. La brecha es de **cobertura**: cinco módulos cambian de estado de forma brusca.

## 2. Hallazgos

| # | Severidad | Área | Hallazgo |
|---|---|---|---|
| 1 | P2 · Lógica/visual | Perfil | El separador antes de «Solicitar tutoría» solo consideraba `onSecurity`; con «Aceptar/Enviar invitación» visibles y sin Seguridad, la fila quedaba pegada a la anterior. |
| 2 | P2 · Accesibilidad | Clases, Calificaciones | `CircularProgressIndicator` sin texto ni semántica (6 apariciones): TalkBack no anunciaba la carga y el giro continuo ignoraba el movimiento reducido del producto. |
| 3 | P3 · Motion | Tutorías (4 pantallas), Clases, Calificaciones | Tarjetas de carga y spinners aparecen/desaparecen en un frame, desplazando el contenido de golpe. |
| 4 | P3 · Coherencia | Calificaciones | Spinner suelto dentro de una tarjeta, distinto del patrón `V8LoadingCard` del resto de la app. |
| 5 | Recomendación | Perfil | «Cerrar sesión» actúa sin confirmación. No se cambió: altera el flujo cubierto por `ProfileConsistencyUiTest` y `AccountOnboardingUiTest`; requiere decisión de producto. |
| 6 | Recomendación | Perfil | «Docente pendiente» ve la experiencia `TEACHER` con etiqueta correcta, pero la fila «Roles de la cuenta» no explica qué falta para la aprobación. |

## 3. Cambios

- `core:designsystem` · nuevo `V8InlineLoading(title)`: fila de 48 dp mínimo con la misma semántica que `V8LoadingCard` (`liveRegion = Polite`, progreso indeterminado, descendientes fusionados) y el mismo indicador que cambia a puntos fijos con movimiento reducido. Pensado para secciones que ya están dentro de una tarjeta, evitando tarjeta-en-tarjeta.
- Clases (5), Calificaciones docente (1) y alumno (1): spinners sustituidos por `V8InlineLoading`/`V8LoadingCard` con texto («Cargando tus clases…», «Leyendo lista de alumnos…», etc.).
- Tutorías (5 cargas en 4 pantallas), Clases y Calificaciones: la carga entra/sale con `CompaneroExpandableVisibility` (fundido + expansión 220 ms; sin transición con movimiento reducido).
- Perfil: separador corregido para cualquier fila previa del grupo «Acceso».

Sin cambios de API, navegación, permisos, roles ni resolución de experiencias.

## 4. Verificación

| Comprobación | Resultado |
|---|---|
| `compileDebugKotlin` designsystem, grading, tutoring, classroom, profile | OK |
| `testDebugUnitTest` de esos cinco módulos | OK |
| `ktlintCheck` fuente principal de los cinco módulos | OK |
| `ktlintCheck` test de `core:designsystem` | Falla previa en `ColorContrastTest.kt` (existe en `HEAD`, no tocado) |
| Instrumentadas API 31/35, 360/390/430 dp, 200 % de texto, TalkBack | **Pendiente**: no había emulador ni dispositivo conectado |
| `assembleAcademicDebug` + `assembleManagementDebug` (local) | OK, ambas APK |
| `testDebugUnitTest` de todo Android (local) | 309 casos, 0 fallos, 0 omitidos |
| `lintAcademicDebug` (local) | OK |
| `ktlintMainSourceSetCheck` global | Fallas previas en `shared:models`, `shared:validation`, `core:attendance`, `core:database`, `core:network`; ninguno modificado en esta rama |

## 5. Commits

1. `f2ce251e` — trabajo local previo del autor: `ContentMotion.kt` (`CompaneroExpandableVisibility`, `animateCompaneroProgressAsState`), `V8PreferenceGroup`, extracción de `ProfileComponents.kt` y uso en Inicio, Asistencia y Ajustes.
2. `464ba371` — esta auditoría, que se apoya en `CompaneroExpandableVisibility` del commit anterior.
