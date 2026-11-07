# Compañero — APK Académica de registro QA

El workflow **qa-registration-android** crea una APK *debug* conectada únicamente a https://companero-qa-univ-v25.onrender.com/ para registrar y verificar una identidad nativa en la base QA. No utiliza la API productiva, no lleva claves Firebase, no está firmada con certificado de distribución ni concede roles administrativos. Usa el paquete independiente `org.companerodeescuela.qa`, de modo que no reemplaza los datos de la instalación académica de producción.

## Entrega

Al ejecutarse el workflow se verifican paquete Android, firma y presencia del origen QA dentro de los archivos DEX. Publica el artifact temporal **companero-qa-registration-debug** con APK, suma SHA-256 y archivo PROVENANCE. La validez queda acreditada **solo si el workflow termina en verde**. Puede dispararse manualmente o al cambiar la app/Gradle en main. El pipeline también corre sobre un PR que modifica el workflow.

## Pasos del titular (no automatizables)

1. Abrir GitHub Actions, elegir **qa-registration-android**, abrir una ejecución verde y descargar su artifact QA. Validar el archivo SHA256SUMS. **No es una actualización de la app productiva.** Esta APK utiliza `org.companerodeescuela.qa`, distinto de `org.companerodeescuela`, y puede instalarse sin sustituir los datos de producción. Confirma en Ajustes → Aplicaciones que abres la instalación de QA. La interfaz puede mostrar el mismo nombre; valida el paquete antes de registrar cuentas.
2. En la APK de QA, elegir **Crear cuenta**, utilizar el correo exclusivo autorizado para la cuenta propietaria, completar verificación del buzón y configurar MFA desde Perfil → Seguridad. **No escribir contraseñas, OTP ni claves TOTP en GitHub o ChatGPT**.
3. Confirmar que el registro aparece en la base `companero_qa` con email verificado y ningún permiso administrativo. Registrar evidencia de la sesión QA.
4. El bootstrap de primer propietario continúa expresamente **deshabilitado** hasta certificar recuperación de reserva interrumpida y autorización del operador. Esta APK solo facilita el paso legítimo de registro.
5. Antes de cualquier correo de Súper Admin en producción deben estar certificados la separación `companero_prod`, un operador autorizado y MFA, y el proceso propio de asignación auditada. Una invitación no asigna privilegios automáticamente.

**Prohibido:** copiar cuentas de desarrollo o producción a QA, publicar APK debug como release, utilizar código falso de invitación o otorgar SUPER_ADMIN directamente en Mongo.
