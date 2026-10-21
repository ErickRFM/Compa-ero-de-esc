# Firma Android para distribución — V16

Este repositorio distingue **dos binarios diferentes** aunque ambos se generan con la variante `release` de Gradle:

- `COMPANERO_SIGNING_MODE=internal` (valor predeterminado): APK minificada y firmada con **debug** para pruebas del CI. Prohibida su publicación como versión de distribución.
- `COMPANERO_SIGNING_MODE=distribution`: APK firmada con clave privada persistente de distribución. El build **falla** si falta cualquier variable o el archivo de clave.

La firma NO se guarda en Git, Vercel ni Render. No es posible sustituir una clave perdida sin afectar actualizaciones futuras.

## Configuración inicial, una sola vez

1. Genera o recupera tu **clave de distribución definitiva** fuera del repositorio. Guarda copia offline cifrada y su alias/contraseñas. Si ya se publicó una aplicación con este package name, **recupera esa misma clave** (o sigue el proceso formal de rotación correspondiente); no crees una nueva para una actualización.
2. En GitHub > Settings > Environments, crea `android-release`, con aprobaciones y restricciones a la rama `main`.
3. Añade **environment secrets**: `COMPANERO_RELEASE_KEYSTORE_B64` (Base64 del archivo .jks), `COMPANERO_RELEASE_STORE_PASSWORD`, `COMPANERO_RELEASE_KEY_ALIAS`, `COMPANERO_RELEASE_KEY_PASSWORD`.
4. Añade **environment variables** `COMPANERO_RELEASE_CERT_SHA256` (huella pública SHA-256 del certificado, 64 hex, obtenible con `keytool -list -v` o `apksigner`) y `COMPANERO_RELEASE_API_BASE_URL` (URL HTTPS terminada en `/` del backend productivo probado).
5. En GitHub > Actions > **android-distribution-signed** > Run workflow, elige `main` y asigna `version_code` creciente y `version_name` del siguiente lanzamiento.
6. Verifica que las pruebas de CI Android/API de esa revisión estén verdes antes de distribuir. Descarga el artifact aprobado; contiene `companero-vVERSION-signed.apk` y `PROVENANCE.txt`, nunca la clave.

La tarea falla si el certificado de salida no corresponde a la huella aprobada o si equivale al certificado `debug`. `apksigner verify --verbose --print-certs` valida el APK final, no solo la configuración de Gradle.

## Instalación en el teléfono de pruebas

La APK debug existente y una APK con firma definitiva **no se actualizan una sobre otra** con el mismo `applicationId` porque sus certificados son distintos. Antes de desinstalar la versión de pruebas, exporta/respalda el horario local, los archivos importados y cualquier registro offline pendiente. Verifica con servidor qué registros fueron sincronizados. La desinstalación puede eliminar almacenamiento privado local. Solo entonces instala la distribución firmada.

Para actualizar de una distribución a la siguiente, usa siempre la **misma identidad de firma** y un `versionCode` superior. Nunca copies secretos a `gradle.properties`, CI logs o PR. La publicación en tienda sigue necesitando su checklist de privacidad, pruebas reales, versión, permisos y política de datos.

## Puertas de aceptación aún necesarias

- Login/refresh en producción con API HTTPS y cuentas legítimas.
- PDF de 9.º A: 25 bloques y conteos 5/5/4/4/6/1 contrastados tras importar en un teléfono físico.
- Asistencia: un alumno con matrícula vigente puede ver sus sesiones antes del QR; confirmar sigue exigiendo evidencia.
- SIII: credenciales de lectura, inbox firmado, proyección y reconciliación/retirada efectiva probadas de extremo a extremo.
- Instalación de release firmada y actualización de una versión de distribución previa.

**Estado actual:** pipeline preparado. Hasta provisionar y verificar una clave privada persistente y pasar aceptación física, no existe un release definitivo certificado.
