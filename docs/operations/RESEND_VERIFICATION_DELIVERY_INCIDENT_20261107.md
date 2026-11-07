# Incidente de entrega de verificación por correo — 2026-11-07

## Alcance

Este incidente corresponde al correo de **verificación de cuenta** enviado al registrarse, no a la invitación administrativa. Son flujos distintos: `/auth/verification/resend` usa `ResendVerificationEmailGateway`; las invitaciones administrativas usan `ResendInvitationEmailGateway`.

## Evidencia revisada

- El cliente Android muestra la pantalla de verificación para `erik55509@hotmail.com`.
- El backend genera el desafío y registra el resultado del transporte.
- El adaptador de verificación usa `VERIFICATION_EMAIL_API_KEY` y `VERIFICATION_EMAIL_SENDER`, envía texto y HTML y utiliza una clave de idempotencia por operación.
- Una respuesta HTTP 2xx con un `id` de Resend significa **aceptación por el proveedor**, no entrega al buzón.
- La consulta de eventos del proveedor mostró tres eventos `bounced` para el destinatario. Por lo tanto, no se debe comunicar que el correo fue entregado.
- No hay webhooks de eventos de Resend configurados en la cuenta consultada. El backend no recibe todavía la confirmación asíncrona de entrega/rebote.

## Decisión inmediata

1. No cambiar `MONGODB_URI`, `MONGODB_DATABASE` ni migrar la base de datos por este incidente.
2. No marcar la cuenta como verificada ni conceder roles privilegiados por un rebote.
3. No insistir con reenvíos rápidos: el endpoint aplica cooldown de 60 segundos y un máximo de tres desafíos por hora.
4. Mantener el desafío vigente hasta que expire o se emita uno nuevo bajo el límite establecido.
5. Inspeccionar el detalle del evento de rebote en Resend (código SMTP, respuesta remota y tipo de rebote) antes de cambiar el remitente o la configuración DNS.

## Corrección de producto requerida

Implementar un webhook autenticado de Resend (firma Svix, tolerancia temporal y protección contra replay) para eventos `email.delivered`, `email.bounced`, `email.failed` y `email.delivery_delayed`.

El receptor debe:
- validar firma antes de procesar el JSON;
- limitar el tamaño del cuerpo y validar el tipo/ID del evento;
- ser idempotente por ID de evento;
- persistir el estado asociado al ID del correo del proveedor, sin guardar tokens de verificación;
- nunca convertir un rebote en una verificación exitosa;
- exponer estados diferenciados: solicitado, aceptado por proveedor, entregado, rebotado y fallo;
- no registrar claves, tokens, códigos de verificación ni el cuerpo completo del correo.

## Criterio de cierre

El incidente solo se cierra cuando:
- el detalle SMTP del rebote se ha identificado y corregido;
- un nuevo desafío (no reutilizar un código anterior) se envía a la dirección correcta;
- Resend reporta `delivered` para el nuevo ID;
- la app permite confirmar ese código y el backend registra `emailVerifiedAt`;
- la cuenta conserva sus roles previstos; la verificación de correo por sí sola nunca concede `SUPER_ADMIN`.

## Configuración existente

El backend ya consume `VERIFICATION_EMAIL_API_KEY` y `VERIFICATION_EMAIL_SENDER`. No copiar secretos a GitHub ni a este documento. El webhook requiere un secreto independiente guardado como variable privada de Render una vez implementado.
