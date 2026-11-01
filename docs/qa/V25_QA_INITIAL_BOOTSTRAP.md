# QA-UNIV: alta inicial sin administrador previo

## Estado y límites

La consulta de Atlas encontró una única cuenta en `companero_dev`: `85a668db-c978-4e87-add9-f1ff243d21d9`, activa, rol `STUDENT`, sin institución ni verificación de correo registradas. No es administradora. Las cuentas y los enlaces QA nativos todavía no estaban creados. No se cambió esta cuenta.

SIII conserva sus usuarios `qa-student`, `qa-teacher`, `qa-admin`, matrícula `MAT-QA-2026`, docente `EMP-QA-2026`, sección `SEC-QA1` y materia Calidad de Software. Sus contraseñas y UUID no se comparten automáticamente con Compañero.

El servicio compartido actual usa `companero_dev`. El bootstrap de administración QA rechaza esa base y producción. Para el piloto real usar `APP_ENV=staging`, que selecciona proveedores nativos y rechaza mocks, y una base exclusiva `companero_qa` con usuario de Atlas limitado a esa base. `local` se reserva para laboratorios automatizados.

## Infraestructura preparada

`infrastructure/qa/render.yaml` define únicamente el nuevo recurso `companero-qa-univ-v25`, con `/ready` y despliegue después de CI. No administra ni reemplaza `Compa-ero-de-esc`. Revisar cuota y destino antes de crear el recurso. No se ha creado un servicio remoto al escribir este documento.

Los secretos se introducen en el almacén privado del servicio. MFA e integración necesitan claves **distintas**, cada una de 32 bytes aleatorios codificados como base64url sin relleno. El usuario Mongo debe tener `readWrite` exclusivamente sobre `companero_qa`; no copiar la credencial con acceso a la base compartida. El remitente de verificación debe pertenecer a un dominio autorizado y entregar correos reales. No poner claves de servicio Firebase en la app.

