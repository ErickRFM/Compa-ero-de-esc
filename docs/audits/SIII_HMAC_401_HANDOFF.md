# Continuar cierre SII → Compañero: HMAC 401

## Objetivo y autorización vigente

Resolver la autenticación institucional productiva mediante los bootstrap existentes y coordinar después el despliegue y la activación con SII. No crear worktrees, repetir matrices completas, crear datos académicos ficticios, omitir HMAC, reactivar credenciales revocadas ni sobrescribir credenciales existentes. El respaldo y las migraciones Supabase ya están verificados; no repetirlos.

## Destinos confirmados

- Repositorio local Compañero: `C:/proyectos/esc`.
- Repositorio local SII: `C:/proyectos/siii`.
- Render: workspace `esc`, ID `tea-db0u3segekts73b9vvl0`, servicio `srv-db0vt5s9v7es73d98pu0`, URL `https://compa-ero-de-esc.onrender.com`.
- Conector Render correspondiente: `link_6ad1a27ff0a88191992630b52edd4ddd`.
- Despliegue Render comprobado anteriormente: `dep-dbf6da67bikc73bt0m50`, SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`.
- MongoDB Atlas: `Cluster0`, base `companero_dev`, coincidente con `MONGODB_DATABASE`, confirmado por el usuario y capturas. No investigar otras bases ni volver a pedir Environment.
- Vercel SII: proyecto `prj_5OMS0EPuwS3cuQF4ICqBYBF6UNlT`, equipo `team_yJjgpau6x2SsEeKXBw09sE4a`.
- Candidato SII pendiente: `1699415748b71fad8f11a8754096e2ccc52d6b69`.

## Evidencia y límites

La captura del explorador Atlas muestra las colecciones de `companero_dev`; no aparecen `institutional_integration_credentials` ni `platform_institutions`. Esto es evidencia visual, NO una consulta ejecutada por el agente. Confirmar por lectura antes de provisionar. No conocemos todavía el ID institucional nativo, el UUID fuente ni el keyId real del destino.

El usuario instaló y conectó MongoDB Atlas. La búsqueda de plugins confirmó `installed=true`. Sin embargo, el inventario de herramientas de esta sesión sigue sin exponer herramientas MongoDB/Atlas. No se ejecutaron consultas Mongo, no se provisionaron credenciales y no se repitió el handshake. No solicitar otra instalación; utilizar el conector en una sesión que ya exponga sus herramientas.

Las variables permanentes Render están configuradas según el usuario; no pedir reconfigurarlas ni revelar valores. No se modificaron Render, Mongo, flags ni despliegues durante este diagnóstico. El estado anterior documentado mantiene productor y entrega en false; no se volvió a comprobar su valor runtime en esta sesión.

## Próximos pasos concretos

1. Usar MongoDB Atlas para listar exclusivamente las dos colecciones relevantes de `companero_dev`, contar credenciales y leer metadatos sin `secretCiphertext`. Leer únicamente `_id`, `displayName` y `registrationEnabled` de `platform_institutions`. Existe una consulta opcional en `docs/operations/SIII_HMAC_READONLY_CHECK.mongosh.js`; solo se verificó su sintaxis con `node --check`, no se ejecutó contra Mongo.
2. Comprobar el mapa entre ID nativo y `sourceInstitutionId` SII con identidad y procedencia reales. Si falta la institución, no copiar automáticamente el UUID SII: exigir ID/nombre reales validados antes del bootstrap de institución. Este crea solo metadatos con registro público cerrado.
3. Usar `docs/operations/SIII_INBOUND_PILOT_BOOTSTRAP.md`, `SiiiPilotInstitutionBootstrap.kt` y `SiiiPilotCredentialBootstrap.kt`. Ejecutar los bootstrap por separado, retirar sus variables temporales tras verificar persistencia. La credencial debe reutilizar el keyId y secreto reales gestionados para SII, con vigencia explícita. No generar reemplazos arbitrarios.
4. Precaución del código actual: `findCredential` devuelve null tanto por ausencia como por error de descifrado. El bootstrap llama después a `putCredential`, que hace replace/upsert. No interpretar null como prueba de ausencia; si existe un documento ilegible, detener el bootstrap para evitar sobrescritura. Esta debilidad fue detectada pero NO modificada.
5. Verificar descifrado con la clave de integración del entorno del receptor, sin imprimir claves, ciphertext ni HMAC. Las variables sensibles Vercel no devolvieron su valor al consultar metadatos; no intentar extraerlas a logs. Usar el flujo seguro de secretos existente.
6. Repetir únicamente el handshake de observación `POST /integration/v1/projection-status` con el firmador de SII, timestamp vigente y nonce nuevo. Exigir HTTP 200 y contrato válido. Mantener también el control de rechazo ante firma inválida. No enviar snapshots ni crear entidades ficticias.
7. Validar `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON`: mapa ID nativo → origen HTTPS SII, sourceInstitutionId y keyId. Coordinar despliegue del candidato y comprobar autoridad vigente antes de habilitar flags, entrega y cron.
8. Documentar estados, errores, reintentos e idempotencia sin atribuir validación productiva a pruebas locales. Faltan datos académicos reales autorizados para certificar sincronización visible en Android.

Referencia principal: `C:/proyectos/siii/docs/audits/SIII_PRODUCTION_ACTIVATION_2026-10-25.md`.

## Actualización verificada — 25 octubre 2026, hora de México

Esta sección sustituye la limitación anterior de acceso al conector Atlas. Las observaciones anteriores se conservan como historial.

- Se conectó mediante el complemento MongoDB Atlas al proyecto `companero_dev`, Cluster0, y se consultó exclusivamente la base `companero_dev`.
- `list_collections` devolvió 15 colecciones. No existen `platform_institutions` ni `institutional_integration_credentials`. Las consultas con proyección inclusiva y ambos conteos devolvieron cero documentos.
- Proyección institucional: `_id`, `displayName`, `registrationEnabled`. Proyección de credenciales: `_id`, `tenantId`, `scopes`, `enabled`, `version`, `expiresAtEpochSeconds`, `revokedAt`. No se consultó `secretCiphertext` ni se solicitaron documentos completos.
- No hay identificador nativo, estado enabled, vigencia ni revocación de credenciales que inspeccionar: faltan los documentos. La ausencia de credencial basta para explicar el rechazo: `InstitutionalPushAuthenticator.authenticate` retorna null cuando `findCredential` no encuentra el keyId. Esto no acredita todavía coincidencia de secretos ni descifrado.
- Lectura acotada en Supabase SII, proyecto `kkjzbqsaummyralqjqrb`: única institución `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, nombre `SII Institucional`, código `SII-001`, estado `active`. Se usaron transacciones READ ONLY; no se repitieron migraciones, respaldos ni auditorías generales.
- Vercel Production tiene `SIII_COMPANION_DELIVERY_TARGETS_JSON` de tipo sensitive; listar variables y obtenerla individualmente no devuelve su valor. No se imprimieron respuestas crudas con valores.
- Diagnóstico dentro del entorno protegido de Vercel recuperó únicamente metadatos: keyId real `siii-group-01`. Otra comprobación devolvió `SII_MATCH true true`: el destino existe para el UUID fuente anterior y su origin coincide exactamente con `https://compa-ero-de-esc.onrender.com`.
- Ejecuciones de diagnóstico sobre el SHA `1699415748b71fad8f11a8754096e2ccc52d6b69`: `dpl_4WJNECXJMJAgsrBbQ3cepbutSEa4` y `dpl_HhAZdBtBTmUXehqVi1rCCUiwKPTq`. Ambas terminaron ERROR deliberadamente mediante `process.exit(1)`, antes de ejecutar el build Next.js. Solo se sustituyó el comando por ejecución; no se modificaron variables, flags ni el comando global del proyecto. No hubo promoción ni handshake nuevo.
- `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` no aparece entre las variables del proyecto Vercel. El contrato de `ApplicationModule.kt`/`SiiiHttpAcademicAuthority.kt` lo consume en Ktor/Render: clave = tenant nativo, valor = `{origin, sourceInstitutionId, keyId}`. Su valor actual en Render sigue sin verificarse.
- Render confirma `dep-dbf6da67bikc73bt0m50` live, SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`, con ambos bootstrap disponibles. El conector permite actualizar variables, pero carece de lectura de variables y de operaciones suficientes para completar el ciclo de arranque/limpieza. El Dashboard del navegador integrado pide inicio de sesión. El intento de inspección de Brave se detuvo porque Computer Use no pudo determinar la URL con suficiente confianza; no se continuó la automatización de UI.

### Alta concreta pendiente de aprobación del usuario

El usuario pidió preparar el alta y solicitar su aprobación antes de crear la institución. Propuesta basada en la institución real de SII, permitida por el procedimiento oficial únicamente tras aprobar este mapeo:

- `_id` nativo propuesto: `0690309f-8897-4c45-bf7f-d46c9c3a8ae1` (reutilización explícita del UUID fuente, no un ID nativo preexistente descubierto).
- `displayName` propuesto: `SII Institucional`.
- `registrationEnabled`: `false`.
- Credencial posterior: keyId existente `siii-group-01`, ligada al ID nativo aprobado y al mismo secreto ya protegido en SII. No se generó un secreto sustituto ni se extrajo el existente a chat, archivos o logs.

Ejecutar `SiiiPilotInstitutionBootstrap` mediante `SIII_PILOT_TENANT_*` solo después de esa aprobación, verificar el documento en Atlas y retirar sus variables temporales antes de activar por separado `SiiiPilotCredentialBootstrap`. Nunca insertar directamente en Atlas. La transferencia del secreto existente al bootstrap de Render requiere un canal protegido entre los entornos; el conector Vercel no devuelve los valores sensitive y no se ha establecido ese canal. No solicitar pegar secretos en el chat.

Estado: causa de ausencia confirmada; alta preparada, pendiente de aprobación; credencial no provisionada; HMAC autenticado aún no acreditado; flags y entrega sin cambios. Consultar la propuesta concreta en `docs/operations/SIII_NATIVE_INSTITUTION_BOOTSTRAP_APPROVAL.md`.

## Aprobación recibida y prelectura repetida

El usuario aprobó expresamente en este chat crear `SII Institucional`, UUID nativo `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, con `registrationEnabled=false`, exclusivamente mediante el bootstrap institucional oficial en `companero_dev`. No volver a pedir esa aprobación.

