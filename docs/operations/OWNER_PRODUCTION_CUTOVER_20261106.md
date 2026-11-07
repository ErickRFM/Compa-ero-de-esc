# Compañero — corte de producción e invitación del primer propietario

**Destinatario designado:** `erik55509@hotmail.com`.
**PR de implementación:** #292. **Estado:** pendiente de CI, revisión, respaldo, migración y despliegue.

Este runbook es la secuencia autorizable para el primer propietario. No confirma que producción ya esté activada.

## Datos constatados (6–7 nov 2026)

- Render `srv-db0vt5s9v7es73d98pu0` sigue desplegando `main` en el commit `00b8f2eb1ec5bcd48ba6d7a6d99214e563a405ce`. No contiene la UI ni la política OWNER_INVITED.
- Atlas `Cluster0` en el proyecto `companero_dev` contiene las bases `companero_dev` y `companero_qa`; `companero_prod` carece de colecciones.
- Se creó `companero_prod_render_rw` con rol exclusivo `readWrite@companero_prod`, sin privilegios de administrador global.
- La cuenta Hotmail no se encontró en `companero_dev.platform_accounts` durante el diagnóstico. **No afirmar que existe en producción sin volver a comprobar el destino efectivo.**
- La invitación inicial enviada por Gmail fue informativa; no contiene código de propietario.

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

## Concesión y verificación del propietario

Tras confirmar que la cuenta Hotmail existe y está verificada, y que posee MFA:

1. Obtener `accountId` y `authRevision` actuales del servicio productivo, sin trasladar credenciales por el chat.
2. Preparar operación efímera `PROD_OWNER_BOOTSTRAP_*` ligada por digest a correo, ID, revisión, operador autorizado, razón, expiración y rol. El titular aprueba MFA `PRIVILEGED_INVITE_ACCEPT` sobre `global-owner`.
3. Verificar la identidad autorizada del operador fuera del valor simplemente declarado; la etiqueta del comando no prueba autorización. Revisión independiente obligatoria.
4. Ejecutar el comando mediante arranque controlado, comprobar la reserva global y el cambio atómico de rol; comprobar `roles == [SUPER_ADMIN]` y auditoría.
5. Borrar `PROD_OWNER_BOOTSTRAP_PROOF`, `PROD_OWNER_BOOTSTRAP_ENABLED`, `OWNER_INVITE_CODE_SHA256` y cualquier secreto efímero; volver a desplegar.
6. Reautenticar en Compañero Gestión y confirmar facultades de invitación, roles, instituciones y carreras. Probar que otra cuenta no accede.

## Condiciones que detienen el procedimiento

- Respaldo sin restauración comprobada; base sin aislar; CI o revisión fallida; cuenta sin correo verificado; MFA no inscrito; expiración; vínculo de email ausente; código sin despliegue real; solicitud de reutilizar pruebas QA; discrepancia de roles o usuario.
- Nunca actualizar `platform_accounts.roles` a mano, ni activar `PROD_OWNER_BOOTSTRAP_ENABLED` sin prueba MFA válida y contexto autorizado.