Referencia del proveedor: [Blueprints](https://render.com/docs/blueprint-spec), [variables y secretos](https://render.com/docs/configure-environment-variables). `sync: false` solicita el secreto durante la creación inicial; las altas posteriores se hacen desde Environment. No incorporar variables temporales de bootstrap al Blueprint: una sincronización futura no debe reactivarlas.

## Intervención del propietario del buzón y autenticador

1. Instalar el APK Académico construido con `COMPANERO_API_BASE_URL` del **nuevo servicio QA**. Abrir **Crear cuenta**, elegir **Participante**, usar el correo independiente del operador QA y verificarlo con el correo recibido. Este paso aún no concede administración ni afiliación académica.
2. En el servicio QA, registrar la institución con el bootstrap institucional existente: nombre `QA-UNIV`, UUID nativo independiente y validado, registro inicialmente cerrado. Activarlo durante un arranque, verificar persistencia, desactivar `SIII_PILOT_TENANT_BOOTSTRAP_ENABLED` y retirar sus dos valores temporales. No copiar por suposición el UUID de SIII.
3. En el APK: **Perfil → Seguridad de la cuenta → Configurar autenticador**. Introducir la contraseña propia, agregar la clave al autenticador y pulsar **Confirmar autenticador** con su código. No enviar el código ni la clave por chat.
4. Ejecutar localmente `infrastructure/scripts/Prepare-QaFirstAdmin.ps1` con el origen QA, su UUID institucional nativo y el correo del candidato. El script solicita contraseña y MFA de forma privada; obtiene una prueba ligada a cuenta, revisión, institución, operación, capacidades, vigencia y apertura del registro QA. No concede privilegios desde el cliente.
5. El script deja temporalmente las variables de arranque en el portapapeles. En **Render → servicio QA → Environment → Add from .env**, pegarlas y pulsar **Save and deploy**. La prueba MFA vence en cinco minutos: si expira, generar otra; no ampliar ni omitir su control.
6. Tras el arranque correcto, desactivar `QA_ADMIN_BOOTSTRAP_ENABLED` y eliminar `QA_ADMIN_BOOTSTRAP_PROOF`; guardar y desplegar. Volver a iniciar sesión en Gestión. El alta está auditada por CAS y reserva institucional permanente; no crea `SUPER_ADMIN`. Un reintento no restaura administración revocada ni reabre un registro cerrado después.

## Alumno y docente

1. En Académico QA, **Crear cuenta → Estudiante**, elegir `QA-UNIV`, indicar la matrícula oficial y un correo verificable. Elegir una contraseña independiente y verificar el correo.
2. Crear de igual modo **Docente**, con `QA-UNIV` y `EMP-QA-2026`; verificar su correo. Queda pendiente de aprobación.
3. En Gestión, abrir el directorio de usuarios. Para el alumno, pulsar **Autorizar afiliación escolar** y completar contraseña/MFA. Para el docente, usar la aprobación de personal existente. Ambas acciones revisan intención, correo, institución, rol y revisión actuales; no inventan matrícula, inscripción ni grupo.
4. Para cada cuenta aprobada, pulsar **Vincular con SIII**. Escribir `MAT-QA-2026` para el alumno o `EMP-QA-2026` para el docente y confirmar mediante MFA. La app obtiene los `nativeUserId` reales del directorio y llama a `POST /admin/accounts/institutional-identity-links` con prueba ligada al comando completo. No se necesita que el usuario proporcione UUID de cuentas que aún no existen.

## Publicación y retorno institucional

1. Configurar destinos y credenciales independientes de `group:write`, `attendance:read` y `attendance:archive`, asociados únicamente a la institución QA. El origen de autoridad SIII y el destino de Compañero deben ser los del piloto autorizado. No reemplazar mapas productivos para añadir QA.
2. En SIII, un administrador autorizado con MFA publica los patrones existentes mediante `POST /api/v1/institutions/:institutionId/companion-sync`, con `sectionId`, `expectedSourceVersion`, `reason` y `materializeOccurrences=true`. La recuperación exige además permiso académico, conserva cancelaciones, detecta conflictos y confirma ocurrencias/outbox/version/auditoría en una transacción.
3. Comprobar receipt aplicado y `/academic/load` desde ambas sesiones; deben concordar sección, ocurrencia y versión oficiales. Registro o afiliación por sí solos no acreditan un horario válido.
4. Aplicar migración SIII `0027` mediante la liberación autorizada, configurar `SIII_COMPANION_RESULTS_TARGETS_JSON` con la credencial exclusiva de archivo y activar `SIII_COMPANION_RESULTS_ENABLED`. Ejecutar el trabajador SIII real. Sus recorridos paginados y repetidos conservan correcciones antiguas y verifican el snapshot cifrado original, incluso después del cierre del periodo o retirada de inscripción.
Los cierres docentes guardan el padrón autorizado y una continuación durable en Mongo antes de generar ausencias. Un trabajador retoma cierres incompletos tras reinicios; el archivo no exporta la clase como realizada hasta finalizar. Un cierre administrativo no cuenta como clase realizada. Los trabajos docentes incluyen clases sin registros de alumnos.

5. Después de resolver una vinculación faltante o un destino caído, recuperar trabajos agotados mediante `POST /api/v1/institutions/:institutionId/companion-attendance/reconcile` con exactamente uno de `studentId` o `teacherId`, y motivo, usando la sesión autorizada de integraciones. La recuperación también se audita.
6. Configurar el QR y la red QA autorizada, y Firebase real para Académico. Comprobar entrada, apertura docente, notificación y confirmación, cierre, correcciones, Mongo, archivo PostgreSQL y pantallas de alumno/docente. Gestión conserva FCM deshabilitado conforme a su distribución.

No certificar la clase QA, FCM ni dispositivos físicos a partir de fixtures. La creación del entorno, la verificación de buzones, la aprobación MFA del propietario y el piloto físico deben constar con evidencia independiente.
