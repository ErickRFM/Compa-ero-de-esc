# Operación MongoDB de producción

## Arquitectura vigente

Compañero opera con una API y una base persistente. La base existente `companero_dev` es el almacén que conserva las cuentas y datos operativos; no crear ni migrar a `companero_prod` por convención de nombre. La base `companero_qa` y el servicio de QA se retiraron del flujo de la aplicación. La configuración del backend permite `companero_dev` en `APP_ENV=production` y rechaza nombres de base QA retirados.

Las aplicaciones Académica y Gestión usan la misma API productiva y el mismo sistema de cuentas. Sus paquetes Android son distintos para mantener datos de instalación y notificaciones separadas.

## Antes de desplegar cambios de API

1. Confirmar en Render que el servicio operativo conserva `APP_ENV=production` y `MONGODB_DATABASE=companero_dev`. No copiar URI, usuario ni contraseña a Git, issues, logs o chat.
2. Verificar desde Atlas que la identidad efectiva de Render tiene solo los permisos requeridos sobre `companero_dev`. La restricción de nombre en la aplicación no reemplaza el control de acceso de MongoDB.
3. Tomar un respaldo recuperable conforme al plan disponible para el clúster y registrar la evidencia antes de cambios que alteren datos o índices.
4. Desplegar el commit y revisar `/ready`, autenticación, sesiones, verificación de correo, invitaciones y registros de auditoría. Si falla, revertir al deploy anterior en Render y comprobar de nuevo `/ready` y lectura autenticada.

## Datos y cuentas

- No cambiar roles, contraseñas ni estado de cuentas para facilitar pruebas o despliegues.
- La cuenta preexistente con `PENDING_VERIFICATION` se recupera solo mediante el flujo normal de verificación/restablecimiento del titular. No promoverla directamente ni duplicarla.
- La primera cuenta `SUPER_ADMIN` requiere el proceso de bootstrap productivo autorizado, MFA, prueba firmada y auditoría. Una invitación o una dirección de correo por sí solas nunca conceden ese rol.
- Los recorridos de registro y correo se prueban con cuentas controladas y datos sintéticos en herramientas locales o suites automatizadas; no se crea otro servicio ni otra base persistente.

## Retiro del entorno QA

La base `companero_qa` fue eliminada por el operador de Atlas. El manifiesto que creaba `companero-qa-univ-v25` ya se quitó del repositorio. Render debe confirmar la eliminación del servicio remoto; eliminar un Blueprint o archivo local no elimina un servicio existente.

**Este runbook no autoriza cambios de datos, concesiones de rol ni modificación de secretos.**
