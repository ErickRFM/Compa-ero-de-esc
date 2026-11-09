# Invitaciones: núcleo experimental

Estado actualizado tras la consolidación de #277/#283/#284: el núcleo ya está conectado a rutas desactivadas en producción, al envío de correo de verificación y a la interfaz de Android, pero **nunca concede roles**. Antes de exponer una nueva capacidad, cumplidas las garantías siguientes.

- El adaptador HTTP debe aceptar exclusivamente el código original, calcular su huella en el servidor, autenticar una cuenta nativa y comprobar correo verificado y alcance. Nunca debe devolver la huella ni aceptar como entrada el valor almacenado.
- El servicio de aceptación aún no está conectado a una ruta. Antes de exponerlo, la ruta debe resolver identidad nativa y sesión vigentes, limitar cuerpo e intentos y cubrir pruebas HTTP. La lectura de cuenta y la aceptación son operaciones separadas: hay que cerrar o manejar la carrera con suspensión, cambio de cuenta o revocación antes de utilizar una aceptación como autoridad.
- La aceptación no acredita una aprobación docente ni una concesión administrativa. La transición a `ACTIVE` debe coordinarse de forma atómica o recuperable con el otorgamiento autorizado, las revocaciones, MFA y auditoría. Una invitación revocada debe impedir un efecto pendiente.
- Faltan creación y envío con RBAC/MFA, outbox recuperable, reenvío y rotación de códigos, estados de error y caducidad, límites de intentos, API, aplicación Gestión, aceptación en la app y pruebas de integración de extremo a extremo.
- La aceptación del proveedor de correo no acredita entrega. La recepción de webhooks requiere verificar firma sobre el cuerpo original, deduplicar y manejar reintentos; no hay webhooks configurados en la cuenta consultada.
- `tokenHash` tiene un índice único; el único código original se mantiene temporalmente en memoria y no se incluye en representaciones de registro. El índice es aditivo y necesita una migración revisada antes de desplegar.

La aceptación exacta de una operación confirmada puede consultarse después de la caducidad; esto no autoriza una concesión ni revive una invitación revocada. Se normalizan las fechas a milisegundos para que el comportamiento en memoria y MongoDB sea coherente.
