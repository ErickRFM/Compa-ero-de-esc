# Credencial HMAC existente SII → Compañero: preparación separada

Procedimiento del 25 de octubre de 2026, hora de México. La rotación fue autorizada después de la investigación inicial. Ya se generó una sola clave nueva y se guardó en custodia DPAPI; todavía no está provisionada en Mongo. No se activó el bootstrap HMAC ni se ejecutó el handshake de rotación. La sección final registra el estado vigente y sustituye las prohibiciones históricas de generar una nueva clave.

## Datos verificados

| Dato | Valor |
| --- | --- |
| MongoDB destino | Cluster0 → `companero_dev` |
| Tenant nativo aprobado y existente | `0690309f-8897-4c45-bf7f-d46c9c3a8ae1` |
| Nombre institucional | `SII Institucional` |
| Registro público | `false` |
| UUID fuente real de SII | `0690309f-8897-4c45-bf7f-d46c9c3a8ae1` |
| keyId existente de Vercel | `siii-group-01` |
| Receptor confirmado por comparación en Vercel | `https://compa-ero-de-esc.onrender.com` |
| Credencial en Mongo | Ausente en la última consulta con proyección de metadatos |
| Bootstrap institucional | Bandera desactivada por API; identidad temporal retirada según el operador |

No utilizar el registro creado por error con ID terminado en `a8ae`: el destino aprobado termina en `a8ae1`.

## Condición pendiente: disponibilidad protegida del secreto original

`SIII_COMPANION_DELIVERY_TARGETS_JSON` existe como variable sensitive de Vercel Production. Las herramientas de listar/obtener variables no devuelven su contenido. Las ejecuciones de diagnóstico dentro de Vercel comprobaron source UUID, receptor y keyId sin sacar el secreto de ese entorno.

Se solicitó al usuario indicar solo si existe una copia original en una bóveda o si la única copia está en Vercel. No pedir que pegue el secreto en el chat ni recuperarlo por logs, codificaciones alternativas o artefactos. No copiar tokens administrativos a otro entorno ni abrir endpoints para recibir secretos sin definir y autorizar antes un canal protegido concreto.

El usuario confirmó después que el secreto está en Vercel como variable sensible y no tiene constancia de otra copia. Orden vigente: no generar ni sustituir todavía el secreto original; investigar reutilización y explicar una rotación coordinada como alternativa. No interpretar esa investigación como autorización para rotar.

Si existe una copia en una bóveda, el operador puede cargarla directamente en la variable temporal `SIII_INBOUND_SECRET_BASE64` de Render. Debe ser exactamente el `secretBase64` del destino existente de SII; no un secreto nuevo ni una clave AES. Si solo existe dentro de Vercel, la transferencia protegida entre entornos todavía debe resolverse; no cambiar el tipo de variable para volverla legible ni reemplazar su valor.

## Parámetros del bootstrap oficial

Usar exclusivamente `SiiiPilotCredentialBootstrap.kt`, siguiendo `SIII_INBOUND_PILOT_BOOTSTRAP.md`. Preparar la configuración completa antes de activar el arranque:

| Variable temporal | Valor / requisito |
| --- | --- |
| `SIII_INBOUND_BOOTSTRAP_ENABLED` | Mantener `false` hasta disponer de todos los datos; `true` solo en el arranque de provisión |
| `SIII_INBOUND_KEY_ID` | `siii-group-01` |
| `SIII_INBOUND_TENANT_ID` | `0690309f-8897-4c45-bf7f-d46c9c3a8ae1` |
| `SIII_INBOUND_SECRET_BASE64` | Secreto ORIGINAL de SII por canal protegido, sin mostrarlo |
| `SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS` | Definir explícitamente al ejecutar: UTC UNIX, más de una hora y no más de 90 días desde el arranque |

Conservar `INSTITUTION_INTEGRATION_ENCRYPTION_KEY`, las demás variables permanentes y los flags productivos existentes. No sustituir la clave de cifrado ni usar `replace=true` para modificar el entorno completo. El bootstrap fija scope `group:write` y guarda la credencial cifrada; no leer `secretCiphertext` para comprobarlo.

