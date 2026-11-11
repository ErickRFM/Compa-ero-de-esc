# Compañero — corte de producción e invitación del primer propietario

**Destinatario designado:** `erik55509@hotmail.com`.
**PR de implementación:** #292. El último HEAD con CI completo verde verificado fue `d5bb0a7dd5012b47c0bf7002a2a78e99f7c9097b` (API `42413496305`, Android `42413496272`, iOS `42413496284`: `success`). Los commits posteriores incorporan recuperación atómica de reservas expiradas y validación contra un trust anchor; requieren CI completo propio antes de cualquier merge. El PR sigue abierto, draft y sin fusionar. Respaldo/restauración, aislamiento y migración continúan bloqueados.

> **Aviso de vigencia (2026-11-10): este runbook conserva anotaciones históricas y no debe leerse como estado actual de Git/CI.** El PR #292 se fusionó el 2026-11-08 (`de250aa5e05e4f1cc938971e11b4156ee5d9441b`); el código de recuperación de reservas y trust anchor quedó integrado y `origin/main` (`6e77b4d472bbe89d51f1651746e7b7d941316488`) tiene CI API y Android exitosos, incluida instrumentación Android Académica/Gestión API 31/35. Esos resultados no certifican el corte de Mongo, el correo real ni el bootstrap del propietario.
>
> Para operar, usar primero [el runbook vigente de Mongo de producción](PRODUCTION_MONGODB_SINGLE_STORE.md). El resto de este archivo es un registro histórico, anterior a la decisión de conservar `companero_dev` como única base. No seguir instrucciones de migración a `companero_prod` ni referencias al servicio QA.

Este documento es un archivo histórico, no la secuencia autorizable para la operación actual. Sigue sin confirmar que el bootstrap del propietario se haya ejecutado.

## Corrección de recuperación y trust anchor (2026-11-07)

- El HEAD anterior `d5bb0a7dd5012b47c0bf7002a2a78e99f7c9097b` tiene CI completo verde: API `42413496305`, iOS `42413496284` y Android `42413496272` (`success`). Esto no se transfiere a los commits posteriores.
- Corrección de código en la rama: las reservas de primer propietario ahora persisten `state=RESERVED|COMMITTED` y `expiresAt`. Una reserva `RESERVED` vencida solo puede recuperarse mediante una nueva autorización firmada y MFA, con compare-and-set atómico sobre el digest anterior. `COMMITTED` es tombstone permanente; registros legacy sin estado se interpretan como `COMMITTED` para fallar de forma segura.
- Después del cambio CAS de identidad, el bootstrap marca la reserva `COMMITTED`. Si el proceso cae entre ambos pasos, repetir el mismo comando autorizado y la misma identidad auditada completa la marca; no vuelve a conceder roles. Se añadieron pruebas unitarias de recuperación/terminalidad y prueba Mongo con 24 recuperaciones concurrentes, un único ganador.
- La clave de firma ya no se acepta como clave pública libre del mismo comando: `parseProduction` valida contra `PROD_OWNER_BOOTSTRAP_TRUSTED_OPERATOR_PUBLIC_KEY`, que debe fijarse una sola vez como trust anchor con acceso de infraestructura restringido. La firma efímera se valida contra esa clave; la clave privada permanece fuera de GitHub y Render. No configurar esta variable junto con el comando efímero por un mismo canal sin control de cambios.
- HEAD de código con estos cambios antes de actualizar este runbook: `66e38f7fe5a412c109cdd6113922fbbdf7bca535`. CI nuevo: API `42415493879` en ejecución, Android `42415493880` pendiente, iOS `42415493837` pendiente. Mantener draft hasta un resultado completo verde en el HEAD final.
- **Atlas, lectura actual:** `Cluster0` FREE, MongoDB 8.0.37. Bases enumeradas: `companero_dev` (20 colecciones, 49 documentos), `companero_qa` (9 colecciones, 1 documento), `sample_mflix`, `admin`, `local`. `companero_prod` no aparece y no tiene colecciones. El registro existente del destinatario continúa en `companero_dev.platform_accounts` como `STUDENT`, `PENDING_VERIFICATION`, `roles=[]`, `emailVerifiedAt=null`; no existe en QA. No mutar ni borrar.
- **Atlas, accesos:** `companero_prod_render_rw` solo tiene `readWrite@companero_prod`; usuarios QA limitados a `companero_qa`; hay un usuario `atlasAdmin@admin`. Network Access sigue con `0.0.0.0/0` y una regla /32. No se ha probado el egreso de Render ni reducido la regla global.
- **Render, workspace esc:** servicio `srv-db0vt5s9v7es73d98pu0`, Free, Oregon, rama `main`, autoDeploy activado. Deploy live sigue en `ee3a5e259a3cb5569c8dc02b085c1e4c27ee11bf`. Log de `2026-11-07T20:37:07Z` confirma apertura de `companero_dev`; no se cambiaron variables ni se desplegó el PR.
- **Resend:** `companero.manecomb.com` verificado y envío habilitado; los tres correos de verificación recientes al destinatario siguen `bounced`. La consulta de supresión devuelve 404 (no hay supresión registrada), pero eso no corrige los rebotes. No hay webhooks configurados y el detalle disponible no proporciona causa SMTP. No reenviar ni enviar OWNER_INVITED hasta resolver entregabilidad y reconciliación de eventos.
- La integración conectada de Atlas no expone operaciones de backup/snapshot/restore; no existe evidencia de respaldo restaurado. La integración de Render tampoco expone el rango de egreso del servicio. Se requiere exportación externa cifrada + restore drill aislado, y obtener los rangos de salida desde el panel de Render antes de restringir Atlas. No modificar datos ni variables productivas.