Se repitió la consulta previa a escritura por UUID O nombre y devolvió cero registros; la consulta con proyección de metadatos de `siii-group-01` devolvió también cero registros. Render sigue en el mismo SHA live. No hay evidencia de registro incompatible y no se han consultado secretos cifrados.

Ahora existe herramienta Render `trigger_deploy`; siguen sin existir lectura ni eliminación selectiva de variables. Para poder comprobar y retirar las temporales sin reemplazar las variables existentes, se solicitó abrir en Brave `https://dashboard.render.com/web/srv-db0vt5s9v7es73d98pu0/env`, con sesión iniciada. No se activó el bootstrap ni se inició su despliegue antes de tener disponible la limpieza. La próxima acción depende de ese acceso operativo, no de una nueva autorización institucional. Preparar después la credencial por separado y ejecutar el handshake solo una vez provisionada; no activar flags productivos.

## Alta verificada y corrección del ID introducido en Dashboard

Las capturas y los despliegues manuales posteriores muestran que el operador ejecutó el bootstrap. La primera captura tenía `SIII_PILOT_TENANT_ID` sin el `1` final. El arranque `dep-dbfcoiom7kps738jf380` quedó live y creó ese registro incompleto. Tras corregir el valor en Dashboard, `dep-dbfcp4navr4c73dl2jb0` quedó live a `2026-10-26T03:45:23Z` y creó el ID aprobado. Ambos tienen SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`.

Atlas devolvió dos documentos mediante proyección exclusiva `_id`, `displayName`, `registrationEnabled`:

- Correcto/aprobado: `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, `SII Institucional`, `false`.
- Error de transcripción del primer arranque: `0690309f-8897-4c45-bf7f-d46c9c3a8ae`, `SII Institucional`, `false`.

