# V19 — Pase de lista en un toque: validación física

Este guion prepara una prueba controlada de Android para el flujo de asistencia de un toque. No certifica FCM, la ubicación física ni la sincronización con SIII. Requiere cuentas de prueba autorizadas y sesiones sintéticas/no productivas.

## Preparación

- Instalar el APK Academic correspondiente al SHA candidato; usar Management solo para verificar que los roles docentes/administrativos siguen disponibles.
- Confirmar que la cuenta de alumno tiene rol STUDENT, institución habilitada e inscripción activa al grupo de la sesión.
- Confirmar conectividad al backend de pruebas. No usar tokens, estudiantes ni sesiones reales.
- Registrar modelo, versión Android, flavor, SHA Git del APK y hashes SHA-256 antes de empezar.
- Aceptar notificaciones y comprobar que la app está autorizada para mostrarlas. El registro del token del dispositivo debe responder con éxito; un error HTTP debe quedar visible en logs/diagnóstico y no marcar el dispositivo como registrado.

## Pase de lista en un toque

1. Con la app cerrada y luego en segundo plano, emitir una notificación sintética para una sesión abierta y un alumno inscrito.
2. Tocar la notificación. Debe abrir Asistencia, refrescar la sesión y preparar el intento para la sesión indicada sin obligar al alumno a escanear el QR de clase.
3. Confirmar que la pantalla distingue sesión activa, estado y resultado del intento. La confirmación de presencia debe proceder del servidor y persistir en MongoDB.
4. Repetir con sesión cerrada, alumno no inscrito, institución distinta y permiso revocado. Verificar los HTTP 401/403/404 según el contrato y que no aparezca un mensaje de éxito.
5. Simular desconexión antes y durante el envío. La pantalla debe indicar el estado pendiente/error y no atribuir presencia hasta confirmación del backend.
6. Comprobar que el QR institucional es la única acción de escaneo habitual. Las opciones de QR de clase permanecen bajo “Opciones de contingencia de clase”.

## Matriz de dispositivos y accesibilidad

Ejecutar en teléfonos físicos Android 12 / API 31 y Android 15 / API 35:

- Notificación con app en primer plano, segundo plano y proceso terminado.
- Escala de texto 1.0x, 1.3x y 2.0x; revisar etiquetas, foco y botones sin recortes.
- Tema claro, oscuro y alto contraste; verificar superficies mates fuera de Inicio.
- Movimiento reducido; revisar que la navegación y el pase no dependan de animaciones.
- Repetir el flujo con permiso de notificaciones denegado y luego restaurado desde ajustes.

## Evidencia a capturar

- Capturas de Inicio, Horario, Asistencia, Clases, Calificaciones (acceso secundario) y Perfil.
- Modelo/versión Android, hora, flavor, SHA Git, SHA-256 del APK y resultado HTTP redactado (sin tokens ni datos personales).
- Estado del intento servidor/MongoDB, registro del dispositivo y resultado de cada caso negativo.
- Anotar explícitamente si el caso fue automatizado o ejecutado físicamente.

## Criterios para declarar resultados

- “Automatizado” significa que CI o pruebas locales ejecutaron el caso; no demuestra entrega de notificación en un teléfono.
- “Prueba física completada” exige evidencia reproducible en los dos niveles Android indicados.
- No declarar FCM certificado hasta probar recepción y navegación directa en los dispositivos; no declarar presencia física por GPS/Wi‑Fi ni sincronización SIII sin prueba de autoridad, inscripción y sesión realista con datos sintéticos.
- La integración de entrega SIII debe continuar deshabilitada en producción hasta que migración/outbox, receptor fijado al SHA vigente, credenciales y preflight estén revisados y pasen un ensayo sintético aislado.