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

## Group synchronization

Implemented in the V9 group-sync wave:

- persistent academic groups and group memberships;
- admin-only group creation and member assignment;
- students inherit classes from their group without per-class invitation;
- group-owned schedule blocks are projected into the student's agenda;
- independent platform accounts can receive a group schedule even when the school API has no identity for them;
- enrollment codes remain only as a fallback when group synchronization is unavailable.

## Next waves

1. Searchable teacher/student/group pickers instead of raw account IDs.
2. Bulk schedule import scoped to group (PDF/image/API).
3. Class detail tabs: Resumen · Horario · Canal · Asistencia · Avisos.
4. Conflict-aware drag/drop schedule publishing for administrators.
5. Roster management, transfers between groups and audit trail.