El agente no creó, editó ni eliminó documentos directamente en Atlas. No utilizar el ID incompleto ni trasladar referencias. Su limpieza queda separada del bootstrap autorizado; antes de eliminarlo exigir revisión de referencias y autorización.

Se desactivó exclusivamente `SIII_PILOT_TENANT_BOOTSTRAP_ENABLED=false` por merge con `replace=false`, conservando las otras variables. El conector Render solicitó automáticamente el despliegue `dep-dbfcpebphbkc73abt3m0`; verificar que quede live antes de acreditar la desactivación efectiva. No disparar un despliegue duplicado por la misma actualización. El conector no expone eliminación individual de variables; se solicitó al operador retirar solo `SIII_PILOT_TENANT_ID` y `SIII_PILOT_TENANT_DISPLAY_NAME` y guardar, conservando la bandera en false. Computer Use detuvo la inspección de Brave por imposibilidad de verificar la URL con suficiente confianza; no se eludió la restricción.

La consulta de metadatos de `siii-group-01` sigue vacía. El secreto original continúa protegido en SII; no se generó sustituto ni se consultó ciphertext. Antes del provisionamiento separado exigir tenant exacto aprobado, mismo keyId/secreto, expiración explícita válida, bootstrap institucional apagado y verificación de ausencia de credencial concurrente. La lectura de variables sensitive de Vercel no permite extraer el secreto; falta un canal protegido para trasladarlo al entorno de bootstrap Render. No ejecutar el handshake antes de provisionar y no activar sincronización productiva.

