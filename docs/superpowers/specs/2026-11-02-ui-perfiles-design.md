# UI consistente y perfiles claros

Base: `9ab4655e15dde98291c44c1cf8b57920c290b9f4`. Solicitud: auditar y mejorar la UI y comprobar que los perfiles funcionen.

## Dirección

Conservar Compose y la identidad borgoña, superficies V8, preferencias de tema, contraste y movimiento. El usuario confirmó «Conservar borgoña y unificar». La ejecución se limita al pulido y las correcciones solicitadas.

Alternativas: unificar las primitivas y corregir fricción conserva la navegación conocida y permite verificar el cambio. Un reemplazo completo de tema y navegación exigiría nuevas referencias y ampliaría el riesgo sin resolver los errores de contexto. Se elige la primera vía para este lote.

## Resultado concreto

- Encabezados compartidos con título, descripción y semántica de encabezado. Mantener el saludo del alumno y los nombres de destinos existentes.
- Perfil muestra el modo activo validado contra roles/distribución, separado de los roles de la cuenta. Las herramientas administrativas y solicitudes docentes corresponden al modo activo, con comprobación de rol además del callback.
- Selector de experiencias desplazable, ancho legible y opciones/fin de sesión alcanzables en 360 dp y texto al 200%. Seleccionar un modo no otorga permisos.
- Perfil usa grupos claros y filas con semántica de botón y objetivos táctiles de al menos 48 dp. Información estática se presenta como información, sin fingir navegación.
- Tutoría y coordinación eliminan el relleno duplicado de tarjetas; encabezados y mensajes usan el mismo vocabulario de producto. No exponer nombres internos de capabilities en mensajes para el usuario.
- La acción de actualizar administración se separa del título cuando compiten por ancho.

## Límites

Sin cambios de API, roles, contratos, dependencias ni permisos de servidor. Sin inventar servicios pendientes, política legal, resultados institucionales o datos de usuario. No se certifican SIII, FCM físico ni accesibilidad con TalkBack real mediante fixtures.

## Verificación

Regresiones que fallen antes del arreglo para cuenta mixta y selector compacto. Instrumentación con callbacks observados, roles no autorizados, modo revocado y texto ampliado. Capturas sintéticas antes/después cuando sea posible; ambas APK, unitarias de navegación, ktlint/lint y matriz API 31/35 del SHA entregado. El informe indica exactamente tamaños, casos y límites verificados.
