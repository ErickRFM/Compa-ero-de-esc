# Más diseño y movimiento sobre borgoña

Base `fb2f539e`. El usuario pidió reforzar diseño y animaciones; conserva su elección anterior de borgoña y consistencia.

Dirección: superficies editoriales sobrias con una tarjeta de identidad borgoña, accesos con iconos, encabezados con acento y tarjetas de accesos rápidos más legibles. Se descarta añadir desenfoques y adornos permanentes porque aumentan ruido y coste visual. Se reutilizan V8 y Material 3.

Movimiento: respuesta de pulsación de 100–140 ms mediante transformaciones, navegación de 180–220 ms con fundido y desplazamiento corto. Sin bucles decorativos, entrada retardada de controles ni temporizadores de carga. Mantener el scroll y la misma geometría de layout. Con movimiento reducido: navegación inmediata, indicadores sin desplazamiento animado y controles sin escala. Compose mantiene su control de duración del sistema.

Cambios: core:designsystem consume core:motion (sin ciclo ni librería nueva); componente de identidad sin autoridad de roles; Perfil conserva su validación y callbacks; Inicio conserva sus cuatro destinos. No cambian APIs, permisos ni almacenamiento.

Verificación: RED/GREEN para pulsación y movimiento reducido; pruebas de navegación/cancelación/callbacks; capturas sintéticas nuevas de Perfil claro/oscuro/200%; ambas APK, lint/ktlint y CI API 31/35 del SHA final. La revisión visual no certifica frame rate ni hardware físico.
