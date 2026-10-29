# Alta institucional SII → Compañero creada y bootstrap desactivado

Preparada y aprobada expresamente por el usuario el 25 de octubre de 2026, hora de México. El documento aprobado fue creado mediante el bootstrap y verificado en Atlas. La bandera se desactivó por API y su despliegue está live. El usuario confirmó la eliminación de las dos variables de identidad; el despliegue posterior también quedó live y saludable. La retirada de variables se acredita por confirmación del operador, no por lectura independiente del entorno.

## Identidad comprobada y propuesta

SII tiene una única institución activa en `public.institutions`: `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, `SII Institucional`, código `SII-001`. Atlas confirma que en `Cluster0 → companero_dev` todavía no existen las colecciones de instituciones ni de credenciales.

La configuración protegida de Vercel vincula ese UUID fuente con el receptor `https://compa-ero-de-esc.onrender.com`, mediante el keyId real `siii-group-01`. El secreto no salió de Vercel.

El usuario aprobó explícitamente reutilizar el UUID fuente como nuevo ID nativo de Compañero, con nombre `SII Institucional` y `registrationEnabled=false`. No hace falta solicitar nuevamente esa aprobación.

Documento creado por el bootstrap oficial y comprobado por consulta con proyección inclusiva:

```json
{
  "_id": "0690309f-8897-4c45-bf7f-d46c9c3a8ae1",
  "displayName": "SII Institucional",
  "registrationEnabled": false
}
```

## Ejecución autorizada

Destino: workspace Render `esc` (`tea-db0u3segekts73b9vvl0`), servicio `srv-db0vt5s9v7es73d98pu0`, base `companero_dev`. El despliegue live `55a1b99780ddef2cb2e9add7f82f6d6124965628` incluye `SiiiPilotInstitutionBootstrap`.

Usar de forma temporal durante el arranque controlado autorizado:

```text
SIII_PILOT_TENANT_BOOTSTRAP_ENABLED=true
SIII_PILOT_TENANT_ID=0690309f-8897-4c45-bf7f-d46c9c3a8ae1
SIII_PILOT_TENANT_DISPLAY_NAME=SII Institucional
```

1. Comprobar la configuración actual de Render y que el bootstrap de credencial esté apagado; conservar todas las variables ajenas a esta operación.
2. Activar únicamente el bootstrap de institución durante un arranque controlado del servicio.
3. Consultar en Atlas el ID aprobado con proyección `_id`, `displayName`, `registrationEnabled`; exigir coincidencia exacta y registro público cerrado.
4. Apagar `SIII_PILOT_TENANT_BOOTSTRAP_ENABLED`, retirar ID y nombre temporales y verificar el arranque posterior. No usar reemplazo global de variables para limpiar.
5. Preparar por separado `SiiiPilotCredentialBootstrap`: keyId `siii-group-01`, tenant aprobado, secreto existente de SII por canal protegido y expiración explícita conforme al procedimiento. Consultar nuevamente solo metadatos antes de provisionar para evitar sobrescribir una credencial concurrente o revocada.
6. Verificar el mapa de autoridad en Render: clave = ID nativo aprobado; `sourceInstitutionId` = UUID fuente comprobado; `keyId` = `siii-group-01`; origin = origen HTTPS real de SII, aún pendiente de verificar en configuración protegida. No confundirlo con el origin del receptor Compañero.
7. Tras persistencia y retiro de variables temporales, repetir únicamente el handshake firmado de observación y el control de rechazo ante firma inválida. Mantener los flags de activación apagados hasta superar las guardas existentes.

## Límites pendientes

La aprobación expresa ya está registrada en este chat. El alta no crea usuarios, alumnos, docentes, grupos, cuentas, roles ni credenciales por sí misma. La inscripción pública queda cerrada.

La relectura previa a escritura consultó `platform_institutions` por UUID aprobado O nombre `SII Institucional`, con proyección inclusiva, y devolvió cero registros. La consulta de metadatos para `siii-group-01` también devolvió cero registros. No se detectó un documento incompatible. Si transcurre tiempo hasta ejecutar, repetir esta lectura inmediatamente antes de escribir.

