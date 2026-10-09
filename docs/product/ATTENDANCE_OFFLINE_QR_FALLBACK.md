# Modo contingencia — QR de clase sin Wi-Fi

## Implementado en esta rama

- El docente **abre el pase en línea** (duración 1–15 minutos) y la app descarga una secuencia de QR HMAC-SHA256 firmados por la API para la sesión vigente.
- Los QR se guardan de forma privada en Android, asociados al usuario docente y a la sesión. El teléfono elige el QR de la franja de 15 segundos actual sin necesitar conexión.
- Un estudiante autenticado escanea el QR con la cámara incluso sin Wi-Fi. Se guarda en Room con ID de operación, marca temporal y token QR, y WorkManager reenvía al recuperar conectividad.
- La API sigue validando inscripción, sesión, firma HMAC, ventana de captura y un límite de sincronización de 24 horas tras el cierre.
- Cuando la captura **no tiene evidencia Wi-Fi**, la API registra `REVIEW_REQUIRED / OFFLINE_NETWORK_QR_REVIEW`. Nunca declara presente automáticamente ni inventa jornada escolar verificada.
- El profesor consulta los registros pendientes al recuperar conexión y decide PRESENCIA / RETARDO / AUSENCIA mediante la revisión auditada existente.

## Límites reales

1. **El pase tiene que abrirse con conexión.** Si se cae la red *antes* de abrir el pase o descargar el paquete, el docente no puede emitir QR nuevos desde cero sin un emisor previamente autorizado.
2. **El QR pegado en la pared es para la entrada institucional normal**, que sigue exigiendo SSID/BSSID y la API. Aún falta un outbox independiente para escanear y auditar la entrada de pared durante la caída. No se debe prometer que un QR estático demuestre presencia offline por sí solo.
3. **No se confirma automáticamente la presencialidad offline**: hora de teléfono, SSID declarado, pantallazos o QR compartidos pueden manipularse. El QR firmado más inscripción permite solo una solicitud para revisión humana.
4. Los QR precargados solo son válidos hasta el cierre de su sesión. No se permite al docente generar extensiones offline ni usar el secreto HMAC en Android.
5. El paquete guardado es exclusivo de la cuenta activa. No es una credencial para autorizar acceso a la API. Ante revocación remota, el servidor rechazará intentos fuera de ventana.
6. La app debe conservar la sesión autenticada para atribuir el registro local; al expirar el JWT y no poder refrescar, la UI podría pedir autenticación al recuperar red.

## QA / aceptación

- Preparar paquete online, cortar Wi-Fi y datos, mostrar QR en el dispositivo docente durante varias rotaciones; verificar que no se muestra QR ya expirado.
- Estudiante escanea sin conectividad: aparece PENDIENTE y no PRESENTE; reactivar conexión y comprobar outbox / idempotencia.
- Confirmar que sin token, con QR alterado, con sesión incorrecta o sin inscripción no se concede revisión válida.
- Profesor de otra cuenta no puede solicitar el paquete; cerrar sesión invalida QR en backend.
- Probar reinicio de la app docente mientras el pase está abierto y la sesión guardada, con reloj local correcto.
- Probar Wi-Fi escolar autorizado y QR online para asegurar que su ruta VERIFIED habitual no regresa.
- Para jornada offline con QR de pared, programar módulo específico de evidencia pendiente y reconciliación administrativa; no confundir con la prueba de clase.

La construcción y los ensayos físicos requieren entorno Android/Gradle/API conectado; este cambio no implica que ya se probaron en teléfonos.
