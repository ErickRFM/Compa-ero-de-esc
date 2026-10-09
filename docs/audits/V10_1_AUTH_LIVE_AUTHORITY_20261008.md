# V10.1 — autoridad vigente de identidad y sesión

Base: `97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca`. Alcance parcial de #115, independiente del QR de #123.

## Problema y comportamiento

La API comprobaba la existencia de la sesión, pero no su generación ni el estado vigente de la cuenta. Un JWT previo a un refresh continuaba autenticando; desactivar una cuenta o quitar un rol no invalidaba su acceso. Además, una cuenta nativa eliminada podía caer en la resolución institucional durante el refresh.

Cada petición protegida ahora requiere sesión vigente, JWT con caducidad explícita, generación actual, identidad activa en su fuente original y roles iguales a los vigentes. El refresh utiliza esa misma autoridad. Las sesiones guardan `NATIVE` o `INSTITUTIONAL`; una fuente ausente/desconocida exige login nuevo. Nunca se infiere otra fuente a partir de una cuenta que desapareció.

La indisponibilidad del repositorio o proveedor rechaza el acceso con `503 DEPENDENCY_UNAVAILABLE`, sin divulgar la excepción del proveedor, credenciales, identificadores ni direcciones de base de datos. La revocación o identidad inexistente/inactiva produce `401`. La cancelación de coroutines se propaga.

## Compatibilidad y operación

- Los DTO HTTP no cambian. Los JWT existentes ya emitidos incluyen generación y caducidad.
- Las sesiones anteriores sin `identitySource` requieren una nueva autenticación; no se borran cuentas ni datos.
- `IdentityProvider.currentState` debe devolver estado activo y roles vigentes. El valor predeterminado no concede acceso. El único adaptador de identidad disponible actualmente es local/mock; su uso fuera de LOCAL está prohibido por el registro existente. Una integración institucional real permanece pendiente.
- La consulta vigente añade lectura de cuenta/proveedor a cada petición protegida. No se cachea una autorización obsoleta para tolerar una dependencia caída.
- Rollback: revertir el commit squash completo. Las cuentas creadas siguen siendo compatibles; el campo adicional de sesión no requiere eliminación. Un rollback reintroduciría las vulnerabilidades y necesita decisión explícita de seguridad.

## Evidencia y límites

Pruebas HTTP reales con Ktor y repositorios locales cubren rotación, desactivación nativa/institucional, retirada de roles, eliminación de identidad nativa, fuente legacy, claims de rol/propietario/generación/actividad, JWT sin caducidad e indisponibilidad durante petición y refresh. Se observaron fallos antes de corregir generación, actividad, fallback, caducidad y clasificación de indisponibilidad.

La revisión independiente encontró dos problemas adicionales: actividad Mongo ausente interpretada como activa y revocación por replay perdida ante una rotación concurrente. Se reprodujeron ambos (2 fallos de 4 pruebas) y se corrigieron en una pasada. La cuenta Mongo ahora exige booleano explícito `true`; el replay revoca con una actualización atómica por sesión activa y hash anterior reconocido, sin depender del token actual leído previamente. Véase [atomicidad Mongo](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

Tras las correcciones, API (181), contratos (16) y validación (7): **204 pruebas, cero fallos**, además de `installDist`, en `auth-review-full-green.log`. Las regresiones Mongo ejercitan los repositorios y el driver coroutine con una frontera reactiva determinista; no equivalen a una base Mongo real. El checkout contiene cambios de apariencia de otro chat; CI deberá validar el árbol de seguridad aislado del SHA publicado antes del merge.

| Escenario | Estado |
| --- | --- |
| Autoridad actual de generación/actividad/roles en HTTP local | PASS; suite completa tras correcciones |
| Cuenta Mongo con actividad ausente/null/falsa o tipo incorrecto | PASS; acceso, refresh y login denegados |
| Replay concurrente, hash desconocido, sesión distinta y revocación previa | PASS en frontera reactiva determinista |
| MongoDB real, migración de sesiones y caída del proveedor externo | NOT_RUN |
| MFA, recuperación de contraseña, dispositivos y abuso distribuido | NOT_RUN; alcance posterior de F2 |
| Permisos por institución/recurso/grant y privacidad Tutor | NOT_RUN; no quedan resueltos por comparar roles |
| Experiencia Android restringida cuando caduca JWT sin red | NOT_RUN; alcance posterior F2/F4 |
| CI del SHA final de este cambio | NOT_RUN |

Este documento no declara terminada F2 ni V10.1. El PR #123 continúa en borrador: su prueba física con dos teléfonos permanece BLOCKED. En su SHA `bab1665`, los checks de compilación/API pasaron; instrumentación pasó en la ejecución de rama, mientras la ejecución de PR agotó el tiempo descargando el SDK/emulador antes de lanzar pruebas.

La numeración F2 corresponde a la misión del usuario; el plan versionado V10.1 enumera seguridad como F1. La igualdad de roles no certifica permisos por recurso/institución. La revisión dejó para una tarea posterior el rechazo completo de roles desconocidos en un JWT firmado (no se observó elevación), y no certificó Mongo real, proveedor productivo, MFA/reset/dispositivos, privacidad Tutor, cancelación extremo a extremo en Ktor, operaciones ya autorizadas en vuelo ni Android/QR/CI. Estos límites permanecen abiertos y no justifican una declaración de V10.1 completa.