Verificación posterior: `dep-dbfcpebphbkc73abt3m0` está live, finalizado a `2026-10-26T03:46:29Z`, con la bandera institucional en false solicitada por la actualización. `/health` y `/ready` respondieron HTTP 200 mediante consultas de solo lectura sin credenciales. La eliminación de ID/nombre temporales aún espera la intervención de Dashboard solicitada al usuario. No se considera terminada la limpieza. No se cambió ningún flag de sincronización productiva.

## Limpieza confirmada por el operador y arranque posterior

El usuario confirmó «Ya eliminé las dos variables» en respuesta a retirar solo `SIII_PILOT_TENANT_ID` y `SIII_PILOT_TENANT_DISPLAY_NAME`, conservando la bandera en false. Esto es confirmación del operador; el conector sigue sin exponer una lectura independiente de esas variables.

El despliegue posterior `dep-dbfcr2gm7kps738jobv0` quedó live a `2026-10-26T03:49:22Z`, con el mismo SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`. Las peticiones posteriores `/health` y `/ready` devolvieron HTTP 200. Se volvió a consultar el ID institucional aprobado y conserva `registrationEnabled=false`. `siii-group-01` sigue ausente.

La parte institucional queda creada, bootstrap apagado y retiro de identidad temporal confirmado por el operador. La preparación de credencial está en `docs/operations/SIII_EXISTING_HMAC_BOOTSTRAP_PREPARATION.md`. Se preguntó únicamente si el secreto original también existe en una bóveda o si su única copia está en Vercel, sin pedir su valor. Esa ubicación determina el canal de transferencia protegido; no volver a solicitar UUID, nombre o keyId, ya comprobados.

## Secreto únicamente acreditado en Vercel: alternativas documentadas

El usuario confirmó el almacenamiento sensible de Vercel y que no conoce copia adicional en una bóveda. Prohibió generar o sustituir todavía el secreto. Se verificó la documentación oficial: los valores Secret/Sensitive guardados no son legibles desde la configuración. El secreto sigue siendo utilizable en una ejecución protegida, pero transferirlo directamente al bootstrap Render requeriría un canal HTTPS autenticado y una credencial administrativa autorizada; el conector no expone su autenticación para ese uso. No se introdujeron tokens administrativos en el build, no se exportó el secreto y no se intentó recuperar por logs o artefactos.

Se amplió `docs/operations/SIII_EXISTING_HMAC_BOOTSTRAP_PREPARATION.md` con la alternativa de transferencia protegida y un procedimiento de rotación coordinada, pendiente de autorización: un único secreto nuevo custodiado y keyId nuevo, bootstrap HMAC oficial separado, validación contra destino temporal protegido de SII, handshake 200/401, cambio de configuración activa y autoridad solo después de validar, retiro de temporales y revocación anterior solo si existe. No se generó secreto, no se sustituyó `SIII_COMPANION_DELIVERY_TARGETS_JSON`, no se provisionó credencial ni se modificaron flags productivos.

## Rotación autorizada: nuevo secreto custodiado, cargas protegidas pendientes

El usuario autorizó después ejecutar la rotación coordinada completa, manteniendo la sincronización apagada. Se eligió keyId nuevo único `siii-hmac-20261025-138dedb2ba5840759868ff77844616a7`, previa lectura de credenciales (cero documentos). Se generó exactamente una clave de 32 bytes y se guardó primero en DPAPI CurrentUser con ACL exclusiva, fuera de Git, bajo `C:/Users/erik5/AppData/Local/Companero/Secrets/<keyId>/`. No regenerar. Expiración `1793591855` (`2026-11-02 03:57:35 UTC`). Se verificaron custodia/ACL y recuperación en memoria sin imprimir secreto ni ciphertext.

El asistente local `infrastructure/operations/Use-SiiiHmacRotation.ps1` prepara la copia privada de las cinco variables Render con bootstrap false y del JSON temporal SII. Historial/roaming de portapapeles se desactivan por contenido y se limpia al confirmar pegado. El usuario debe ejecutar las acciones de copia y pegar directamente en los paneles; no pedir el valor ni capturas. Las instrucciones exactas están en el procedimiento principal y ya se solicitaron mediante pregunta asíncrona.

Vercel aceptó ambos flags en false y el source no secreto `SIII_HMAC_ROTATION_PROBE_SOURCE`, sin fallos parciales. El destino activo original se conservó. El probe usará el lector y firmador reales de SII contra el destino temporal, registrará únicamente estados/contrato y abortará antes del build de aplicación. No hay deployment de rotación todavía, no se habilitó bootstrap HMAC y no se ejecutó handshake. No tratar preparación como resolución del 401.

## Asistente de custodia corregido; copia real Render esperando pegado

El usuario confirmó que el portapapeles protegido funciona y pidió no repetir pruebas WinRT. Se compararon rutas del sandbox y host: LocalApplicationData cambia, mientras la custodia real está en la ruta host ya registrada. Se verificó lectura/descifrado con Windows PowerShell 5.1 y el mismo SID, sin imprimir valores.

Se fijó el localizador público `infrastructure/operations/SiiiHmacRotationState.json` y se añadieron diagnósticos seguros por etapa, `Diagnose` y `-CheckOnly`. Tres comprobaciones focales fallaron antes y pasaron después: falta de custodia informa etapa y falla cerrada; los payloads Render y SII se construyen con la custodia existente sin clipboard ni salida de secretos. No hubo nueva generación, sustitución de secreto ni cambio de políticas de ejecución.

La acción real `CopyRenderSettings` se ejecutó después por autorización expresa del usuario. Llegó a `clipboard.copy=OK` con todas las etapas previas correctas. El proceso interactivo de ejecución `84379` espera confirmación; al responder el usuario que guardó Render con bootstrap false, enviar Enter al proceso para limpiar el portapapeles y luego iniciar la acción real `CopySiiTarget`. No capturar ni leer el portapapeles. La pregunta asíncrona de pegado ya está enviada. Vercel aún no tiene `SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON`; la última consulta de metadatos del keyId nuevo en Atlas devolvió cero documentos y Render sigue en `dep-dbfcr2gm7kps738jobv0` live. La credencial no está provisionada y el handshake sigue pendiente.

El usuario indicó que sustituyó el portapapeles antes de pegar en Render. Se cerró la espera `84379` y se repitió únicamente la copia real usando la misma custodia, sin generar otra clave. `clipboard.copy=OK` otra vez. La espera vigente es ahora el proceso `61901`; continuar desde ese proceso tras la confirmación de guardado. Todavía no se cargó Render ni se debe habilitar el bootstrap.

El usuario prefirió recibir un archivo para pegar. Se cerró `61901` y se limpió el portapapeles. La revisión automática rechazó una exportación local con fallback a texto sin cifrar; no se ejecutó. Se intentó la alternativa de EFS obligatorio antes de descifrar/escribir el secreto: falló en la preparación del archivo cifrado, sin exportar contenido. Se retiró únicamente el archivo vacío generado y se conservó la custodia DPAPI intacta.

Alternativa segura entregada: `infrastructure/operations/Copiar-Render.cmd` y `Copiar-SII.cmd`, para doble clic del operador. No contienen secretos; invocan el asistente validado, que carga la custodia existente y copia privadamente el payload en el momento del pegado. El operador debe mantener abierta la consola hasta pegar/guardar y después pulsar Enter para limpiar. No hay procesos de copia pendientes ahora. Las cargas en Render y Vercel siguen sin confirmar; no habilitar bootstrap ni lanzar handshake todavía.

El usuario pidió explícitamente `.txt` con el contenido para pegar y mostró que había copiado el texto del lanzador a Render. Se reconoció la confusión y, con esa autorización específica, la revisión automática aceptó crear dos TXT temporales en la carpeta privada de la custodia, fuera de Git y con ACL explícita exclusiva del usuario: `Para-Render.txt` (cinco variables con bootstrap false) y `Para-SII.txt` (JSON para NEXT en Production). Son copias temporales en texto claro destinadas al pegado manual; no contienen otra clave ni se generó una nueva rotación. Se comprobó su contenido en memoria y permisos sin imprimir valores. No leerlos, adjuntarlos ni mostrarlos mediante herramientas; entregar solo enlaces locales al operador. Retirarlos tras confirmar las cargas, conservando `secret.dpapi` y sus metadatos. Los TXT sustituyen a los lanzadores como instrucción vigente de carga manual. Aún faltan confirmación de cargas, bootstrap HMAC, handshake y cambio activo.

## Cargas confirmadas; bootstrap HMAC iniciado

El usuario confirmó «ya estan en ambas». Vercel expone metadatos de `SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON`, ID `RSbnGnlRQHB3BjyA`, tipo sensitive y solo Production; no se leyó su valor. La institución correcta sigue con registro público cerrado. La lectura previa del keyId nuevo devolvió cero documentos.

Se habilitó únicamente `SIII_INBOUND_BOOTSTRAP_ENABLED=true` por merge, sin reemplazar otras variables. El conector inició automáticamente `dep-dbfdhrvavr4c73dntuu0`, creado `2026-10-26T04:37:03Z`, SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`. No disparar otro despliegue duplicado. La credencial seguía ausente en la primera lectura durante el arranque; no atribuir persistencia ni éxito hasta comprobarla y confirmar deploy live.