## Auditoría adicional del 2026-11-07 (20:51–20:53 UTC)

- **Atlas, lectura directa:** proyecto `companero_dev`, clúster `Cluster0` FREE, AWS `US_EAST_1`, MongoDB 8.0.37. Las bases enumeradas son `companero_dev`, `companero_qa`, `sample_mflix`, `admin` y `local`; `companero_prod` no tiene colecciones. `companero_dev`: 20 colecciones, 49 documentos, 57 índices; `companero_qa`: 9 colecciones, 1 documento. La cuenta `erik55509@hotmail.com` sigue en `companero_dev.platform_accounts` con `roles=[]`, `PENDING_VERIFICATION`, intención `STUDENT`, `emailVerifiedAt=null`; no existe en QA. No actualizar ni borrar este documento.
- **Atlas, permisos/perímetro:** `companero_prod_render_rw` mantiene `readWrite` solo en `companero_prod`; los usuarios QA solo en `companero_qa`; existe `richis4571_db_user` con `atlasAdmin@admin`. La lista de acceso mantiene `0.0.0.0/0` y `201.138.192.182/32`. No restringir hasta probar el egreso de Render, pero el acceso abierto impide certificar el perímetro.
- **Render, workspace esc:** servicio `srv-db0vt5s9v7es73d98pu0`, rama `main`, autoDeploy activado, plan Free, región Oregon. El deploy live sigue en `ee3a5e259a3cb5569c8dc02b085c1e4c27ee11bf`. Logs nuevos de 20:05 y 20:20 UTC vuelven a confirmar `APP_ENV=PRODUCTION` y apertura de `companero_dev`; no cambiar variables ni disparar deploy.
- **Resend:** `companero.manecomb.com` verificado y envío habilitado; los tres correos de verificación recientes al destinatario figuran `bounced`. No aparece el destinatario en los 10 registros de supresión consultados y no hay webhooks configurados. El detalle del mensaje no expone causa SMTP del rebote; no reenviar ni quitar supresiones ajenas.
- **Revisión de código:** `MongoOwnerInvitationRepository.consume` usa digest como `_id`, fija destinatario/expiración y consume con `findOneAndUpdate` condicional a `consumedAt` ausente; la prueba Mongo usa 24 consumidores, un ganador, replay rechazado y ausencia del código en claro. `AuthService.register` requiere producción, base `*_prod`, certificación, correo exacto, digest SHA-256, código Base64URL de 43 caracteres y caducidad; la verificación de email asigna `WORKSHOP_PARTICIPANT` inicial. `OwnerBootstrap` requiere cuenta activa/verificada OWNER_INVITED, MFA `PRIVILEGED_INVITE_ACCEPT` y autorización Ed25519 ligada al comando; en producción deja únicamente `SUPER_ADMIN`.
- **Bloqueo de recuperación:** `MongoOwnerBootstrapClaims.reserve` persiste `global-owner` antes del CAS de identidad. La prueba solo permite reanudar el mismo comando mientras su prueba siga vigente (15 min); falta recuperación firmada tras expiración. Resolver antes de la primera concesión productiva.
- **CI Android:** el primer intento del job API 35 falló por un timeout de la prueba MFA con `enrollmentRequests=0`; el reintento del job fallido estaba aún ejecutándose al cierre de esta auditoría. No relajar la aserción ni declarar éxito por los demás jobs.

