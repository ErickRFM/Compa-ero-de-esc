# V10.1 — Presencia y revisión de asistencia

## Problema y alcance

La API aceptaba el SSID/BSSID declarado por Android como verificación de red y
asignaba PRESENTE/RETARDO automáticamente al confirmar un pase. No existe en esta
implementación un testigo del plantel autenticado de forma independiente. Una
firma QR acredita el token emitido; esos datos no prueban la ubicación física.

Este cambio conserva los contratos, repositorios y componentes V8 existentes.
El ingreso se conserva como captura activa, con QR reconocido y red declarada,
pero `networkVerified=false`. Los pases recibidos quedan `REVIEW_REQUIRED`, sin
disposición automática. La hora de recepción sigue siendo la del servidor.
La red permitida y la inscripción siguen siendo requisitos de la captura en línea.

## Compatibilidad e historial

Las vistas del alumno, padrón docente, ingreso escolar y respuestas idempotentes
normalizan los registros automáticos antiguos para revisión. La lectura no
reescribe los datos originales. Al revisar, se conservan estado/motivo originales,
revisor, fecha, motivo e historial. Los controles de autorización actuales siguen
aplicándose; este cambio no crea permisos ni una fuente de presencia ficticia.

Android también trata respuestas antiguas VERIFIED/PRESENT/LATE sin revisor y
fecha válidos como pendientes de revisión. Una fila del outbox sincronizada solo
acredita recepción; el registro de clase obtenido de la API contiene la decisión
humana. Las decisiones humanas válidas siguen visibles. Se reutilizan las tarjetas,
avisos y botones existentes, sin crear otro sistema visual.

El contador docente usa la misma política que la lista: una disposición humana
terminada no queda pendiente, y una respuesta automática antigua sí requiere
revisión. Validar evidencia por excepción sin asignar PRESENTE se comunica como
validación docente; el rechazo tiene su propio mensaje. Cerrar un ingreso antiguo
parte del registro almacenado y conserva su evidencia, aplicando la proyección
honesta únicamente a la respuesta.

Los clientes antiguos recibirán pases para revisión en lugar de confirmación
automática. No se elimina la configuración de tolerancia horaria, pero deja de
decidir RETARDO automáticamente mientras no exista evidencia independiente.
Revertir el cambio restauraría afirmaciones de presencia basadas en declaraciones
del cliente; no es un retroceso seguro para la regla de presencia de V10.1.

## Evidencia local

- API: cinco regresiones inicialmente fallidas sobre ingreso, pases y registros
  históricos. Tras la corrección, 186 pruebas API, 16 de contratos y 7 de validación
  pasan sin fallos.
- Android: regresiones sobre recepción local y respuestas antiguas; pasan las
  18 pruebas de política de asistencia.
- Interfaz Compose real: tres pruebas pasan en el AVD API 30 `emulator-5554`:
  captura habilitada sin verificación física, respuesta automática antigua sin
  confirmación positiva y resultado humano conservado.
- Revisión independiente del árbol aislado: ningún Critical y tres Important.
  Se reprodujeron los tres en una única pasada: 1/8 pruebas API falló al cerrar
  evidencia histórica, 3/10 de política Android fallaron por contador/mensajes,
  y 1/4 pruebas Compose falló por una validación humana mostrada como pendiente.
  Las tres correcciones pasan en la única pasada prevista; sin segunda revisión.
- Verificación completa local: 166 pruebas unitarias Android y 210 de API y
  módulos compartidos, cero fallos; lint, cero errores y 52 advertencias en
  22 informes; APK debug y release con R8 construidos; nueve pruebas instrumentadas
  de la app pasan en el AVD API 30 propio. Gradle final: SUCCESS, seis minutos.
- Escaneo de secretos y revisión de espacios pasan. CI del commit aislado es
  obligatorio antes del merge. No se certifica un piloto, una base Mongo real,
  un despliegue ni una release firmada.

## Pendientes explícitos

La fuente independiente del plantel, Mongo real, integración institucional,
revocación de asignaciones docentes por recurso, dos teléfonos físicos y TalkBack
permanecen NOT_RUN o pendientes. No se declara presencia física verificada.
El QR de muro y su outbox offline (#125) no se implementan en este paquete.
El PR #123 sigue condicionado a su prueba física y deberá reconciliar esta política
antes de integrarse. Esta entrega completa una corrección de verdad de evidencia;
no completa toda la fase F3 ni la misión V10.1.
