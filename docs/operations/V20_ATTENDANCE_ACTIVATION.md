# Activación de consulta de asistencia V20

Fecha de auditoría: 2026-10-28, America/Mexico_City. Estado: **BLOQUEADO para certificación productiva completa**; el código y el despliegue no sustituyen una lectura autorizada de datos persistidos.

## Incremento nuevo

El bootstrap existente admite `SIII_INBOUND_SCOPE=attendance:read` para crear una clave separada de lectura. La ausencia de la variable conserva `group:write`. Se rechazan valores vacíos, desconocidos y combinaciones. Un keyId existente con otro propósito o secreto provoca fallo de arranque: nunca se amplía ni se revive una credencial existente. El repositorio cifra el secreto antes de persistirlo en Mongo.

`infrastructure/scripts/Prepare-AttendanceCredential.ps1` ofrece:

- `-Action Prepare`: genera una clave de 32 bytes, vigencia de 30 días, DPAPI CurrentUser y ACL exclusiva; escribe únicamente el secreto cifrado y metadatos. Se niega a sobrescribir una custodia existente. El destino predeterminado `build/attendance-private` está ignorado por Git.
- `-Action Status`: valida propietario, ACL, propósito, tenant, caducidad y descifrado sin imprimir la clave.
- `-Action ConfigureSiii`: verifica proyecto/equipo existentes y crea **sin upsert** el Secret de producción `SIII_COMPANION_ATTENDANCE_TARGETS_JSON`. Usa la sesión local Vercel; nunca exporta secretos existentes. Si la variable ya existe, se detiene en lugar de reemplazar su mapa. La respuesta debe certificar creación, target y tipo Secret sin fallos parciales.
- `-Action CopyRenderSettings`: transferencia interactiva por portapapeles al editor privado de Render; nunca pegar en chat. Espera al operador y limpia el portapapeles al terminar. No ejecutar desde un agente ni en un transcript.

Antes de activar Render, confirmar que el incremento de bootstrap está en el SHA desplegado. En el workspace **esc**, servicio **Compa-ero-de-esc**, conservar las variables existentes y aplicar las seis variables copiadas. Hacer un despliegue normal, esperar estado `live`, comprobar metadatos de la credencial en Atlas sin consultar `secretCiphertext`, y verificar que el scope sea únicamente `attendance:read`. Después desactivar `SIII_INBOUND_BOOTSTRAP_ENABLED` y retirar su secreto temporal de la configuración de despliegue mediante el editor privado; no revocar la clave persistida. Un restart no aplica cambios pendientes de variables.

La clave de lectura **no** sustituye las credenciales de autoridad SIII ni las del productor `group:write`. No cambiar los targets de escritura. Si ya existe un mapa de lectura, usar un procedimiento de modificación que preserve todas sus entradas; este helper solo realiza el alta inicial y nunca hace una fusión ciega.

## Evidencia inicial y límites

- Compañero main `4d17c38c052b19f983e2a4ea62f1f60c50e35827`: CI API [40805449801](https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/40805449801), CI Android [40805449776](https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/40805449776), ambos success. API35 Academic tuvo un timeout de interacción en V12AdministrationUiTest y pasó en el segundo intento; no se relajaron aserciones.
- SIII main `83432d11ae9ee16e5ffdb27661b7885fe2ad4ade`: [Verify SIS 40805468627](https://github.com/ErickRFM/Siii/actions/runs/40805468627) success.
- Render `dep-dbhapavavr4c739kc7q0` live sobre Compañero `4d17c38c`; `/health` y `/ready` 200 con Mongo UP. El log de arranque confirma ambiente production, binding 0.0.0.0:10000 y Mongo configurado.
- Vercel `dpl_F6HjdpodQAs35yXSYVVP6UCetk5z`, SIII `83432d11`, READY/production. El lector institucional existe; el autoservicio nuevo se entrega en un PR separado.
- Atlas `companero_dev`: al auditar había cero attendance_records, attendance_sessions, academic_group_memberships y versiones de proyección. Solo existía una credencial group:write, con vencimiento 2026-11-01 21:57:35 CDMX. No confundir esta base alojada con una base desechable por su nombre.
- Configuración Firebase Academic local validada; APK Debug generado con FIREBASE_CONFIGURED=true, paquete org.companerodeescuela, API productiva. La configuración Management no se reutiliza. Hash del artefacto inicial: `2f25c137f7d413a5a7e8b9a4faaf862ba188a1b910ffde1da80daec3c4f7645f`, 104010158 bytes; fuente Android `4d17c38c`, firma interna Debug, no distribución institucional.
- Cuenta de servicio Firebase en Render declarada configurada por el operador; entrega FCM todavía no demostrada. Solo hay emulador conectado, no Poco físico. Registrar un dispositivo exige cuenta activa y correo verificado; una cuenta pendiente no se eleva para fabricar evidencia.
- Custodia exclusiva attendance preparada y validada. El primer intento Vercel devolvió403 antes de escribir; tras renovar sesión local, ConfigureSiii devolvióOK y el conector confirmó la variable sensitive/secret, solo production, sin leer su valor. Su uso en runtime requiere un despliegue posterior. Credencial todavía pendiente de provisionar en Render/Mongo.

## Validación nueva

`SiiiPilotCredentialBootstrapTest`: RED (dos fallos por scope inexistente) → GREEN. `:services:api:check` aprobado. Tests PowerShell: DPAPI roundtrip, identidad estable, rechazo de sobrescritura y propósito alterado; transporte simulado rechaza respuestas2xx con fallos, target ajeno, visibilidad legible y creación ausente. Ninguna prueba imprime valores secretos.

Para levantar el gate falta: bootstrap exclusivo persistido, runtime de SIII actualizado, cuentas docente/alumno y vínculos institucionales autorizados, grupo/inscripción/ocurrencia vigentes, registro de dispositivo, pase docente→envío alumno→cierre docente→registro Mongo→lectura SIII autorizada, y prueba FCM. Las cuentas de laboratorio son sintéticas; no se usaron datos reales para obtener un resultado positivo.