## Datos constatados (6–7 nov 2026)

- Auditoría directa (2026-11-07 18:05–18:06 UTC): Render `srv-db0vt5s9v7es73d98pu0` está en `main`, `autoDeploy=yes`; el deploy `dep-dbnmglg473hc739qe0d0` quedó `live` con `ee3a5e259a3cb5569c8dc02b085c1e4c27ee11bf` (ajuste de `connectTimeoutMillis`). Los logs del arranque a las 18:05:44 UTC confirman `APP_ENV=PRODUCTION`, `verification email configured=true`, pero abren MongoDB en `companero_dev`; #292 no está desplegado.
- Atlas `Cluster0` pertenece al proyecto `companero_dev`, tier `FREE`, MongoDB 8.0.37. La enumeración directa devuelve `companero_dev`, `companero_qa`, `sample_mflix`, `admin` y `local`; `companero_prod` **no existe**. El usuario `companero_prod_render_rw` está limitado a `readWrite@companero_prod`, pero ese permiso no demuestra que Render lo utilice.
- Atlas lista `companero_prod_render_rw` con `readWrite@companero_prod` y los usuarios QA con `readWrite@companero_qa`. Ninguno de esos usuarios restringidos explica la conexión de Render a `companero_dev`; el inventario también contiene un usuario `atlasAdmin`. La identidad efectiva del usuario Mongo configurado en Render sigue pendiente de verificar sin exponer URI ni contraseña.
- La lista de acceso de red de Atlas incluye `0.0.0.0/0` y `201.138.192.182/32`; la regla global permite conexiones desde cualquier IP. No retirar ni estrechar reglas hasta identificar y probar el egreso real de Render, pero esta apertura impide certificar el perímetro productivo.
- CI verificado el 2026-11-07 17:51 UTC sobre HEAD `1e588a9b`: API `42398284428`, Android `42398284419` e iOS `42398284464` finalizaron `success`. El job API confirmó estilo Kotlin, compilación, pruebas backend, ciclo de vida Mongo real y smoke test. Esos resultados no se transfieren al commit de sincronización: requiere una nueva ejecución completa.
- Consulta Atlas exacta de `companero_dev.platform_accounts` (2026-11-07, 19:30 UTC): **sí existe un registro** para `erik55509@hotmail.com`; `roles=[]`, `accountStatus=PENDING_VERIFICATION`, `registrationIntent.profile=STUDENT`, `emailVerifiedAt=null`, creado `2026-11-07T18:07:55.193Z`. La misma consulta en `companero_qa.platform_accounts` no devuelve documentos. El registro está en desarrollo, no en producción: `companero_prod` no existe. No es `OWNER_INVITED`, no está verificado y no debe promoverse ni reutilizarse.
- Riesgo de recuperación pendiente: `MongoOwnerBootstrapClaims` reserva `global-owner` antes del cambio CAS de identidad. La prueba actual solo demuestra reintento con el mismo comando antes de que venza la autorización de 15 minutos; no hay recuperación firmada para una reserva interrumpida que ya expiró. Añadir un estado/lease y recuperación explícita antes del bootstrap productivo.
- La invitación inicial enviada por Gmail fue informativa; no contiene código de propietario.
- **Conflicto de identidad para migración:** el corte documentado copia `platform_accounts` conservando todos los documentos. Si se copia el registro STUDENT pendiente anterior a `companero_prod`, `AuthService.register` rechazará la invitación OWNER_INVITED con `409 Conflict` porque ya existe ese correo. No eliminar, sobrescribir ni cambiar roles. Resolver mediante un flujo de recuperación/conciliación aprobado que pruebe control del buzón y reestablezca credenciales, o una cuarentena explícita que conserve íntegro el registro en origen y en el respaldo; añadir pruebas antes de ejecutar el corte.
- **Correo:** Resend muestra tres mensajes de verificación para el buzón propietario como `bounced` a las 18:07:56, 18:09:32 y 18:10:45 UTC del 2026-11-07. El dominio `companero.manecomb.com` está verificado y habilitado para envío, pero eso no evita estos rebotes. No hay webhooks de Resend configurados. Mongo registra `ACCEPTED` al aceptar el proveedor, pero ese estado no se reconcilia luego con `bounced`; no equivale a entrega. No reenviar invitaciones ni generar el código de propietario hasta resolver la causa del rebote y la reconciliación de eventos.

