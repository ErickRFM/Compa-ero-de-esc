# Auditoría Preflight V11: Representantes de Grupo y Canal Institucional

**Fecha:** 2026-10-09
**Módulo:** Representantes de Grupo y Canal Institucional V11
**Repositorio:** `ErickRFM/Compa-ero-de-esc`
**Entorno de Trabajo:** `C:\proyectos\esc-v11` (git worktree en `feat/v11-group-representatives`)

---

## Inventario de Componentes y Matriz EXISTE / PARCIAL / FALTA

| Componente | Estado | Ubicación en Código |
| :--- | :--- | :--- |
| **Contratos de Tutoría** | EXISTE | `shared/contracts/src/.../TutoringContracts.kt` |
| **Asignación de Tutoras** | EXISTE | `services/api/.../tutoring/TutorAssignmentService.kt` |
| **Canales y Publicaciones** | EXISTE | `services/api/.../channel/ChannelService.kt` |
| **Contratos de Representantes** | **FALTA** | `shared/contracts/.../GroupRepresentativeContracts.kt` |
| **Persistencia Representantes** | **FALTA** | `services/api/.../representatives/GroupRepresentativeRepository.kt` |
| **Servicio Representantes** | **FALTA** | `services/api/.../representatives/GroupRepresentativeService.kt` |
| **Rutas Ktor Representantes** | **FALTA** | `services/api/.../representatives/GroupRepresentativeRoutes.kt` |
| **Canal Institucional Representantes** | **PARCIAL** | Requiere extensión de `ChannelAccessPolicy.kt` |
| **UI Tutora Representantes** | **PARCIAL** | Extender `TutorGroupsScreen.kt` |
| **UI Estudiante Invitación/Insignia** | **PARCIAL** | Extender `ProfileScreen.kt` |
| **UI Admin Representantes** | **PARCIAL** | Extender `AdminHomeScreen.kt` |

---

## Reglas de Seguridad y Privacidad
1. **Sin modificación de `UserRole`**: Los cargos `CHIEF` y `DEPUTY` son asignaciones temporales de recurso en un grupo académico y periodo.
2. **Privacidad de Tutorías**: El cargo de representante NO otorga acceso a notas internas de tutoría, expedientes ni calificaciones ajenas.
3. **Revocación Inmediata**: La revocación del cargo invalida de inmediato el acceso al canal institucional en el servidor.