Se verificó únicamente la forma de importación del lector TypeScript existente de SII: export named `readProjectionStatus` disponible con `node --import tsx`, sin cargar secretos ni enviar HTTP. No se repitieron matrices ni se hicieron cambios de código SII. Después del provisionamiento: apagar bootstrap, retirar sus cuatro variables temporales mediante el operador y verificar el arranque; después ejecutar el probe staged ya preparado. Mantener ambos flags false.

## Credencial nueva persistida y bootstrap HMAC apagándose

`dep-dbfdhrvavr4c73dntuu0` quedó live a `2026-10-26T04:37:59Z`. La consulta con proyección de metadatos confirmó exactamente:

- `_id`: `siii-hmac-20261025-138dedb2ba5840759868ff77844616a7`.
- `tenantId`: `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`.
- `enabled`: true; `scopes`: `[group:write]`; `expiresAtEpochSeconds`: `1793591855`.

No se leyó `secretCiphertext` ni se sobrescribió una credencial anterior. Se cambió únicamente `SIII_INBOUND_BOOTSTRAP_ENABLED=false` por merge; el conector inició `dep-dbfdil4s728c73f1a9ig` a `2026-10-26T04:38:44Z`, mismo SHA. No iniciar despliegues duplicados.

Se solicitó al operador recargar Environment, retirar exclusivamente `SIII_INBOUND_KEY_ID`, `SIII_INBOUND_TENANT_ID`, `SIII_INBOUND_SECRET_BASE64` y `SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS`, conservar bootstrap false y guardar con Save and deploy. También se preguntó solo la existencia de `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` para no sobrescribir un mapa que pueda contener otros tenants. Ambas respuestas siguen pendientes. Después de limpiar y confirmar arranque live se podrá ejecutar el probe staged; todavía no hay resultado de handshake ni cambio de destino activo.

