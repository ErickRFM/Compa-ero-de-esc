# Progreso de Mática en Compañero

Compañero muestra un resumen educativo de Mática; no lo convierte en calificación institucional. El enlace siempre parte de la sesión autenticada del alumno y no usa coincidencias por correo.

## Activación de los servicios

Configura en **ambos backends** el mismo secreto aleatorio `MATICA_COMPANION_INTEGRATION_SECRET` (mínimo 32 bytes). En el backend de Compañero configura además `MATICA_API_BASE_URL` con la URL HTTPS base de la API de Mática. Ninguna de estas variables va en Android, en el repositorio ni en logs.

### URLs Render confirmadas

| Entorno | API Compañero | API Mática |
|---|---|---|
| QA | `https://companero-qa-univ-v25.onrender.com` | `https://matica-api-staging.onrender.com` |
| Producción | `https://compa-ero-de-esc.onrender.com` | `https://matica-api.onrender.com` |

En Compañero, establece `MATICA_API_BASE_URL` con la URL de Mática del mismo entorno. En ambos servicios de cada pareja establece exactamente el mismo `MATICA_COMPANION_INTEGRATION_SECRET`; usa secretos distintos para QA y producción. No conectes Compañero QA a Mática producción ni compartas la clave entre entornos.

Mática staging requiere una base PostgreSQL exclusiva de QA antes de certificar el flujo; nunca apuntes staging a la base productiva.

El esquema actual usa una clave compartida: ambos servicios deben pertenecer al mismo perímetro de confianza. Si uno se compromete, podría emitir o consultar firmas de integración; para separar operadores habrá que migrar a firma asimétrica para códigos y a una clave S2S independiente.

El código de vínculo dura 10 minutos. Compañero consume el nonce una sola vez y permite una sola cuenta Mática enlazada por cuenta de Compañero. El alumno puede quitar el enlace desde Inicio.

## Flujo

1. En Mática: Perfil → Compartir progreso con Compañero → Generar código.
2. En Compañero: Inicio → Progreso de Mática → Vincular; pegar el código.
3. Compañero conserva el par de IDs en Mongo y consulta el resumen firmado entre servidores.
4. Al abrir Inicio o actualizar, la app muestra XP total, XP semanal, nivel y rachas. La tarjeta permite volver a intentar la consulta o desvincular la cuenta.

Endpoints de Mática: `POST /v1/integrations/companion/link-code` (sesión Mática) y `GET /v1/integrations/companion/summary/{userId}` (firma servidor a servidor). En Compañero: `POST /integrations/matica/link`, `GET /integrations/matica/progress` y `DELETE /integrations/matica/link` (sesión nativa de estudiante).

## Alcance de los datos

El resumen procede de la gamificación guardada por el backend de Mática. Crossmath, gemas, trofeos y ciertos avances de dominio aún son locales y no se sincronizan en ese endpoint; no se presentan como si fueran datos del servidor. El panel no muestra notas escolares.

La funcionalidad devuelve servicio no disponible hasta que ambos backends estén desplegados con estas variables. Un build correcto no confirma configuración ni operación en producción.


La tarjeta de progreso y el repositorio autenticado de Android se incluyen en el mismo cambio que los endpoints de Compañero. El panel no bloquea la agenda ni el uso independiente de Compañero cuando Mática no está configurada.
