# Progreso de Mática en Compañero

Compañero muestra un resumen educativo de Mática; no lo convierte en calificación institucional. El enlace siempre parte de la sesión autenticada del alumno y no usa coincidencias por correo.

## Activación de los servicios

Configura en **ambos backends** el mismo secreto aleatorio `MATICA_COMPANION_INTEGRATION_SECRET` (mínimo 32 bytes). En el backend de Compañero configura además `MATICA_API_BASE_URL` con la URL HTTPS base de la API de Mática. Ninguna de estas variables va en Android, en el repositorio ni en logs.

El esquema actual usa una clave compartida: ambos servicios deben pertenecer al mismo perímetro de confianza. Si uno se compromete, podría emitir o consultar firmas de integración; para separar operadores habrá que migrar a firma asimétrica para códigos y a una clave S2S independiente.

El código de vínculo dura 10 minutos. Compañero consume el nonce una sola vez y permite una sola cuenta Mática enlazada por cuenta de Compañero. El alumno puede quitar el enlace desde Inicio.

## Flujo

1. En Mática: Perfil → Compartir progreso con Compañero → Generar código.
2. En Compañero: Inicio → Progreso de Mática → Vincular; pegar el código.
3. Compañero conserva el par de IDs en Mongo y consulta el resumen firmado entre servidores.
4. Al abrir Inicio o actualizar, la app muestra XP total, XP semanal, nivel y rachas. Mientras carga se muestra un estado de progreso; ante un fallo permite reintentar. Si no está vinculada, el flujo muestra el campo de código temporal; si está vinculada pero falla la consulta, explica el problema sin ocultar el enlace.

Endpoints de Mática: `POST /v1/integrations/companion/link-code` (sesión Mática) y `GET /v1/integrations/companion/summary/{userId}` (firma servidor a servidor). En Compañero: `POST /integrations/matica/link`, `GET /integrations/matica/progress` y `DELETE /integrations/matica/link` (sesión nativa de estudiante).

## Alcance de los datos

El resumen procede de la gamificación guardada por el backend de Mática. Crossmath, gemas, trofeos y ciertos avances de dominio aún son locales y no se sincronizan en ese endpoint; no se presentan como si fueran datos del servidor. El panel no muestra notas escolares.

Si la integración aún no está configurada, el campo para pegar el código permanece visible, pero el intento de enlace falla de forma cerrada con servicio no disponible. El backend limita el cuerpo de la solicitud antes de decodificar JSON y rechaza códigos inválidos, expirados o reutilizados. Un build correcto no confirma configuración ni operación en producción.