Antes del arranque, volver a consultar en Atlas únicamente metadatos para `_id=siii-group-01`. Si apareció una credencial concurrente, no interpretarla como ausente por fallo de descifrado ni sobrescribirla. Si está revocada, deshabilitada o es incompatible, detener la provisión.

Después del arranque, exigir deploy live, metadatos exactos del keyId/tenant/estado/expiración y salud. Apagar `SIII_INBOUND_BOOTSTRAP_ENABLED` y eliminar las otras cuatro variables temporales mediante eliminación selectiva; comprobar el arranque posterior. No mantener ambos bootstrap activos simultáneamente.

## Handshake posterior a la provisión

Desde el entorno protegido de SII, utilizar su `readProjectionStatus` y firmador existentes sobre `POST /integration/v1/projection-status`, con timestamp vigente y nonce nuevo. Solicitar observación de una referencia inexistente sin enviar eventos ni crear datos académicos. Exigir HTTP 200 y validación estricta de `siii.projection_status.v1`; conservar también el control de rechazo HTTP 401 ante firma inválida con nonce independiente. No imprimir firma, secreto, cabeceras de autenticación ni cuerpos sensibles.

No atribuir al alta institucional la resolución del 401. No habilitar `SIII_ACADEMIC_OUTBOX_ENABLED` ni `SIII_COMPANION_DELIVERY_ENABLED`. El mapa de autoridad de Ktor/Render y su origen SII vigente siguen pendientes de verificación antes de la activación productiva.

## Alternativas verificadas y decisión pendiente

La documentación oficial de Vercel establece que Secret es de escritura solamente después de guardarse y que las variables Sensitive existentes se tratan como Secret. No se pueden convertir a Config en el mismo registro para revelar su contenido. Esto concuerda con las consultas reales de listar y obtener la variable: no devolvieron valor. Los endpoints genéricos de descifrado de variables no acreditan que un Secret sea recuperable.

Esto no significa que el secreto sea inutilizable dentro de Vercel: las ejecuciones protegidas anteriores ya lo utilizaron para validar metadatos y firmar sin revelarlo. La alternativa de reutilización consiste en una ejecución revisada que lea el destino existente y envíe solo su `secretBase64` directamente, mediante HTTPS autenticado, a la variable temporal `SIII_INBOUND_SECRET_BASE64` del servicio Render autorizado. La API oficial de Render permite actualizar una variable individual mediante `PUT /v1/services/{serviceId}/env-vars/{envVarKey}`.

Ese canal requeriría una credencial de administración Render autorizada para la operación y disponible de forma protegida en el ejecutor; la autenticación del conector no está expuesta para reutilizarse desde un build. No se ha creado, extraído ni trasladado un token administrativo. No afirmar que una API key pueda limitarse a una sola variable o servicio sin verificar las capacidades de permisos reales. Una implementación futura debe fijar host/servicio/variable, impedir redirecciones, descartar cuerpos de respuesta con valores, registrar solo estados, y retirar/revocar la credencial temporal y todas las variables de bootstrap al terminar. No se ha ejecutado esa transferencia.

Con los accesos actuales no hay una recuperación legible ni un canal de transferencia protegido ya establecido. Si se decide no ampliar temporalmente ese acceso, la alternativa es la rotación coordinada descrita abajo. No usar logs, artefactos, rutas HTTP nuevas para secretos, eliminación/recreación como Config ni codificaciones alternativas para recuperar el valor.

## Rotación coordinada propuesta, todavía no autorizada ni ejecutada