`dep-dbfdil4s728c73f1a9ig` está confirmado live, finalizado a `2026-10-26T04:39:39Z`: el bootstrap HMAC ya está desactivado en el despliegue. Sigue pendiente acreditar la eliminación de las otras cuatro variables y el arranque posterior a esa limpieza.

## Limpieza HMAC confirmada; handshake staged ejecutándose

El usuario confirmó retirar las cuatro variables temporales y guardar; también confirmó que no existe `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` en Render. `dep-dbfdk4favr4c73do55a0` quedó live a `2026-10-26T04:42:50Z`, mismo SHA, y la credencial nueva conserva metadatos exactos después del reinicio sin bootstrap. La limpieza de variables se acredita por el operador y el arranque posterior, no por lectura directa del entorno.

Se actualizó el probe no secreto para informar también si el destino activo contiene exclusivamente la institución aprobada, a fin de no sobrescribir otros destinos al cambiar el JSON. Sintaxis verificada. Se fijó modo staged y se inició `dpl_66REZ5ur6K4M1UMJUoNiiayoCD2j` sobre el SHA SII `1699415748b71fad8f11a8754096e2ccc52d6b69`. Ejecuta únicamente las dos observaciones de firma válida/inválida y aborta deliberadamente antes de construir/publicar la aplicación. Aún no atribuir éxito al handshake hasta leer sus resultados. No cambió el destino activo ni el mapa de autoridad.

## Handshake staged aprobado: 200 y 401 reales

El probe `dpl_66REZ5ur6K4M1UMJUoNiiayoCD2j` informó: validHttpStatus 200, contractVerified true, invalidHttpStatus 401, invalidRejected true, ambos flags false, activeTargetSingleInstitutionMatchesExpected true y passed true. Terminó con salida 17 deliberada antes del build de aplicación; el estado ERROR de ese deployment es la guarda de observación, no un fallo HMAC. No se enviaron snapshots ni se crearon recursos académicos.