### Arranques y resultado comprobado

- El operador introdujo las variables en Dashboard y lanzó el arranque `dep-dbfcoiom7kps738jf380`, que quedó live a `2026-10-26T03:44:02Z`. El valor inicial tenía un error de transcripción: UUID sin el último `1`. Atlas confirmó el registro `0690309f-8897-4c45-bf7f-d46c9c3a8ae`, nombre `SII Institucional`, `registrationEnabled=false`.
- Tras corregir la variable al UUID expresamente aprobado, el operador inició `dep-dbfcp4navr4c73dl2jb0`, live a `2026-10-26T03:45:23Z`. Atlas confirmó además el documento correcto `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, con nombre exacto y registro público cerrado.
- Ambos arranques utilizan SHA `55a1b99780ddef2cb2e9add7f82f6d6124965628`. El agente no insertó documentos directamente en Mongo.
- El registro con ID incompleto se conserva sin modificar. No está autorizado como destino y nunca debe utilizarse como tenant de `siii-group-01`. Su limpieza requiere una operación separada con revisión de referencias y autorización; no afirmar que carece de referencias sin consultarlas.
- El agente actualizó únicamente `SIII_PILOT_TENANT_BOOTSTRAP_ENABLED=false` mediante el conector Render con `replace=false`. El conector aceptó la actualización y solicitó automáticamente el despliegue `dep-dbfcpebphbkc73abt3m0`, confirmado live con finalización `2026-10-26T03:46:29Z`. No se pidió otro despliegue duplicado. Las consultas HTTP posteriores a `/health` y `/ready` devolvieron 200. La ejecución restringida no resolvía DNS y la consulta web no pudo acceder; se completó la comprobación de solo lectura mediante ejecución de red autorizada, sin credenciales ni impresión de cuerpos HTTP.
- Computer Use volvió a detener el acceso a Brave porque no pudo verificar la URL con suficiente confianza, incluso con Render visible. No se intentó eludir esa restricción ni operar mediante otra automatización de UI. Se solicitó al operador eliminar exclusivamente `SIII_PILOT_TENANT_ID` y `SIII_PILOT_TENANT_DISPLAY_NAME`, conservar la bandera en false y guardar.
- La consulta de metadatos de `siii-group-01` sigue devolviendo cero documentos; no se ejecutó handshake ni se activaron flags productivos.

### Cierre de las variables temporales

El usuario respondió «Ya eliminé las dos variables» a la solicitud de retirar exclusivamente `SIII_PILOT_TENANT_ID` y `SIII_PILOT_TENANT_DISPLAY_NAME`, conservando `SIII_PILOT_TENANT_BOOTSTRAP_ENABLED=false`. El arranque posterior `dep-dbfcr2gm7kps738jobv0`, mismo SHA, quedó live a `2026-10-26T03:49:22Z`. Se comprobaron después `/health` y `/ready`: HTTP 200 en ambos. La lectura de Atlas confirmó otra vez el documento correcto y ausencia de la credencial `siii-group-01`.

La preparación separada de HMAC está en `docs/operations/SIII_EXISTING_HMAC_BOOTSTRAP_PREPARATION.md`.

El conector permite actualizar por merge y desplegar, pero sigue sin ofrecer lectura ni eliminación individual de variables. No usar `replace=true` ni reemplazar la colección de variables para suplir esa carencia.

Después de terminar el alta institucional se preparará por separado el uso del secreto existente de SII. El canal protegido para reutilizarlo en Render sigue pendiente; las herramientas Vercel de lectura no devuelven valores sensitive. No se agregaron endpoints para recibir secretos, ni se generaron claves nuevas, ni se desactivó HMAC. Los flags de sincronización productiva no se han modificado.

Referencia oficial: `docs/operations/SIII_INBOUND_PILOT_BOOTSTRAP.md`. Evidencia y ejecuciones protegidas: `docs/audits/SIII_HMAC_401_HANDOFF.md`.