1. Mantener los flags de productor y entrega apagados y confirmar su estado en la ejecución que se vaya a desplegar. Conservar el UUID institucional aprobado y `registrationEnabled=false`. Reconsultar metadatos para detectar credenciales concurrentes; no reutilizar un keyId revocado ni sobrescribir documentos existentes.
2. Tras autorización explícita, acordar un keyId nuevo y crear **una sola** clave HMAC aleatoria de 32 bytes, en Base64 canónico, mediante una herramienta local o gestor de secretos. Guardarla primero en una bóveda con acceso restringido. No producir una clave por servicio y no cambiar `INSTITUTION_INTEGRATION_ENCRYPTION_KEY`. La generación y carga deben evitar chat, argumentos de procesos, historial, repositorio y logs.
3. Cargar la misma clave desde esa custodia en ambos destinos protegidos. En Render: parámetros del bootstrap oficial, con el keyId nuevo, tenant aprobado y expiración explícita válida. En Vercel: un destino **temporal de validación**, por ejemplo bajo una variable sensible separada `SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON`, sin modificar todavía `SIII_COMPANION_DELIVERY_TARGETS_JSON`. La nueva variable es una propuesta, no existe ni se ha creado.
4. Ejecutar solo el bootstrap HMAC de Render, con el bootstrap institucional apagado. Exigir deploy live y consultar únicamente `_id`, `tenantId`, `enabled`, `scopes` y `expiresAtEpochSeconds`. Apagar el bootstrap, retirar selectivamente sus cuatro variables temporales y verificar el nuevo arranque.
5. Ejecutar desde SII el `readProjectionStatus` existente con `options.targets` apuntando al destino temporal. Esto no requiere cambiar el firmador ni habilitar la entrega. Usar nonce nuevo, timestamp vigente y una referencia de observación inexistente; exigir HTTP 200 y contrato válido. Con una firma inválida y nonce independiente exigir HTTP 401. No enviar snapshots ni datos académicos ficticios.
6. Solo después del handshake válido, actualizar el destino activo de SII al nuevo keyId y el mismo secreto custodiado; verificar que no se pierdan destinos de otras instituciones. Actualizar también el keyId del mapa de autoridad en Ktor/Render y verificar el contrato y configuración de autoridad correspondiente de SII. La clave del mapa es el tenant nativo aprobado y `sourceInstitutionId` conserva el UUID fuente real. Verificar el origen SII en su configuración protegida antes de escribirlo.
7. Desplegar con flags todavía apagados y repetir el handshake desde la configuración activa. Una variable guardada no modifica despliegues anteriores: comprobar la configuración efectiva en los nuevos despliegues. Eliminar la variable de validación temporal al concluir. Si falla cualquier guarda, mantener sincronización apagada y detener el cambio; no prometer restaurar el secreto viejo si ya se sobrescribió y no hay copia recuperable.
8. Retirar la credencial anterior solo tras comprobar que el nuevo par funciona y que no hay consumidores pendientes. `siii-group-01` estaba ausente en las consultas de Atlas; no hay una credencial Mongo que revocar mientras siga ausente. Si apareció una, revisarla y usar el mecanismo de revocación existente, sin reactivarla ni sobrescribirla. La autorización para activar sincronización productiva sigue siendo una etapa posterior.

Fuentes oficiales consultadas el 25 de octubre de 2026, hora de México:

- Vercel, Config and Secret environment variables: https://vercel.com/docs/environment-variables/sensitive-environment-variables
- Render, actualización individual de variables: https://api-docs.render.com/reference/update-env-var
- Render, variables y efectos del guardado/despliegue: https://render.com/docs/configure-environment-variables

## Rotación autorizada y custodia creada

El usuario autorizó expresamente generar un keyId único y un solo secreto, custodiarlo antes de usarlo, provisionar por bootstrap oficial, verificar HMAC y cambiar el destino y autoridad después de la validación. No habilitar sincronización productiva.

