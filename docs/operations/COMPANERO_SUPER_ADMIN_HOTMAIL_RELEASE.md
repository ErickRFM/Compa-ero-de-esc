# Primera cuenta `SUPER_ADMIN` de Compañero

## Arquitectura de operación

Compañero usa una API productiva, las aplicaciones Académica y Gestión y la base existente `companero_dev`. QA ya no forma parte de los flujos desplegables. No crear otra cuenta ni modificar roles para hacer avanzar el alta.

## Secuencia autorizada

1. Confirmar que el servicio de Render responde desde el commit aprobado, usa `APP_ENV=production` y `MONGODB_DATABASE=companero_dev`.
2. Verificar el flujo real de registro, verificación y correo con el titular y su buzón controlado. Si el correo rebota, detenerse y resolver el transporte antes de emitir otra invitación.
3. El titular inicia el registro `OWNER_INVITED` con el mismo correo y la contraseña actual, e ingresa el código de invitación de un solo uso. Para una cuenta `PENDING_VERIFICATION` sin roles, el servicio cambia la intención de registro y vuelve a emitir la verificación. Para una cuenta activa, verificada y sin alcance académico, la conversión conserva el ID, el hash de contraseña, la verificación, el MFA y el rol `STUDENT`; ambas rutas revocan sesiones previas. No se admiten cuentas con institución, solicitudes de rol ni privilegios administrativos.
4. La cuenta pendiente verifica el correo desde el mensaje nuevo y recibe `WORKSHOP_PARTICIPANT`. La cuenta activa ya verificada no requiere otra verificación y conserva `STUDENT`. Ninguna ruta concede `SUPER_ADMIN` en el registro.
5. Confirmar respaldo recuperable, MFA inscrito personalmente por el titular, aprobación firmada del operador y revisión de cuenta actual.
6. Ejecutar el bootstrap de producción mediante las variables `PROD_OWNER_BOOTSTRAP_*`, más `PROD_OWNER_ISOLATION_CERTIFIED=true`. El bootstrap solo acepta una invitación de propietario verificada, requiere MFA y firma Ed25519 del operador, limita la autorización a 15 minutos y guarda auditoría/reserva atómica. Para la conversión de una cuenta activa, añade `SUPER_ADMIN` y conserva `STUDENT`; para el registro pendiente de propietario, mantiene el comportamiento existente.
7. Retirar las variables temporales del comando de bootstrap y verificar login nuevo, rol efectivo, auditoría y revocación de sesiones previas.

Una invitación por correo no concede roles. El nombre o coincidencia de una dirección tampoco concede privilegios. No enviar códigos, pruebas MFA, claves ni secretos por GitHub, logs o chat.

## Bloqueos operativos actuales

- El endpoint de webhook de Resend debe desplegarse y configurarse en el servicio productivo. El recorrido de verificación de correo debe probarse con un buzón controlado.
- El acceso efectivo de Mongo de Render debe comprobarse en Atlas y limitarse a lo necesario en `companero_dev`.
- No activar el bootstrap hasta que los controles anteriores y el respaldo se hayan verificado.
