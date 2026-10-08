# Docente V8 — revisión visual y diseño de vistas (2026-10-08)

> Estado: implementación en PR, **no** aceptación visual física. Basado en `main` @ `135b9c6c` y contrato visual `docs/ui/V8-RED-EDITION-VISUAL-CONTRACT.md`.

## Auditoría Git

- Panel docente actual: `feature:attendance/TeacherHomeScreen.kt`; ya maneja grupos, QR de clase, métricas por sesión y próximas clases de API.
- PR #90 (V9 campus attendance) cambia **TeacherHomeScreen** + AttendanceScreen/VM; no se modifica aquí para evitar pisarlo.
- `feature:classroom/ClassroomScreen.kt` reutilizaba la misma superficie de administración/estudiante para un usuario docente y no distinguía correctamente la experiencia seleccionada en una cuenta multirrol.
- `feature:grading/GradebookScreen.kt` exigía introducir un ID de clase en texto libre, propenso a errores. `ClassroomRepository.classrooms()` ya obtiene asignaciones autorizadas con `canManage`.
- `feature:channel/ChannelScreen.kt` tiene un formulario de publicación extenso. El PR #93 está modificando esta misma pantalla; se delega a ese PR para evitar conflictos y cambios visuales duplicados.

## Contrato de diseño del perfil maestro

1. **Inicio docente (V8 + futuro V9):** mostrar primero la siguiente clase/QR activo, resúmenes reales y acciones; no duplicar la navegación ni fabricar números. Ajustar cuando #90 esté integrado.
2. **Mis clases (implementado en este PR):** UI V8 de lectura de materias y grupos activos asignados, con acceso directo a asistencia, canal y evaluación. Admin crea clases; profesor **no**.
3. **Evaluación (implementado en este PR):** seleccionar clase autorizada `canManage` por chips; periodo, ponderaciones hasta 100%, importar XLSX/CSV y revisión. El servidor sigue siendo fuente de permisos. Si no hay gateway institucional, mostrar resultado de error, nunca afirmar sincronización real.
4. **Canal (gestionado en PR #93):** preservar lectura y respuestas predefinidas; evaluar un compositor plegado, enlaces opcionales y jerarquía compacta una vez integradas las correcciones visuales del #93. No permitir mensajes libres a alumnos.
5. **Horario:** reutilizar agenda académica y fuente real; no introducir un calendario paralelo.
6. **Pase QR:** mantener diferenciados QR de entrada escolar y QR en clase. El PR #90 V9 ya se integró en main mientras se realizaba esta auditoría; validar regresiones en la próxima revisión visual sin tocar su lógica en este PR.

## Principios UX

- V8CampusBackdrop, V8BrandHeader, V8RedColors, V8GlassCard; sin nuevos frameworks ni cambio global del tema.
- Acciones pequeñas pero con área táctil utilizable; densidad fluida en 360, 390, 430 dp y tablet.
- Cuentas TEACHER+STUDENT: mostrar Mis clases docente exclusivamente al elegir experiencia docente; no exponer creación por ser docente.
- Estados loading / error / vacío honestos. Pérdida de conectividad no se muestra como información actualizada.
- Campos y notas reales, sin arreglos de mocks en producción ni IDs escritos manualmente.
- Evitar `confirm()`, `LayoutAnimation`, hardcodes de datos/estadísticas.

## Matriz QA obligatoria para cerrar

- Roles: STUDENT, TEACHER_PENDING, TEACHER, TEACHER+STUDENT, ADMIN y cambio de experiencia.
- Mis clases: 0/1/N clases asignadas; aulas vacías; sin duplicados; rutas a QR/canal/evaluación.
- Evaluación: 0/1/N materias; selección; porcentajes 0/100/130; XLSX/CSV válido/malformado; columnas faltantes; offline; servidor sin integración.
- Canal (PR #93 y posterior): 0/N canales, aviso sin enlace, con enlace HTTPS, intento vacío, fallo de publicación, cierre y reconexión.
- Compose/uiTests reales en emulador, TalkBack, fuente 1.3–1.5x, reduce motion, modo claro/oscuro, viewport 360/390/430/768 dp.
- Capturas reales comparadas con referencias del usuario. **No marcar aprobado hasta verificarlas**.
- CI: compilación Android, lint, tests y smoke de instrumentación completos; no fusionar si falla el gate.
