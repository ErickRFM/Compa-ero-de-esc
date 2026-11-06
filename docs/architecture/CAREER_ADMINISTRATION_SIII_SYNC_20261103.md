# Compañero ↔ SIII — autorización por carrera (inicio)

Se agrega al backend Ktor `CareerAdministrationScopePolicy` como punto central
para negar acceso de carrera sin una concesión institucional verificada.
La identidad nativa se vuelve a consultar para revocaciones, sesión y correo verificado.
`ADMIN` institucional no se confunde con un permiso para toda carrera;
el `COORDINATOR` nativo requiere una asignación específica al programa y
a la misma institución; `SUPER_ADMIN` conserva su alcance global existente.

La asignación se representa con `VerifiedCareerGrant` e incluye institución,
carrera, usuario nativo, expiración, revocación y un límite de frescura.
Una proyección caducada se rechaza. Una lista vacía cierra el acceso
(fail closed). **Todavía NO se ha conectado el repositorio de concesiones
con la sincronización firmada de SIII**, por lo que esta protección existe
como política reutilizable y suite de regresiones, no como nuevas
pantallas de administración en APK.

Para completar la funcionalidad:
1. Asociar identidad Compañero↔SIII mediante UUID institucional verificable;
   nunca autorizar únicamente por correo recibido del cliente.
2. Emitir desde SIII los grants `coordinator/program` en una outbox
   firmada/HMAC y revisionada, con eventos de revocación y
   expiración, validación de replay y auditoría.
3. Consumir en Ktor, almacenar de forma idempotente los grants verificados
   con índice por cuenta, institución y programa, y exponerlos por API de
   solo lectura para el selector Android.
4. Enlazar `CareerAdministrationScopePolicy.require` a cada endpoint de
   alumnos, docentes, horarios, grupos, red y QR; filtrar siempre en
   consulta real, nunca tras devolver documentos de otra carrera.
5. Realizar pruebas de dos instituciones, dos carreras,
   MFA-step-up, QR de asistencia temporal y datos aislados QA.
6. Registrar `UD4-Alumno` como SSID sujeto a aprobación de la institución.
   **No deducir contraseña, proximidad ni autenticación de IP/MAC/SSID**,
   y no reutilizar el QR Wi-Fi para acreditar asistencia.
7. Para Compañero la identidad propuesta de Súper Admin es `erik55509@hotmail.com` y debe ser provisionada por
   flujo protegido, con correo verificado, MFA y auditoría independiente
   en QA y producción. El bootstrap existente es deliberadamente solo QA.