- keyId nuevo: `siii-hmac-20261025-138dedb2ba5840759868ff77844616a7`.
- La lectura previa de metadatos de `institutional_integration_credentials` devolvió cero documentos. No se sobrescribió ni reactivó una credencial.
- Se generaron exactamente 32 bytes mediante `RandomNumberGenerator`; se guardaron primero cifrados con DPAPI CurrentUser, en una carpeta con herencia ACL desactivada y acceso exclusivo al SID del usuario Windows. Se verificó el round trip en memoria y se limpiaron los arrays. No se imprimió ni escribió el secreto en texto plano.
- Custodia fuera del repositorio: `C:/Users/erik5/AppData/Local/Companero/Secrets/siii-hmac-20261025-138dedb2ba5840759868ff77844616a7/`. `secret.dpapi` contiene únicamente el material protegido y `metadata.json` contiene metadatos no secretos. No regenerar si existe la carpeta.
- Expiración explícita seleccionada: `1793591855`, equivalente a `2026-11-02 03:57:35 UTC` (siete días desde la creación). Si queda fuera de la ventana del bootstrap antes de ejecutar, revisar la expiración sin crear otra clave.
- El asistente `infrastructure/operations/Use-SiiiHmacRotation.ps1` permite consultar solo estado o copiar la configuración Render y el destino temporal SII para que el operador los pegue directamente en sus paneles. No imprime payloads ni usa argumentos con secretos. Su copia usa `ClipboardContentOptions` con historial y roaming desactivados y limpia el portapapeles al confirmar el pegado.
- Se comprobó la sintaxis PowerShell y Node y se ejecutó `Status` con Windows PowerShell sobre la custodia real: permisos y archivo protegido correctos. Las acciones de copia las ejecutará el operador; no se capturará su pantalla.
- Vercel aceptó por separado `SIII_ACADEMIC_OUTBOX_ENABLED=false` y `SIII_COMPANION_DELIVERY_ENABLED=false`, conservando sus IDs. Se añadió únicamente la variable temporal no secreta `SIII_HMAC_ROTATION_PROBE_SOURCE` con el código revisado de `infrastructure/operations/SiiiHmacRotationProbe.mjs`. La respuesta no tuvo elementos fallidos. La variable activa original de destino no se cambió.

### Cargas manuales necesarias, antes del bootstrap

En una consola propia del operador:

```powershell
powershell.exe -NoProfile -STA -File C:\proyectos\esc\infrastructure\operations\Use-SiiiHmacRotation.ps1 -Action CopyRenderSettings
```

Pegar en Render, servicio Compa-ero-de-esc → Environment → Add from .env; fusionar únicamente las cinco variables copiadas, mantener `SIII_INBOUND_BOOTSTRAP_ENABLED=false` y elegir Save only. Volver a PowerShell y pulsar Enter después de pegar/guardar para limpiar el portapapeles. No sacar capturas de los valores.

```powershell
powershell.exe -NoProfile -STA -File C:\proyectos\esc\infrastructure\operations\Use-SiiiHmacRotation.ps1 -Action CopySiiTarget
```

Crear en el proyecto Vercel `siii` la variable `SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON`, tipo Secret/sensible, solo Production. Pegar el payload como valor; conservar `SIII_COMPANION_DELIVERY_TARGETS_JSON`. Guardar y pulsar Enter en PowerShell. No enviar el portapapeles al chat.

Después de confirmar ambas cargas: verificar otra vez metadatos del keyId nuevo y tenant, habilitar solo el bootstrap HMAC por merge y observar el despliegue automático que solicita el conector Render (no duplicarlo). El probe protegido, con modo staged predeterminado, exige ambos flags exactamente false, verifica destino/keyId y usa el firmador/lector reales de SII. Registra solo resultados 200/401 y contrato; finaliza siempre antes del build de aplicación, con salida 17 si todas las guardas pasan y 1 si fallan. Ese ERROR intencional de deployment no es una publicación. No lanzar el probe mientras falte la credencial o el destino temporal.

## Diagnóstico y corrección del asistente de custodia

El usuario confirmó que su comprobación de portapapeles protegido y `Clipboard.Clear()` funcionó, y pidió no repetir esas pruebas. Se verificaron únicamente rutas, usuario, archivos, DPAPI y construcción de payloads.