El origen esperado inicialmente no coincidió. Se consultaron únicamente metadatos públicos del origen configurado en otra ejecución protegida, `dpl_DRrGAUUnULAVJN11hNTNaVN4vUCv`: hostname `siii-three.vercel.app`, HTTPS/origen simple válido. La API `get_project_domain` y la lista de dominios Production verificados confirman que pertenece al proyecto `prj_5OMS0EPuwS3cuQF4ICqBYBF6UNlT`, está verified y no redirige. Se corrigió el origen esperado del probe; no se modificó `SIII_PUBLIC_ORIGIN`.

Como el operador confirmó ausencia del mapa de autoridad y el handshake pasó, se creó exclusivamente `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` en Render: clave tenant aprobado; origin `https://siii-three.vercel.app`; sourceInstitutionId aprobado; keyId nuevo. Merge sin reemplazar otras variables; inició `dep-dbfdm9navr4c73docjd0` a `2026-10-26T04:46:30Z`.

Se solicitó al operador actualizar solo el valor Secret Production de `SIII_COMPANION_DELIVERY_TARGETS_JSON` usando el mismo `Para-SII.txt`, conservando NEXT y las variables de probe por ahora. La última lectura de metadatos todavía mostraba la fecha vieja de la variable activa, por lo que sigue pendiente esa acción. No iniciar el probe active ni el despliegue final hasta confirmarla/verificarla.

Se detectó además un deployment Production READY del mismo candidato1699415, `dpl_AL1aQJQop9w9xhMYtham6ECVPUsR`, creado por una operación externa a estos probes; verificar alias y configuración efectiva antes de declarar el origen servido actualizado. La ruta de autoridad de SII autentica primero y devuelve SOURCE_AUTHORITY_DISABLED/503 mientras el productor esté apagado; no activar flags para obtener un manifest durante esta rotación.

## Destino activo validado; limpieza final Vercel solicitada

El usuario confirmó actualizar el destino activo con el mismo TXT y conservar los flags apagados. El probe `dpl_88bPegmkDGEE96436F42rQ176tLH`, modo active, devolvió validHttpStatus 200, contractVerified true, invalidHttpStatus 401, invalidRejected true, ambos flags false, siiPublicOriginMatchesExpected true, activeTargetSingleInstitutionMatchesExpected true y passed true. Salida intencional 17 antes de build/publicación. Esto acredita el nuevo keyId en la configuración activa protegida; no activar sincronización.

El mapa de autoridad de Render quedó live en `dep-dbfdm9navr4c73docjd0`, finalizado a `2026-10-26T04:47:26Z`. Se pidió al operador retirar solo NEXT, PROBE_SOURCE y PROBE_MODE en Vercel y guardar sin redeploy manual; el conector no tiene eliminación individual. Mantener destino activo y ambos flags false. Después de verificar su ausencia por metadatos, realizar el despliegue normal con guarda explícita de flags y comprobar alias/dominio servido.

La consulta del alias público mostró otro deployment `dpl_FJ92pwvUHGX3bcCEYAz2jTG9MYMz` asignado por una operación externa. No atribuir esa publicación al probe. No repetir la respuesta completa del alias: puede incluir campos sensibles de acceso; seleccionar solo projectId/domain/deploymentId/redirect para la evidencia.

## Destino activo eliminado accidentalmente: restauración protegida pendiente

El usuario indicó que quizá eliminó `SIII_COMPANION_DELIVERY_TARGETS_JSON`. La consulta de metadatos confirmó ausencia tanto del destino activo como de las tres variables temporales; ambos flags siguen presentes con los mismos IDs/fechas de su configuración false. No desplegar hasta restaurar el destino activo.

La revisión automática rechazó recrear el TXT con secreto en texto claro por falta de autorización reciente para esa nueva persistencia. El comando no se ejecutó y no se creó el archivo. Se utilizó la alternativa permitida de copia protegida desde la misma custodia DPAPI, sin nueva clave ni cambios en Mongo. Se añadió únicamente el selector de etiqueta `-ActiveSiiDestination` al helper para identificar correctamente la variable activa.

La acción real `CopySiiTarget -ActiveSiiDestination` llegó a `clipboard.copy=OK`. La sesión interactiva vigente es `98260`: al confirmar el usuario que restauró y guardó la variable activa, enviar Enter para limpiar el portapapeles. Se le indicó pegar primero el Value y luego copiar/poner el nombre en Key, Secret solo Production, sin despliegue manual. No recrear NEXT, PROBE_SOURCE ni PROBE_MODE. Después verificar presencia/ausencia por metadatos, desplegar con guarda de flags y validar el entorno servido. El 200/401 active previamente acreditado corresponde a la configuración anterior a esta eliminación accidental; falta comprobar la restauración efectiva.

