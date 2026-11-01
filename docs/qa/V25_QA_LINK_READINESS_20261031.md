# QA-UNIV — revisión de vinculación con Compañero

Consulta de solo lectura realizada el 31 de octubre de 2026. El usuario indicó las cuentas existentes `qa-student`, `qa-teacher` y `qa-admin`. No se cambiaron contraseñas, roles, inscripciones, sesiones ni datos remotos.

## Estado observado

| Dato | SIII | Compañero |
| --- | --- | --- |
| `qa-student` | Membresía activa, rol alumno, matrícula `MAT-QA-2026` | No se encontró contraparte con su usuario, UUID SIII o correo actual de SIII; sin enlace institucional de esa matrícula |
| `qa-teacher` | Membresía activa, rol docente, clave `EMP-QA-2026` | No se encontró contraparte con su usuario, UUID SIII o correo actual de SIII; sin enlace institucional de esa clave |
| `qa-admin` | Membresía activa, rol administrador | No se encontró contraparte con su usuario, UUID SIII o correo actual de SIII; su autorización administrativa no se crea mediante enlaces académicos |
| Grupo/materia | `SEC-QA1` / Calidad de Software; alumno inscrito y docente asignado | Sin snapshot de la sección QA |
| Horario | Tres patrones, cero ocurrencias persistidas | No existe una clase oficial publicada que pueda abrirse o cancelarse en el flujo de asistencia |
| Sincronización | Sin versión de fuente ni eventos de outbox de la sección | No hay una proyección que habilite autoridad académica |

Las cuentas se encontraron en la institución `QA-UNIV` del proyecto principal SIII; no en `siii-staging-p01`. El clúster consultado para Compañero fue `companero_dev`. Esto identifica los recursos observados; no certifica aislamiento de un entorno piloto completo.

## Qué falta para que funcionen en la app

1. Identificar o dar de alta y aprobar las contrapartes nativas de Compañero dentro de la institución QA, con correo verificado y roles autorizados. No reutilizar por suposición el UUID de SIII como identidad nativa. Las credenciales de SIII y Compañero no están federadas actualmente.
2. Un administrador nativo con MFA vincula **cuentas ya aprobadas** usando `POST /admin/accounts/institutional-identity-links`, con la prueba vigente ligada al comando completo. Alumno usa `entityType=student`, `externalId=MAT-QA-2026`; docente usa `entityType=teacher`, `externalId=EMP-QA-2026`. Se necesitan sus `nativeUserId` reales. No escribir enlaces directamente en Mongo ni saltar MFA.
3. La cuenta administrativa requiere el procedimiento separado de autorización nativa. No convertir un enlace académico en privilegios administrativos.
4. Publicar las ocurrencias QA mediante el flujo real de programación de SIII; un patrón recurrente por sí solo no equivale a una clase persistida. Después reconciliar la sección mediante el endpoint auditado `POST /api/v1/institutions/:institutionId/companion-sync`, con la versión de fuente vigente y motivo.
5. Con outbox, HMAC, enlaces y autoridad configurados para la institución, verificar `/academic/load`; entonces Inicio y Horario recibirán la misma clase oficial. Cancelar una ocurrencia desde SIII debe revocar su apertura/pase y actualizar la proyección.

Esta revisión no afirma haber realizado la vinculación: faltan las contrapartes nativas aprobadas y el contexto administrativo/MFA para ejecutar esas operaciones. No se ejecutó el provisionador SIII existente, porque recrea/restablece credenciales y no resuelve por sí mismo el vínculo nativo ni la publicación de ocurrencias.

## Corrección de presentación

Mientras no exista autoridad oficial, la app muestra **Vinculación escolar pendiente** y conserva la agenda personal por separado. No anuncia sincronización institucional disponible ni usa el horario importado para permitir asistencia. Un 401 retira el horario de la sesión; un 403 conserva únicamente la agenda personal independiente.
