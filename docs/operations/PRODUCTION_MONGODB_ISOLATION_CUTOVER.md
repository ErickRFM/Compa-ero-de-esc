# Compañero — aislamiento MongoDB y primer propietario

**Estado de la revisión: no ejecutar el corte ni enviar todavía invitación de Súper Admin.**

El 6 de noviembre de 2026 la API productiva de Render ha arrancado con `MONGODB_DATABASE=companero_dev`; QA usa `companero_qa`. En MongoDB Atlas el usuario histórico tiene rol `atlasAdmin`, y únicamente QA dispone de cuentas dedicadas `readWrite`. Por ello no hay evidencia suficiente de separación productiva.

## Preflight de metadatos

El validador es deliberadamente de solo lectura y no acepta secretos:

```sh
python infrastructure/scripts/verify_production_mongo_isolation.py \
  --production-database companero_prod \
  --qa-database companero_qa \
  --development-database companero_dev \
  --production-principal <usuario-exclusivo> \
  --atlas-users-json <roles-sin-secretos.json>
```

Estructura mínima del JSON de roles, obtenida desde la lista de usuarios de Atlas, sin contraseñas ni URI: `{"users":[{"username":"…","roles":[{"roleName":"readWrite","databaseName":"companero_prod"}]}]}`. Devuelve exit code 0 solo cuando coinciden los nombres y roles suministrados; falla con 2 en el resto. **No prueba por sí mismo permisos efectivos ni consistencia de datos.**

## Procedimiento para el corte productivo

1. Obtener autorización para ventana de mantenimiento, respaldo verificable y ensayo de restauración. No asumir que el plan gratuito de Atlas incluye snapshots recuperables.
2. Crear `companero_prod` y un principal exclusivo `readWrite` para esa base. No utilizar `atlasAdmin` ni credenciales QA.
3. Identificar los consumidores de `companero_dev`, conservar un plan de rollback, restringir escrituras durante el corte y registrar controles de integridad.
4. Migrar cuentas, sesiones, grupos, auditorías, invitaciones, outbox, índices únicos/TTL y todas las colecciones pertinentes con `_id` y referencias intactos. Utilizar `mongodump`/`mongorestore` con respaldo cifrado y controles humanos, no inserciones manuales.
5. Comprobar conteos, índices, firmas y enlaces de identidad entre origen y destino. Una copia en vivo sin suspender escrituras no acredita consistencia.
6. Ejecutar el preflight y comprobar con conexión real que el principal nuevo no puede acceder a bases QA ni desarrollo.
7. Cambiar **juntas** en Render las variables `MONGODB_DATABASE=companero_prod` y `MONGODB_URI` (usuario limitado); mantener la configuración de QA intacta. Guardar secretos en Render, nunca en Git, logs ni chat.
8. Certificar `/ready`, autenticación, MFA, revocación, sesiones, outbox y operaciones académicas; solo entonces reabrir escrituras.
9. Investigar antes de eliminar o reducir los permisos del principal antiguo, que puede ser usado también por otros consumidores.
10. Mantener invitaciones productivas desactivadas hasta que exista procedimiento de primer propietario formalmente autorizado y con MFA.

## Invitación y Súper Admin

La cuenta independiente prevista es `erik55509@hotmail.com`. Aún debe crearse mediante el registro real, verificarse por correo y registrar MFA del titular. En QA no hay cuentas nativas; el comando `Prepare-QaFirstOwner.ps1` permanece intencionalmente deshabilitado porque falta certificar recuperación ante reserva interrumpida y autoridad del operador.

Una invitación normal no asigna `SUPER_ADMIN`. La promoción productiva requiere su propio proceso auditado, no editar roles directamente en Mongo. Tras autorización, comprobar envío, estado `delivered` validado con proveedor, aceptación, login nuevo y permisos efectivos. `ACCEPTED` solo significa aceptación por el proveedor.

**Este documento y el validador no autorizan cambios de base de datos, MFA ni concesiones de rol.**
