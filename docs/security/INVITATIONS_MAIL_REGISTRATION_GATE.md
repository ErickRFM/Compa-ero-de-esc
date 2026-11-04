# Compañero — invitaciones de registro por correo (bloque backend)

## Estado de este PR

Implementación experimental de API con envío mediante el Resend ya configurado para verificación de correo. En producción, las rutas nuevas están **desactivadas**. No envía mensajes al fusionar el PR, no crea cuentas y **nunca** asigna SUPER_ADMIN ni otros roles.

## Secuencia prevista en QA

1. Un operador con sesión nativa activa, correo verificado y rol SUPER_ADMIN genera un `operationId` nuevo.
2. `POST /admin/invitations/prepare` valida identidad, institución habilitada, destinatario y tipo de invitación; devuelve `resourceId=platform-invitations` y `payloadDigest`.
3. El operador obtiene MFA step-up mediante `POST /auth/mfa/step-up`, con `action=ADMIN_INVITE`, `resourceId`, `operationId` y `payloadDigest` inalterados.
4. `POST /admin/invitations` recibe los mismos datos y la prueba MFA de cinco minutos; guarda solo la huella SHA-256 de un código aleatorio de 256 bits en MongoDB y remite código + ID con el proveedor Resend configurado.
5. El destinatario se registra **por separado** desde Android con el mismo correo y confirma el correo de verificación.
6. `POST /auth/invitations/accept` exige JWT nativo vigente, cuenta verificada, ID, código y `operationId` de aceptación. La operación es atómica en MongoDB y **no concede privilegios**.

Ejemplo de cuerpo para los dos primeros endpoints (sin prueba en prepare):

```json
{"recipient":"user@example.test","institutionId":"UPTLAX","profile":"STUDENT","operationId":"new-operation-id-123","proof":null}
```

La operación de envío incorpora `proof`; no incluir nunca contraseñas, OTP o códigos de invitación en el repositorio, logs o este documento. El proveedor puede informar ACCEPTED (aceptó la solicitud), UNCONFIRMED o FAILED: ACCEPTED **no** acredita recepción en el buzón.

## Bloqueos antes de activar envíos

- [ ] Implementar en Android Gestión la pantalla para emitir invitaciones con MFA y en Académica/Gestión la pantalla para aceptarlas usando ID + código; acompañarla de pruebas instrumentadas
- [ ] Completar un outbox durable para recuperar fallos entre la escritura Mongo y el envío; un proceso interrumpido puede dejar un correo no procesado o una invitación sin mensaje
- [ ] Hacer rate limiting compartido entre réplicas, auditoría persistente de emisor/operación y redacción de secretos en trazas
- [ ] Verificar aislamiento de QA, institución habilitada, entrega real desde Resend y control de permisos por carrera
- [ ] Ejecutar CI Android/API verde y casos de revocación, replay, expiración, concurrency y cuenta suspendida
- [ ] Completar #280 y, para SUPER_ADMIN, #281: una invitación no sustituye autorización del titular, MFA ni bootstrap independiente
- [ ] Activación productiva mediante PR explícito posterior y certificación autenticada

Este bloque es una base de implementación, no autorización de enviar el correo de acceso todavía.
