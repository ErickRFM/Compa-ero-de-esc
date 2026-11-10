# Compañero — invitaciones de registro por correo (bloque backend)

## Estado de este PR

Implementación experimental de API con envío mediante el Resend ya configurado para verificación de correo. En producción, las rutas nuevas permanecen **desactivadas por defecto**. Sólo se habilitan con `PRODUCTION_INVITATIONS_ENABLED=true` y con MongoDB, JWT, Resend, cifrado MFA y secreto firmado de webhook configurados. Fusionar el PR no activa envíos por sí solo, no crea cuentas y **nunca** asigna SUPER_ADMIN ni otros roles.

## Secuencia prevista en QA

1. Un operador con sesión nativa activa, correo verificado y rol SUPER_ADMIN genera un `operationId` nuevo.
2. `POST /admin/invitations/prepare` valida identidad, institución habilitada, destinatario y tipo de invitación; devuelve `resourceId=platform-invitations` y `payloadDigest`.
3. El operador obtiene MFA step-up mediante `POST /auth/mfa/step-up`, con `action=ADMIN_INVITE`, `resourceId`, `operationId` y `payloadDigest` inalterados.
4. `POST /admin/invitations` recibe los mismos datos y la prueba MFA de cinco minutos; guarda solo la huella SHA-256 de un código aleatorio de 256 bits en MongoDB y remite código + ID con el proveedor Resend configurado. El outbox Mongo cifra el código y destinatario para reintentos durables; borra ambos al aceptar/entregar/fallar y expira con la invitación.
5. El destinatario se registra **por separado** desde Android con el mismo correo y confirma el correo de verificación.
6. `POST /auth/invitations/accept` exige JWT nativo vigente, cuenta verificada, ID, código y `operationId` de aceptación. La operación es atómica en MongoDB y **no concede privilegios**.

Ejemplo de cuerpo para los dos primeros endpoints (sin prueba en prepare):

```json
{"recipient":"user@example.test","institutionId":"UPTLAX","profile":"STUDENT","operationId":"new-operation-id-123","proof":null}
```

La operación de envío incorpora `proof`; no incluir nunca contraseñas, OTP o códigos de invitación en el repositorio, logs o este documento. El proveedor puede informar ACCEPTED (aceptó la solicitud), UNCONFIRMED o FAILED: ACCEPTED **no** acredita recepción en el buzón.

## Bloqueos antes de activar envíos

- [x] Rutas desactivadas en producción por política (`settings.environment == Environment.PRODUCTION`), no por omisión accidental.
- [x] Android Gestión emite invitaciones con MFA (ID + código) y Académica/Gestión las aceptan; prueba instrumentada de aceptación en CI.
- [x] Contratos de invitación unificados (`shared/contracts/RegistrationInvitationContracts.kt`) sin duplicados entre API y Android; la aceptación vive en `InvitationAcceptanceService` y la ruta reutiliza el mismo contrato cableado.
- [x] Rate limiting compartido entre réplicas (`MongoRateLimitStore`) para emisor y aceptación; revocación y reintento de invitaciones en `/admin/invitations`.
- [x] Outbox durable cifrado con worker de reintentos, reintento manual que reemplaza el token y TTL de expiración (PR #297)
- [x] Endpoint Svix firmado, ventana anti-replay, tamaño acotado, tags de correlación y persistencia idempotente de eventos de Resend (`platform_audit_events`) (PR #300)
- [ ] Configurar webhooks independientes en Resend para QA y producción y probar entrega real; la cuenta consultada todavía no tiene webhooks configurados
- [ ] Auditoría persistente completa de actor/operación para todas las acciones administrativas (el registro de eventos de correo ya está implementado)
- [ ] Verificar aislamiento de QA, institución habilitada, entrega real desde Resend y control de permisos por carrera
- [ ] Ejecutar CI Android/API verde y casos de revocación, replay, expiración, concurrency y cuenta suspendida
- [ ] Completar #280 y, para SUPER_ADMIN, #281: una invitación no sustituye autorización del titular, MFA ni bootstrap independiente
- [ ] Activación productiva mediante PR explícito posterior y certificación autenticada

Este bloque es una base de implementación, no autorización de enviar el correo de acceso todavía.


La implementación del webhook y su procedimiento de activación están descritos en `docs/operations/RESEND_WEBHOOK_SETUP.md`. El código no habilita ni crea webhooks remotos automáticamente; se requiere configurar el endpoint y secreto por ambiente.

## Variables productivas

- `PRODUCTION_INVITATIONS_ENABLED=true` sólo se acepta si también existen `MONGODB_URI`, `JWT_SECRET`, `VERIFICATION_EMAIL_API_KEY`, `VERIFICATION_EMAIL_SENDER`, `MFA_ENCRYPTION_KEY` y `RESEND_WEBHOOK_SECRET` válido (`whsec_`, 32 bytes decodificados).
- No activar este flag hasta desplegar el endpoint `POST /webhooks/resend` y probar un ciclo completo de correo en QA.
- Runbook de Resend: `docs/operations/RESEND_WEBHOOK_SETUP.md`.
