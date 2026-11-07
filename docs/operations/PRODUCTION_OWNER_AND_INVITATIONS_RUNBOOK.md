# Compañero · propietario de plataforma, registro e invitaciones

## Designación global

La identidad designada es **erik55509@hotmail.com**. La dirección es una política de
provisión, **no** una contraseña ni una condición suficiente para elevar privilegios.
El propietario global es una cuenta **nativa** separada de SIII, con correo confirmado,
MFA configurado, sesión activa y una concesión auditable de `SUPER_ADMIN`.

La cuenta no aparece mágicamente por crear este documento ni por fusionar un PR.
No alterar manualmente MongoDB para modificar roles. No enviar contraseña, OTP,
código de recuperación, token JWT ni prueba MFA a ChatGPT, GitHub o incidencias.

## Producción: alta inicial por operación privada

1. Confirmar `APP_ENV=production`, una base MongoDB productiva separada de QA y
   desarrollo y un despliegue verificable. Las bases terminadas en `_qa`,
   `_dev` o `_test` están expresamente bloqueadas para esta operación.
2. Crear la cuenta nueva con **erik55509@hotmail.com** desde registro de la app.
   Usar perfil Participante si todavía no hay institución habilitada; no crear
   cuentas propietarias con privilegios por correo antes de verificar la bandeja.
3. Verificar correo, iniciar sesión, configurar autenticador TOTP desde
   **Perfil → Seguridad de la cuenta** y cerrar cualquier sesión anterior
   una vez concluida la operación.
4. Desde un equipo propio con PowerShell, ejecutar:
   ```powershell
   .\infrastructure\scripts\Prepare-ProductionFirstOwner.ps1 -DeclaredOperator "operador-render" -Reason "Alta inicial propietaria autorizada"
   ```
   Introducir contraseña y OTP **solamente en el equipo propio**. El script fija
   el origen de Render, consulta identidad real, obtiene el `authRevision` y
   calcula el digest ligado a cuenta, revisión, operación, motivo, fecha y rol.
5. El script copia temporalmente `PROD_OWNER_BOOTSTRAP_*` al portapapeles.
   Pegar únicamente en **Render → Compa-ero-de-esc → Environment → Add from .env**,
   guardar y desplegar el commit certificado; la prueba de MFA es corta.
6. El arranque reserva de modo único el propietario en
   `prod_initial_owner_claims` y usa actualización CAS de revisión de cuenta.
   El mecanismo rechaza otro superadministrador existente o un candidato
   sin correo confirmado, sin MFA válido o con email distinto.
7. Comprobar rol `SUPER_ADMIN` con una **nueva sesión** del Hotmail y comprobar
   revocación de la sesión anterior. Desactivar
   `PROD_OWNER_BOOTSTRAP_ENABLED`, borrar `PROD_OWNER_BOOTSTRAP_PROOF`
   y desplegar de nuevo. Conservar auditoría y registro de operación sin secretos.
8. No repetir la preparación si ya hay un súper administrador: corresponde
   a un procedimiento de recuperación separado, no a un segundo bootstrap.

El propietario Hotmail con SUPER_ADMIN está protegido de cambios de estado
por la administración institucional normal. Las universidades siguen siendo
tenants separados: invitar y administrar cuentas no permite leer automáticamente
notas privadas de tutorías.

## Invitaciones de universidades

`PRODUCTION_INVITATIONS_ENABLED` es **false** por defecto. Para habilitar
las rutas en producción deben estar configurados MongoDB, JWT, cifrado MFA y
correo transaccional con Resend; el nuevo outbox guarda el token de reintento
**cifrado** y se procesa por un trabajador acotado.

Flujo de gestión: cuenta propietaria verificada → preparar operación →
obtener prueba MFA ligada al comando → crear invitación → Resend acepta el
envío → invitado se registra con el mismo email → verifica email → introduce
ID/código → confirma aceptación. La aceptación no otorga SUPER_ADMIN ni
cambia automáticamente roles académicos. El administrador sigue otorgando
permisos por institución/carrera con su propio flujo autorizado.

Antes de activar el flag en producción hay que certificar pruebas API/Android,
reintentos, concurrencia, aislamiento de tenants, datos de correo y recepción
con proveedor. `ACCEPTED` significa **aceptado por Resend**, no entregado.
El primer correo real y su estado de entrega necesitan evidencia del proveedor.

## Huella / passkeys

La biometría debe usar Android BiometricPrompt o Credential Manager/passkeys.
La huella se verifica dentro del dispositivo y **no** se registra ni transfiere
a Ktor/MongoDB. La creación de una passkey requiere challenge de servidor,
clave pública persistida, attestation cuando corresponda, validación de
firma/origen, counters y revocación. La implementación de ese flujo Android
y backend queda **pendiente**: TOTP + contraseña son los factores actuales.
No anunciar acceso por huella antes de pruebas en un dispositivo real.

## Comprobaciones finales obligatorias

- Registro Hotmail real, correo verificado y MFA, sin asumir que ya sucedieron.
- QA y producción con bases independientes; no mezclar cuentas QA/producción.
- Una sola reserva global, sin segundo SUPER_ADMIN automático.
- Dos instituciones sin fuga de permisos; invitaciones con expiración,
  revocación, token de un uso y audiencia correcta.
- CI API/Android verde, APK QA/producción apuntando al host correcto.
- Emisión observada en Resend y aceptación real desde la app.