**Bloqueo de seguridad vigente:** el servicio productivo está ejecutándose con `APP_ENV=PRODUCTION` sobre `companero_dev`. No cambiar `MONGODB_DATABASE`, `MONGODB_URI`, roles ni datos hasta tener copia externa cifrada, restauración ensayada en destino aislado, inventario/hash por colección e índices y un rollback probado. El cluster es FREE y esta integración no expone operaciones de snapshot/restore; la prueba de recuperación debe realizarse con herramientas de respaldo externas y evidencia independiente. No configurar `PROD_OWNER_ISOLATION_CERTIFIED=true` mientras el runtime siga conectado a la base compartida.

## Preparación de MongoDB (no ejecutar por simple cambio de variables)

0. Resolver primero el conflicto del correo existente: el registro STUDENT no verificado de `companero_dev` bloqueará OWNER_INVITED si se copia íntegramente. No modificarlo directamente. Definir y probar un procedimiento de recuperación con verificación de buzón y restablecimiento de credenciales, o una cuarentena que conserve el registro completo en origen/respaldo sin copiarlo a producción; registrar decisión y pruebas.
1. Confirmar desde Render la base actualmente usada y credenciales del runtime, sin exponer los secretos.
2. Detener la aceptación de escrituras durante el corte o utilizar migración de doble escritura/reconciliación documentada. Conservar el servicio anterior para revertir.
3. Tomar un **respaldo externo verificable**, con cifrado y prueba de restauración; no confiar solo en una copia en el mismo cluster.
4. Copiar todos los documentos e índices de la base origen real a `companero_prod`, conservando `_id`, índices únicos, TTL, marcas de revisión, sesiones y registros institucionales, sin copiar datos de `companero_qa`.
5. Comparar por colección cantidad, índices y verificaciones de integridad; contrastar login y renovación de sesiones.
6. Confirmar que el usuario Render productivo tiene `readWrite` **solo** sobre `companero_prod`. Ejecutar `infrastructure/scripts/verify_production_mongo_isolation.py` con evidencia de Atlas de roles **sin contraseñas ni URIs**. Solamente tras este paso se podrá afirmar el aislamiento certificado.
7. Actualizar en el servicio Render `MONGODB_DATABASE=companero_prod` y `MONGODB_URI` (secreto del usuario productivo), preservando las demás variables. Comprobar `/ready`, lectura/escritura y rollback antes de habilitar invitaciones.

## Publicar la implementación

1. CI API + Android + iOS y regresiones de identidad **verdes** en el último HEAD de #292; revisión de seguridad sobre la operación de primer propietario, reserva CAS y recuperación de corte interrumpido.
2. Fusionar el PR y verificar en Render que el commit desplegado contiene `OWNER_INVITED` en el contrato.
3. No abrir las invitaciones administrativas normales hasta que ya exista la primera cuenta verificada con `SUPER_ADMIN`.

## Emitir la invitación que efectivamente permite registrar al propietario

En PowerShell en el equipo del operador autorizado, después del corte y la publicación:

```powershell
.\infrastructure\scripts\Prepare-ProdOwnerInvite.ps1 -Recipient 'erik55509@hotmail.com'
```

El script genera 32 bytes de entropía criptográfica, copia el código de 43 caracteres al portapapeles y muestra **solo** el SHA-256 y su caducidad (24 h). No registra el código ni lo sube a GitHub. Configurar en **Render → Environment**:

```dotenv
OWNER_INVITE_EMAIL=erik55509@hotmail.com
OWNER_INVITE_CODE_SHA256=<SHA-256 del script>
OWNER_INVITE_EXPIRES_AT=<epoch UTC del script>
PROD_OWNER_ISOLATION_CERTIFIED=true
```

La última variable **solo** después de certificar de forma independiente el aislamiento. Desplegar y comprobar la política antes de enviar el código. Enviar desde el correo transaccional autorizado al destinatario real, indicando **Crear cuenta → Propietario invitado**, usar ese mismo correo, pegar el código, verificar el correo de la aplicación y habilitar MFA. El proveedor puede aceptar el envío sin confirmar recepción. Actualmente no hay webhooks de Resend y se han observado rebotes del buzón propietario; no emitir la invitación hasta corregir el problema y habilitar reconciliación autenticada de `email.delivered`, `email.bounced`, `email.failed` y `email.delivery_delayed`. La aceptación HTTP del proveedor nunca debe almacenarse como entrega final.

**El código de invitación NO otorga SUPER_ADMIN por sí mismo.**

Al aceptar el registro, el backend consume el digest en `owner_registration_invitations` mediante una actualización condicional atómica. Mongo conserva únicamente el SHA-256, destinatario, caducidad, `consumedAt` y `consumedBy`; nunca el código en claro. El primer consumo gana entre todas las instancias. El registro consumido queda como tombstone: si falla la creación de la cuenta o el correo de verificación, no se reabre el mismo digest; se revoca/retira la invitación anterior y se emite un código aleatorio nuevo.

## Concesión y verificación del propietario

Tras confirmar que la cuenta Hotmail existe y está verificada, y que posee MFA:

1. Obtener `accountId` y `authRevision` actuales del servicio productivo, sin trasladar credenciales por el chat.
2. Preparar operación efímera `PROD_OWNER_BOOTSTRAP_*` ligada por digest a correo, ID, revisión, operador, razón, expiración y rol. Configurar `PROD_OWNER_BOOTSTRAP_TRUSTED_OPERATOR_PUBLIC_KEY` desde un trust anchor protegido y `PROD_OWNER_BOOTSTRAP_AUTHORIZATION` con la firma Ed25519 efímera. La clave privada permanece fuera de GitHub y Render. El titular aprueba MFA `PRIVILEGED_INVITE_ACCEPT` sobre `global-owner`.
3. Verificar la firma Ed25519 del digest canónico. La firma cubre cuenta, correo, revisión, operación, operador declarado, razón, caducidad y `SUPER_ADMIN`; cambiar cualquier campo invalida la autorización. La TOTP del titular sigue siendo una prueba separada y obligatoria.
4. Ejecutar el comando mediante arranque controlado, comprobar la reserva global y el cambio atómico de rol; comprobar `roles == [SUPER_ADMIN]` y auditoría.
5. Borrar `PROD_OWNER_BOOTSTRAP_PROOF`, `PROD_OWNER_BOOTSTRAP_ENABLED`, `OWNER_INVITE_CODE_SHA256` y cualquier secreto efímero; volver a desplegar.
6. Reautenticar en Compañero Gestión y confirmar facultades de invitación, roles, instituciones y carreras. Probar que otra cuenta no accede.

## Condiciones que detienen el procedimiento

- Respaldo sin restauración comprobada; base sin aislar; CI o revisión fallida; cuenta sin correo verificado; MFA no inscrito; expiración; vínculo de email ausente; código sin despliegue real; solicitud de reutilizar pruebas QA; discrepancia de roles o usuario.
- Nunca actualizar `platform_accounts.roles` a mano, ni activar `PROD_OWNER_BOOTSTRAP_ENABLED` sin prueba MFA válida y contexto autorizado.
