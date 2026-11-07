# Compañero — corte de producción e invitación del primer propietario

**Destinatario designado:** `erik55509@hotmail.com`.
**PR de implementación:** #292. **HEAD auditado antes de esta sincronización:** `1e588a9b7d76ccbfd632aee1b82278df59842578` (CI API/Android/iOS verde el 2026-11-07 a las 17:51 UTC). Este commit sincroniza el único avance de `main`; CI debe repetirse sobre el nuevo HEAD. El PR sigue draft y sin fusionar. Respaldo/restauración, aislamiento y migración continúan bloqueados.

Este runbook es la secuencia autorizable para el primer propietario. No confirma que producción ya esté activada.

## Datos constatados (6–7 nov 2026)

- Auditoría directa (2026-11-07 18:05–18:06 UTC): Render `srv-db0vt5s9v7es73d98pu0` está en `main`, `autoDeploy=yes`; el deploy `dep-dbnmglg473hc739qe0d0` quedó `live` con `ee3a5e259a3cb5569c8dc02b085c1e4c27ee11bf` (ajuste de `connectTimeoutMillis`). Los logs del arranque a las 18:05:44 UTC confirman `APP_ENV=PRODUCTION`, `verification email configured=true`, pero abren MongoDB en `companero_dev`; #292 no está desplegado.
- Atlas `Cluster0` pertenece al proyecto `companero_dev`, tier `FREE`, MongoDB 8.0.37. La enumeración directa devuelve `companero_dev`, `companero_qa`, `sample_mflix`, `admin` y `local`; `companero_prod` **no existe**. El usuario `companero_prod_render_rw` está limitado a `readWrite@companero_prod`, pero ese permiso no demuestra que Render lo utilice.
- Atlas lista `companero_prod_render_rw` con `readWrite@companero_prod` y los usuarios QA con `readWrite@companero_qa`. Ninguno de esos usuarios restringidos explica la conexión de Render a `companero_dev`; el inventario también contiene un usuario `atlasAdmin`. La identidad efectiva del usuario Mongo configurado en Render sigue pendiente de verificar sin exponer URI ni contraseña.
- La lista de acceso de red de Atlas incluye `0.0.0.0/0` y `201.138.192.182/32`; la regla global permite conexiones desde cualquier IP. No retirar ni estrechar reglas hasta identificar y probar el egreso real de Render, pero esta apertura impide certificar el perímetro productivo.
- CI verificado el 2026-11-07 17:51 UTC sobre HEAD `1e588a9b`: API `42398284428`, Android `42398284419` e iOS `42398284464` finalizaron `success`. El job API confirmó estilo Kotlin, compilación, pruebas backend, ciclo de vida Mongo real y smoke test. Esos resultados no se transfieren al commit de sincronización: requiere una nueva ejecución completa.
- La cuenta `erik55509@hotmail.com` no aparece en `companero_dev.platform_accounts` (consulta exacta sin resultados). No afirmar que existe en producción sin comprobar el destino efectivo.
- Riesgo de recuperación pendiente: `MongoOwnerBootstrapClaims` reserva `global-owner` antes del cambio CAS de identidad. La prueba actual solo demuestra reintento con el mismo comando antes de que venza la autorización de 15 minutos; no hay recuperación firmada para una reserva interrumpida que ya expiró. Añadir un estado/lease y recuperación explícita antes del bootstrap productivo.
- La invitación inicial enviada por Gmail fue informativa; no contiene código de propietario.

**Bloqueo de seguridad vigente:** el servicio productivo está ejecutándose con `APP_ENV=PRODUCTION` sobre `companero_dev`. No cambiar `MONGODB_DATABASE`, `MONGODB_URI`, roles ni datos hasta tener copia externa cifrada, restauración ensayada en destino aislado, inventario/hash por colección e índices y un rollback probado. El cluster es FREE y esta integración no expone operaciones de snapshot/restore; la prueba de recuperación debe realizarse con herramientas de respaldo externas y evidencia independiente. No configurar `PROD_OWNER_ISOLATION_CERTIFIED=true` mientras el runtime siga conectado a la base compartida.

## Preparación de MongoDB (no ejecutar por simple cambio de variables)

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

La última variable **solo** después de certificar de forma independiente el aislamiento. Desplegar y comprobar la política antes de enviar el código. Enviar desde el correo transaccional autorizado al destinatario real, indicando **Crear cuenta → Propietario invitado**, usar ese mismo correo, pegar el código, verificar el correo de la aplicación y habilitar MFA. El proveedor puede aceptar el envío sin confirmar recepción; revisar id y webhook de entrega.

**El código de invitación NO otorga SUPER_ADMIN por sí mismo.**

Al aceptar el registro, el backend consume el digest en `owner_registration_invitations` mediante una actualización condicional atómica. Mongo conserva únicamente el SHA-256, destinatario, caducidad, `consumedAt` y `consumedBy`; nunca el código en claro. El primer consumo gana entre todas las instancias. El registro consumido queda como tombstone: si falla la creación de la cuenta o el correo de verificación, no se reabre el mismo digest; se revoca/retira la invitación anterior y se emite un código aleatorio nuevo.

## Concesión y verificación del propietario

Tras confirmar que la cuenta Hotmail existe y está verificada, y que posee MFA:

1. Obtener `accountId` y `authRevision` actuales del servicio productivo, sin trasladar credenciales por el chat.
2. Preparar operación efímera `PROD_OWNER_BOOTSTRAP_*` ligada por digest a correo, ID, revisión, operador, razón, expiración y rol. Configurar `PROD_OWNER_BOOTSTRAP_OPERATOR_PUBLIC_KEY` desde un trust anchor protegido y `PROD_OWNER_BOOTSTRAP_AUTHORIZATION` con la firma Ed25519 efímera. La clave privada permanece fuera de GitHub y Render. El titular aprueba MFA `PRIVILEGED_INVITE_ACCEPT` sobre `global-owner`.
3. Verificar la firma Ed25519 del digest canónico. La firma cubre cuenta, correo, revisión, operación, operador declarado, razón, caducidad y `SUPER_ADMIN`; cambiar cualquier campo invalida la autorización. La TOTP del titular sigue siendo una prueba separada y obligatoria.
4. Ejecutar el comando mediante arranque controlado, comprobar la reserva global y el cambio atómico de rol; comprobar `roles == [SUPER_ADMIN]` y auditoría.
5. Borrar `PROD_OWNER_BOOTSTRAP_PROOF`, `PROD_OWNER_BOOTSTRAP_ENABLED`, `OWNER_INVITE_CODE_SHA256` y cualquier secreto efímero; volver a desplegar.
6. Reautenticar en Compañero Gestión y confirmar facultades de invitación, roles, instituciones y carreras. Probar que otra cuenta no accede.

## Condiciones que detienen el procedimiento

- Respaldo sin restauración comprobada; base sin aislar; CI o revisión fallida; cuenta sin correo verificado; MFA no inscrito; expiración; vínculo de email ausente; código sin despliegue real; solicitud de reutilizar pruebas QA; discrepancia de roles o usuario.
- Nunca actualizar `platform_accounts.roles` a mano, ni activar `PROD_OWNER_BOOTSTRAP_ENABLED` sin prueba MFA válida y contexto autorizado.