El usuario informó que la copia no se pegaba y volvió a pedir explícitamente TXT después de conocer el motivo del bloqueo. Con esa autorización reciente, se creó `Para-SII.txt` en la carpeta privada de la misma custodia, con ACL explícita exclusiva del usuario y contenido verificado en memoria, sin imprimirlo. No se generó nueva clave. La sesión `98260` se cerró y el portapapeles se limpió. La instrucción vigente es restaurar solo el destino activo Secret Production usando ese TXT, guardar sin redeploy y confirmar. No hay procesos de copia pendientes. Retirar el TXT después de la restauración.

## Cierre de rotación y verificación del despliegue servido

El usuario confirmó la restauración. Metadatos: destino activo Secret Production presente, ID `nNnjvqMEmgICIXWd`; las tres temporales están ausentes. Se ejecutó el despliegue normal final `dpl_GeJXCYJdF2BmwMrUc8sitCDKQwiC`, SHA1699415, con guarda de ambos flags false y parseo JSON antes de `npm run build`. Quedó READY y el alias `siii-three.vercel.app` apunta exactamente a él, sin redirección. Render sigue live en `dep-dbfdm9navr4c73docjd0`, SHA55a1b99, con el mapa de autoridad nuevo.

La verificación final de red después de ese despliegue terminó exit0 / Passed true: Compañero firma válida200 con contrato y firma inválida401; SII autoridad firma válida503/SOURCE_AUTHORITY_DISABLED (guardia por productor apagado) y firma inválida401. No se crearon datos académicos. `/health` y `/ready` de Render200; raíz SII200. La primera ejecución local no concluyó, pero la repetición con diagnósticos por etapa pasó sin cambios de configuración ni secretos.

Se retiraron todos los TXT privados de pegado; DPAPI y metadatos de custodia permanecen. Ambos flags siguen false; bootstrap apagados y limpieza de temporales acreditada. `/health` de Render todavía informa `environment=development`: pendiente de revisión controlada antes de producción, sin modificar esa variable durante la rotación. El registro institucional de ID incompleto permanece sin tocar y requiere limpieza separada autorizada. No acreditar sincronización académica real ni activar flags.

Informe final autosuficiente: `docs/audits/SIII_HMAC_ROTATION_RESULT_2026-10-25.md`.

## Entorno production confirmado después del cambio del operador

El usuario informó el cambio a production. La primera lectura aún vio development porque el despliegue nuevo estaba en curso. Se esperó el mismo arranque sin iniciar otro: `dep-dbfe57ivcj2c73fq61n0`, mismo SHA55a1b99, quedó live a `2026-10-26T05:19:18Z`. La lectura posterior confirmó `/health`200/environment production y `/ready`200.

La comprobación focal de firmas/guardas, sin matrices completas ni cambios de secretos, volvió a pasar en ese entorno: projection válida200/contrato, inválida401; autoridad SII válida503/SOURCE_AUTHORITY_DISABLED e inválida401; exit0/Passed true. No se activaron flags, no se crearon datos académicos y la custodia existente se conservó. El bloqueo APP_ENV=development del informe inicial queda resuelto. Persisten únicamente las limitaciones de validación académica real y la revisión separada del registro institucional de ID incompleto.

## Activación productiva autorizada después de la rotación

El usuario pidió habilitar la sincronización y autorizó expresamente el traslado protegido del CRON_SECRET existente a GitHub. Se habilitaron productor y entrega por etapas, sin nueva HMAC ni cambios Render. SII final `dpl_6zvmySnwT3q95ikCg1ZfUfPVfJPk` quedó READY y confirmado en el alias público; ambos flags true. Preflight de configuración/cifrado, rol runtime y esquema pasó antes de compilar.

Red final: projection válida200/contrato e inválida401; autoridad activa válida404/SOURCE_IDENTITY_UNAVAILABLE sobre referencia inexistente e inválida401; procesador autenticado200/processed con todos los contadores0 y sin autorización401. Programador existente habilitado mediante SIII_COMPANION_AUTOMATION_ENABLED=true, con CRON_SECRET reutilizado; ejecución40275012722 success. El helper temporal de preflight quedó vacío y no se crearon datos académicos. Detalles y vigencia de la clave: `SIII_SYNC_ACTIVATION_2026-10-25.md`. El estado OFF anterior es histórico; no desactivar ni rotar de nuevo por seguir un traspaso previo.
