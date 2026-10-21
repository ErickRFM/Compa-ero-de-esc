# Piloto SIII → Compañero: registrar credencial de recepción en Render

**Estado inicial: DESACTIVADO.** El receptor HMAC Ktor ya existía; este cambio agrega una manera explícita de registrar una credencial piloto cifrada en Mongo durante el arranque, sin acceder manualmente a colecciones ni crear una ruta HTTP para secretos.

## Render: variables del servidor Ktor

Las cinco variables siguientes son **temporales** para aprovisionar una credencial, y solo tienen efecto si `SIII_INBOUND_BOOTSTRAP_ENABLED=true`.

| Variable | Formato / ejemplo ilustrativo |
| --- | --- |
| `SIII_INBOUND_BOOTSTRAP_ENABLED` | Empezar con `false`. Cambiar a `true` SOLO durante un despliegue de provisión |
| `SIII_INBOUND_KEY_ID` | Identificador `pilot-uptlax-1`, exactamente igual a `keyId` en `SIII_COMPANION_DELIVERY_TARGETS_JSON` de Vercel |
| `SIII_INBOUND_TENANT_ID` | **ID de institución de Compañero**, validado en Ktor; puede no ser igual al UUID de SIII. Nunca uses un ID de usuario |
| `SIII_INBOUND_SECRET_BASE64` | Base64 canónico con padding de **32–128 bytes aleatorios**; mismo contenido que `secretBase64` en el destino configurado de SIII |
| `SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS` | Instante UTC UNIX en segundos entre 1 hora y 90 días posterior al despliegue. No debe omitirse |

Variables **permanentes** ya soportadas por Ktor: `INSTITUTION_INTEGRATION_ENCRYPTION_KEY` (base64url SIN padding de 32 bytes; distinta de `MFA_ENCRYPTION_KEY`), `MONGODB_URI`, `MONGODB_DATABASE`, `JWT_SECRET`, `APP_ENV=production`. Sin cifrado o Mongo, el arranque piloto falla intencionalmente, sin registrar un secreto en texto plano.

**Nunca** metas credenciales en `NEXT_PUBLIC_*`, GitHub, .env compartido, logs o comentarios de código. Genera 32 bytes aleatorios desde tu equipo: `node -p "require('node:crypto').randomBytes(32).toString('base64')"` para HMAC; y `node -p "require('node:crypto').randomBytes(32).toString('base64url')"` para la clave AES de Mongo. Guarda los resultados solo en los entornos de secretos. No pegarlos aquí.

## Secuencia segura

1. Confirmar que SIII #68 está desplegado en `main` (no únicamente fusionado), Supabase tiene la migración 0018 aplicada **en orden**, y la identidad institucional de prueba existe en ambos sistemas.
2. Verificar Ktor/Mongo en Render y definir `SIII_INBOUND_BOOTSTRAP_ENABLED=false`; comprobar reinicio normal.
3. En Vercel, introducir destino HMAC piloto de SIII con las mismas `keyId` y clave HMAC Base64, manteniendo `SIII_ACADEMIC_OUTBOX_ENABLED=false` y `SIII_COMPANION_DELIVERY_ENABLED=false`.
4. Establecer las otras cuatro variables temporalmente en Render, poner la flag de bootstrap en `true` y desplegar. El servicio comprueba forma, tenant, secreto y expiración ANTES de aceptar peticiones y hace `putCredential` cifrada en Mongo. Si el mismo `keyId` ya existe, SOLO acepta una configuración idéntica y activa; un cambio/revocación falla cerrado. La clave no se registra en logs.
5. Tras verificar arranque exitoso, poner la flag en `false` **y eliminar los cuatro secretos temporales de Render**; desplegar nuevamente. La credencial persistida sobrevive los reinicios y continúa venciendo en la fecha elegida.
6. Enviar **un evento sintético** desde SIII; un HMAC correcto permite 202 de primera recepción, 200 en duplicado; un HMAC alterado, nonce repetido o timestamp vencido da 401. Verificar después proyección duradera en Mongo (el 202 únicamente significa recibido).
7. Solo al completar pruebas de alumno/tenant, habilitar temporalmente los flags del outbox en Vercel y luego configurar la automatización autorizada. Tener un plan para revocar con `MongoInstitutionalPushRepository.revokeCredential` sin reutilizar `keyId`.

## Límites de seguridad y NO-GO

- Nunca se provisiona una credencial si la bandera falta, no es `true`, o hay un dato inválido. No otorga roles de estudiante/docente, no crea instituciones ni vínculos de identidad.
- Si una clave anterior existe con diferente secreto, tenant, permisos, estado o fecha de expiración, el arranque **falla**, en lugar de rotarla o reactivar una revocada.
- Bootstrap no confirma que la cuenta y la institución destino sean legítimas: validar el ID tenant y permisos por servicio antes de habilitar HMAC.
- Rutas móviles SIII, sincronización de bajas, verificación del PDF físico y pruebas de asistencia siguen siendo requisitos de aceptación separados.
