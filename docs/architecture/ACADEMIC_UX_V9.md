# Academic UX V9 — Admin-owned classes

## Decision

Academic classes are institutional assignments, not teacher-created chat rooms.

- ADMIN and SUPER_ADMIN create academic class assignments.
- Each class links subject, group, assigned teacher and room.
- The assigned teacher automatically receives membership and can operate the class.
- Teachers cannot create classes or enrollment codes.
- Students receive classes from institutional/group synchronization; temporary enrollment codes remain a fallback controlled by administration.
- Agenda keeps institutional schedule separate from personal planning.
- Class is the contextual entry point for channel and attendance.

## Navigation

Student: Hoy · Agenda · Clases · Perfil

Teacher: Hoy · Clases · Agenda · Perfil

Channel and attendance remain available as contextual capabilities rather than competing primary destinations.

## Compatibility

The existing classroom/channel storage is retained. Group metadata is additive and nullable at persistence level so older Mongo documents remain readable.

## Next waves

1. Group directory and searchable teacher/group pickers for admin.
2. Bulk schedule import scoped to group (PDF/image/API).
3. Automatic group membership projection into class memberships.
4. Class detail tabs: Resumen · Horario · Canal · Asistencia · Avisos.
5. Conflict-aware drag/drop schedule publishing for administrators.
