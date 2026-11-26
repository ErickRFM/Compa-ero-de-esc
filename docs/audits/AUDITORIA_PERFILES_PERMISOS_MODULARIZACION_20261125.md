# Auditoría de perfiles, permisos, UX y modularización — 25 noviembre 2026

Rama `ux/motion-loading-profile-audit-20261125` (sobre `d521b156`). Alcance: qué puede ver y hacer cada perfil en Android, si eso coincide con lo que autoriza la API, si la UI lo comunica, y centralizar la lógica de acceso del cliente.

## 1. Matriz de perfiles (cliente ↔ servidor)

| Perfil | APK | Pestañas | Puede abrir (cliente) | Autoridad en API |
|---|---|---|---|---|
| Estudiante | Académica | Inicio, Horario, Asistencia, Clases | Calificaciones propias, representantes, Canal | Datos propios; QR de su clase; entregas privadas |
| Docente | Académica | Hoy, Horario, Asistencia, Canal | Clases, Calificaciones, solicitar tutoría | `requireRole(TEACHER)` + propiedad de la clase y generación del rol |
| Docente pendiente | Académica | Hoy (aviso de espera) | Solo Perfil y ajustes | Sin herramientas docentes hasta aprobación |
| Tutoría | Académica | Tutoría, Mi grupo, Solicitudes | Horario | `TutorAssignmentScope`: solo grupos asignados |
| Coordinación / Dirección | Gestión | Gestión, Horario | Canal (desde aviso) | `CoordinationAccessPolicy`; Dirección requiere `DIRECTIVE_SUPERVISION` |
| Administración | Gestión | Administración, Horario, Clases, Canal | Editor de horarios/clases, atajos en Perfil | `InstitutionScopePolicy` + `AdminCapability` + MFA en cambios de riesgo; limitado a su institución |
| Administración general | Gestión | Administración, Cuentas, Académico, Historial | Además: invitaciones, historial auditado | Igual que Admin, sin límite de institución; invitaciones exigen `SUPER_ADMIN` |
| Invitado / Participante | Académica | Perfil | Ajustes; aceptar invitación si el correo está verificado | Sin datos escolares |

Las APK son una frontera de presentación, no un permiso: cada endpoint vuelve a verificar sesión viva, rol, capacidad, institución y recurso.

## 2. Hallazgos

| # | Severidad | Hallazgo | Estado |
|---|---|---|---|
| 1 | P2 · Lógica | Las comprobaciones de rol estaban repetidas unas 15 veces en `MainActivity` con fórmulas distintas (p. ej. «Súper Admin» repetida 6 veces; Clases usaba `experience == TEACHER`, que también es cierto para el docente pendiente). | **Corregido**: `ExperienceAccess` centraliza todo |
| 2 | P2 · Defensa en profundidad | Nueve rutas no tenían guardia (Inicio, Hoy, las tres de Tutoría, Gestión, Administración, Horario, Clases, Canal). Una pila restaurada, un enlace o una notificación podía mostrar la pantalla de otro perfil (la API seguía rechazando los datos). | **Corregido**: todas las rutas pasan por `authorizedComposable` |
| 3 | P2 · UX | El acceso denegado a representantes mostraba la pantalla sin «Volver» (solo cerrar sesión). | **Corregido**: un solo `denied` con «Volver» |
| 4 | P3 · Coherencia | Tres vocabularios distintos para el mismo perfil (p. ej. «Tutor académico» en acceso denegado y «Tutoría» en Perfil y pestaña). | **Corregido**: `ExperiencePresentation` |
| 5 | P3 · UX | Perfil nombraba el rol, pero no decía qué permite; el docente pendiente no sabía qué le faltaba (recomendación #6 de la auditoría de motion). | **Corregido**: fila «Qué puedes hacer» / «Validación pendiente» |
| 6 | P3 · UX | «Panel administrativo» y «Clases y canales» no tenían descripción, a diferencia del resto de filas. | **Corregido** |
| 7 | P3 · Accesibilidad | 8 indicadores de carga sin texto en las pantallas de inicio de cada perfil (Hoy docente, Administración, Cuentas, Historial, Gestión, Horario, editor académico, Canal). | **Corregido**: `V8InlineLoading`/`V8LoadingCard`, con los `testTag` conservados |
| 8 | Abierto | Administración y Administración general usan pestañas distintas para tareas que se solapan. Unificar es una decisión de producto. | Pendiente |
| 9 | Abierto | En la distribución `LEGACY`, Administración general obtiene pestañas que su guardia no permite abrir (ya pasaba antes). No se publica ninguna APK `LEGACY`. | Documentado |
| 10 | Abierto | Las comprobaciones de rol de la API siguen repartidas por servicio (19 en `AccountAdministrationService`). Están probadas y en buen estado; extraer una política de servidor debe ir en un cambio aparte. | Recomendación |
| 11 | Abierto | Quedan spinners sin texto en pantallas secundarias (Asistencia, envío de tareas, MFA, solicitud de personal). | Pendiente |

## 3. Modularización

`core:navigation` tiene ahora tres piezas nuevas:

- `ExperienceAccess`: indicadores por perfil activo (`studentTools`, `teacherTools`, `teacherPending`, `tutorTools`, `supervision`, `academicAdministration`, `superAdministration`, …) y `canOpen(Destination)`. Un perfil solo obtiene un indicador si la experiencia está concedida por sus roles en esa APK **y** tiene el rol correspondiente.
- `authorizedComposable(destination, access, denied)`: registra una ruta detrás de `canOpen`.
- `ExperiencePresentation`: nombres de perfil y rol, y el resumen de alcance.

Perfil y `MainActivity` usan estas piezas. Agregar una ruta implica añadirla a `canOpen` (el `when` es exhaustivo, así que el compilador obliga a hacerlo).

## 4. Verificación

| Comprobación | Resultado |
|---|---|
| `ExperienceAccessTest` (11 casos: toda pestaña de cada perfil se puede abrir, docente pendiente, APK cruzadas, Admin vs Súper Admin, supervisión, tutoría, invitado, vocabulario) | OK |
| Compilación Academic + Management y módulos afectados | OK |
| Pruebas unitarias Android completas (Debug, Academic, Management) | 375 casos, 0 fallos |
| `lintAcademicDebug` | OK |
| `ktlintCheck` de los módulos modificados | OK |
| Pruebas instrumentadas (`ProfileConsistencyUiTest`, `V14RoleNavigationUiTest`, …) | **Pendiente**: no hay emulador conectado |
