# Compañero — ingreso de Súper Admin con correo independiente

**Cuenta designada exclusivamente para Compañero:** `erik55509@hotmail.com`.

Este documento **no es una concesión de permisos** y su presencia no autoriza
roles por coincidencia de correo. Nunca convertir la cuenta existente
`erik55509@gmail.com` ni reenviar credenciales de otro perfil.

## Estado confirmado al 3-nov-2026

- La cuenta Hotmail no estaba registrada en las colecciones consultadas de
  `companero_dev` ni `companero_qa`. No hay evidencia aquí sobre la
  base efectiva del entorno productivo de Render.
- La invitación V26 de Resend existe como plantilla publicada y el remitente
  de `companero.manecomb.com` está verificado. La plantilla necesita un enlace
  real, personal y con expiración; no usar URL ficticia ni sustituirla por
  la página principal.
- La implementación de invitaciones en Ktor es aún prototipo no expuesto:
  aceptación no equivale a promoción administrativa.
- `OwnerBootstrap` está cableado para **QA aislado** con correo verificado,
  MFA, prueba de autorización de vigencia corta, CAS, auditoría y reserva
  persistente única. Rechaza explícitamente producción.
- **Ningún mensaje de invitación se ha enviado ni se ha concedido Súper Admin.**

## Orden de habilitación (obligatorio)

1. Terminar flujo de administradores por carrera, selector Android y
   sincronización firmada de grants, revocaciones, horarios y QR; no asumir
   que la capa de política Ktor por sí sola constituye integración completa.
2. Obtener CI obligatorio verde de SIII, Ktor y Android; incorporar los PR
   con resolución de conflictos y probar despliegues y reingreso de sesión.
3. Verificar que el servicio productivo usa una base MongoDB de producción
   distinta de desarrollo y QA, y que la autoridad HMAC de SIII coincide.
4. Finalizar API de invitaciones: creación autorizada, entrega persistente,
   token de un solo uso, verificación de destinatario, expiración, aceptación
   autenticada y auditoría. La plantilla de Resend no se invoca hasta ese punto.
5. Crear la cuenta Hotmail desde el registro real de la aplicación; confirmar
   propiedad del correo e inscribir MFA del dispositivo del titular.
6. En QA, ejecutar `OwnerBootstrap` únicamente con los datos **reales** de
   cuenta y revisión: `QA_OWNER_BOOTSTRAP_ENABLED=true`,
   `QA_OWNER_BOOTSTRAP_ACCOUNT_ID`, `QA_OWNER_BOOTSTRAP_EMAIL` con
   el destinatario indicado, `QA_OWNER_BOOTSTRAP_EXPECTED_REVISION`,
   `QA_OWNER_BOOTSTRAP_OPERATION_ID`, `QA_OWNER_BOOTSTRAP_OPERATOR`,
   `QA_OWNER_BOOTSTRAP_REASON`, `QA_OWNER_BOOTSTRAP_EXPIRES_AT`
   y `QA_OWNER_BOOTSTRAP_PROOF` emitido mediante MFA para el digest exacto.
   Aplicar solo durante el arranque controlado, auditar e inmediatamente
   deshabilitar el flag. No escribir códigos, contraseñas ni prueba MFA en Git.
7. Para producción, establecer un proceso separado de designación propietaria
   con autorización demostrable y auditoría. Nunca modificar la restricción
   de `OwnerBootstrap` para permitir producción ni asignar `SUPER_ADMIN`
   mediante actualización directa de MongoDB.
8. Emitir invitación sólo cuando exista un enlace real y comprobado y la
   promoción segura esté disponible. Comprobar aceptación de proveedor,
   entrega cuando haya webhook validado, registro, autenticación, MFA,
   alcance institucional y revocación.
9. Verificar en una cuenta estudiante separada que no se filtra navegación ni
   privilegios de Súper Admin. No reutilizar el QR Wi-Fi como asistencia.

## Señales de bloqueo

No se cumple el gate de envío si CI está pendiente, hay PR sin merge,
un despliegue no está verificado, no hay usuario Hotmail verificado,
el enlace de invitación no existe, no hay promoción segura en producción,
o las concesiones de carrera no han pasado E2E con dos instituciones.
