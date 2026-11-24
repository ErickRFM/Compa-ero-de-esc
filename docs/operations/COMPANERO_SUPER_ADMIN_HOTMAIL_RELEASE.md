# Primera cuenta `SUPER_ADMIN` de Compañero

## Arquitectura de operación

Compañero usa una API productiva, las aplicaciones Académica y Gestión y la base existente `companero_dev`. QA ya no forma parte de los flujos desplegables. No crear otra cuenta ni modificar roles para hacer avanzar el alta.

Producción está desplegada desde el commit `904d48c17e9428475bc4be2f818010197d2d2041`. La API responde `/ready` con MongoDB `up`; el usuario de Render tiene `readWrite` únicamente en `companero_dev`. El operador omitió el respaldo para esta operación.

## Secuencia autorizada

1. Confirmar que el servicio de Render responde desde el commit aprobado, usa `APP_ENV=production` y `MONGODB_DATABASE=companero_dev`.
2. Generar un código de invitación de 32 bytes, su SHA-256 y un token de despacho temporal de 32 bytes. Configurar temporalmente `OWNER_INVITE_CODE` y `OWNER_INVITE_DISPATCH_TOKEN` junto con `OWNER_INVITE_EMAIL`, `OWNER_INVITE_CODE_SHA256`, `OWNER_INVITE_EXPIRES_AT` y `PROD_OWNER_ISOLATION_CERTIFIED=true`. El endpoint `/internal/owner-invitation/send` manda el código al correo fijo con el emisor Resend existente. La entrega se reserva una sola vez en `owner_registration_invitations`; una respuesta HTTP 202 significa que Resend aceptó el mensaje, no confirma que llegó al buzón.
3. Tras recibir HTTP 202, borrar del entorno `OWNER_INVITE_CODE` y `OWNER_INVITE_DISPATCH_TOKEN`; conservar el hash, destinatario y caducidad hasta completar el registro. Si el envío falla, no reutilizar el mismo intento; generar otro código, hash, token y caducidad.
4. El titular inicia el registro `OWNER_INVITED` con el mismo correo y la contraseña actual, e ingresa el código de invitación de un solo uso recibido por correo. Para una cuenta `PENDING_VERIFICATION` sin roles, el servicio cambia la intención de registro y vuelve a emitir la verificación. Para una cuenta activa, verificada y sin alcance académico, la conversión conserva el ID, el hash de contraseña, la verificación, el MFA y el rol `STUDENT`; ambas rutas revocan sesiones previas. No se admiten cuentas con institución, solicitudes de rol ni privilegios administrativos.
5. La cuenta pendiente verifica el correo desde el mensaje nuevo y recibe `WORKSHOP_PARTICIPANT`. La cuenta activa ya verificada no requiere otra verificación y conserva `STUDENT`. Ninguna ruta concede `SUPER_ADMIN` en el registro.
6. MFA del titular debe seguir activo. El bootstrap requiere prueba MFA nueva y firma Ed25519 del operador, limita la autorización a 15 minutos y guarda auditoría/reserva atómica. Para la conversión de una cuenta activa, añade `SUPER_ADMIN` y conserva `STUDENT`.
7. Ejecutar el bootstrap de producción mediante las variables `PROD_OWNER_BOOTSTRAP_*`; retirar todas las variables temporales y verificar login nuevo, rol efectivo, auditoría y revocación de sesiones previas.

Una invitación por correo no concede roles. El nombre o coincidencia de una dirección tampoco concede privilegios. No enviar códigos, pruebas MFA, claves ni secretos por GitHub, logs o chat.

## Estado operativo

- La credencial de MongoDB de Render está limitada a `companero_dev` y `/ready` confirmó conectividad.
- El servicio QA de Render todavía existe y se debe borrar desde el dashboard.
- El titular debe completar registro con la invitación recibida, contraseña actual y MFA. Aún no se ha concedido `SUPER_ADMIN`.
- No registrar `SUPER_ADMIN` manualmente ni alterar contraseña, MFA o roles para adelantar el flujo.
