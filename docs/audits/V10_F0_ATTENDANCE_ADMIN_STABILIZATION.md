# V10 F0 — estabilización de asistencia y experiencia administrativa

Base: `main@841c8a31d891090a5aba593de9509664bf17d01b`. Issue #114, plan PR #122 / épico #113.

## Cambios implementados

1. `TeacherPassOpenPolicy`: verificaciones de **interfaz** para ocurrencia cancelada, finalizada (fin exclusivo), horario inválido y clases nocturnas. Para evitar que un botón previamente cargado abra un pase terminado, el ViewModel revalida la hora inmediatamente antes de la solicitud. No modifica datos ni evidencia de QR.
2. Error al abrir pase: HTTP 409 se explica en contexto (una sesión puede estar cerrada/ocupada o la clase cancelada), sin confundirlo con «No pudimos actualizar» o mostrar el mensaje técnico del backend. Otros errores conservan mensaje normal.
3. Sin sesión del docente: la UI no afirma «Lista de estudiantes: 0» como si hubiese consultado una sesión. Se diferencia **sin pase activo**, **roster pendiente** y **roster recibido vacío**. Entrada escolar permanece independiente.
4. `AdminHomeScreen`: elimina las métricas falsas «Servicios OK / Sesiones activas / Auditoría 0 alertas» y el CTA «Ver métricas de asistencia», que dirigía a la pantalla de alumno/docente y no a un panel de administración.
5. `RoleExperienceResolver`: coordinación y administración ya no presentan una pestaña de asistencia administrativa sin pantalla adecuada. Admin utiliza el destino de Clases ya existente. El panel de asistencia global real se construye después, en issue #117.

## Matriz de pruebas esperadas (no afirmar aprobadas hasta CI / emulador)

- Docente en Álgebra 09:00–10:30, son 10:29: botón «Abrir pase» habilitado; a las 10:30: «Clase finalizada», botón deshabilitado. Reintentar en 10:33 no llama a la API.
- Ocurrencia cancelada/inválida: botón deshabilitado y motivo accesible. Clases nocturnas: fin del día siguiente.
- Docente con pase activo y lista aún no recibida: «Registros pendientes», no «0 estudiantes». Si servidor sí devuelve lista vacía, mostrar el 0 real.
- Respuesta 409 de `POST /attendance/sessions`: mensaje contextual y actualización manual. No borrar datos de la sesión por un fallo. Doble apertura válida del mismo pase sigue gobernada por Ktor.
- Admin/Coordinación sin rol STUDENT/TEACHER no pueden navegar a una pantalla de asistencia que devuelve «modo no compatible» desde su bottom nav. `AdminHome` no muestra métricas inventadas.
- Regresión de Estudiante QR escolar/pase en aula, Tutorías y Docente V8.1; diseño/tema existente sin duplicados.
- Emulador 360/390/430dp, tablet, fuente 200%, tecla Atrás y rotación; probar fecha/hora del campus vs dispositivo.

## Límites P0/P1 conscientes

- La **API sigue siendo la autoridad** de concurrencia, asignaciones, estados de sesión, QR y asistencia. Estas reglas de hora en cliente son barreras UX defensivas, no una verificación de seguridad. El horario del dispositivo puede estar desajustado o en zona diferente al campus. Antes de operar en una institución con otras zonas horarias, mover la elegibilidad de apertura a una política con reloj del servidor y timezone institucional.
- No se implementó el panel administrativo completo de asistencia. No ocultar este pendiente ni presentar métricas ficticias. Ticket #117.
- No se alteró el parsing del PDF, todavía con trabajo en PR #112.
- Tests JVM de `TeacherPassOpenPolicyTest` y test de rutas administrativas añadidos, pero el estado de build/emulador debe verificarse en el commit exacto del PR.