- La ruta original dependía de `Environment.GetFolderPath(LocalApplicationData)`, que dentro del entorno aislado apunta a otro directorio. Se añadió `infrastructure/operations/SiiiHmacRotationState.json`, con ruta absoluta y metadatos públicos de esta misma custodia; no contiene secreto. No se movió, regeneró ni reemplazó la custodia.
- Con Windows PowerShell 5.1 bajo el usuario real, existían ambos archivos, el SID coincidía y la lectura, descifrado DPAPI y Base64 pasaron. El secreto protegido original de esta rotación sigue íntegro. La ejecución de scripts dentro del sandbox tuvo una restricción de AuthorizationManager; se utilizó la ejecución autorizada del host sin modificar políticas de ejecución.
- `Use-SiiiHmacRotation.ps1` ahora emite JSON seguro por etapa y, al fallar, únicamente etapa, código fijo y nombre del tipo de excepción. No imprime mensajes de excepción, valores de payload ni bytes cifrados/claros. `Status` verifica existencia/metadatos; `Diagnose` verifica también lectura, descifrado y ambos payloads sin tocar el portapapeles; `-CheckOnly` permite validar cada acción antes de copiar.
- Se añadieron tres comprobaciones focales en `Test-SiiiHmacRotationHelper.ps1`: custodia inexistente falla cerrada con etapa identificada, payload Render válido sin clipboard y payload SII válido sin clipboard. Se observaron los tres fallos antes del cambio y los tres PASS después. No se ejecutaron matrices completas ni pruebas WinRT.
- Después se ejecutó la acción real solicitada `CopyRenderSettings`: todas las etapas pasaron, incluidas `dpapi.decrypt`, `payload.render` y `clipboard.copy`. El proceso está esperando confirmación de pegado en Render; todavía no se activó el bootstrap. El agente limpiará el portapapeles al confirmar el operador y luego ejecutará la copia real de SII.

Comprobación segura adicional disponible para el operador, sin clipboard:

```powershell
powershell.exe -NoProfile -STA -File C:\proyectos\esc\infrastructure\operations\Use-SiiiHmacRotation.ps1 -Action Diagnose
```

No es necesario que el operador repita esa comprobación: ya pasaron los casos focales. En este punto solo se le pidió pegar en Render → Add from .env, fusionar las cinco variables y elegir Save only con bootstrap false; no ejecutar más comandos ni enviar capturas.

## Provisionamiento realizado; limpieza previa al handshake

El operador confirmó ambas cargas mediante los TXT privados solicitados. Vercel tiene el destino temporal NEXT como sensitive, solo Production. La lectura previa de credenciales e institución pasó y se habilitó únicamente el bootstrap HMAC.

El arranque `dep-dbfdhrvavr4c73dntuu0` quedó live a `2026-10-26T04:37:59Z`, SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`. Atlas confirmó el nuevo keyId con tenant correcto, enabled true, scope group:write y expiración `1793591855`, sin consultar secreto cifrado.

Se desactivó después `SIII_INBOUND_BOOTSTRAP_ENABLED=false`, iniciando automáticamente `dep-dbfdil4s728c73f1a9ig`. Está solicitada al operador la eliminación selectiva de las otras cuatro variables temporales y el guardado/despliegue correspondiente, además de comprobar si ya existe la variable del mapa de autoridad. No ejecutar el probe ni actualizar el destino activo antes de confirmar esa limpieza y arranque. Los flags de sincronización permanecen configurados en false.

## Resultado final vigente

Rotación ejecutada y HMAC verificado después del despliegue final. SII READY en `dpl_GeJXCYJdF2BmwMrUc8sitCDKQwiC`, servido por `siii-three.vercel.app`; Render live en `dep-dbfdm9navr4c73docjd0`. Firma válida200/contrato y firma inválida401 en Compañero; autoridad SII503/SOURCE_AUTHORITY_DISABLED para firma válida y401 para inválida, con ambos flags false.

Bootstrap desactivados, temporales de proveedores retiradas y TXT privados eliminados; custodia DPAPI conservada. No generar más claves ni repetir provisión. El mapa de autoridad utiliza el origen real verificado `https://siii-three.vercel.app` y el nuevo keyId. La variable activa fue restaurada tras su eliminación accidental y validada en el despliegue servido. Las secciones anteriores documentan la secuencia histórica.

El operador cambió después el entorno a production. Se confirmó `dep-dbfe57ivcj2c73fq61n0` live, `/health`200/environment production, `/ready`200 y la misma comprobación focal HMAC200/401 con guardas de autoridad conservadas, exit0. Ese bloqueo de entorno queda resuelto. Antes de acreditar sincronización académica real quedan revisar el registro institucional de ID incompleto por separado y validar con datos académicos reales autorizados. Los flags permanecen apagados. Informe completo: `docs/audits/SIII_HMAC_ROTATION_RESULT_2026-10-25.md`.
