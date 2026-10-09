# V10.1 — Asignación docente vigente y datos de asistencia

## Problema

Ser quien abrió un pase conservaba acceso al padrón, sus revisiones, cierre y
emisión QR aunque la asignación académica hubiera desaparecido. Los roles vigentes
del JWT no sustituyen la autorización sobre ese recurso. Android también retenía
datos privados al recibir un rechazo de permisos de esas operaciones.

## Corrección

Se reutiliza `AttendanceOccurrenceResolver` y el proveedor académico existente.
Cada operación docente compara la ocurrencia vigente con el actor, fecha, curso,
grupo e identificador del pase; una clase cancelada no otorga ese acceso. Los pases
sin asignación vigente se excluyen de la lista del docente. La autoridad ausente
o no disponible falla de forma segura; no se confía en el propietario histórico.
La composición comparte el mismo guard entre padrón, revisión, cierre y emisión QR.
El alcance administrativo explícito conserva sus operaciones existentes.

Android retira el padrón, ingreso escolar, QR, sesión y listas privadas al perder
el alcance o no poder revalidar la autoridad de clase. Cancela las operaciones
anteriores para evitar que una respuesta tardía repueble datos retirados.
Una revalidación comienza con datos privados vacíos. Los errores ordinarios de
validación conservan los datos autorizados para permitir corregir el formulario.
La indisponibilidad del ingreso escolar opcional solo retira ese padrón; un
401/403/404 allí invalida el alcance de la ocurrencia compartida.

El editor conserva el motivo y la decisión enviados al recibir un error de
validación. Solo una confirmación exitosa del registro correspondiente lo cierra;
la generación del alcance elimina el editor si se retiran los permisos.

Se reutilizan las pantallas V8; los diálogos y borradores se reinician con la
generación del alcance, incluso si la respuesta revalidada conserva los mismos IDs.

## Evidencia

- Backend: 7/8 regresiones inicialmente fallaron: lista, padrón, cierre, revisión,
  emisión QR, indisponibilidad y curso distinto. Tras el guard, pasan 195 pruebas API
  y 23 de contratos/validación, además de `installDist`.
- Android: 4/4 regresiones iniciales fallaron por retención de datos; después,
  otras 2/8 detectaron los consumidores de cierre e ingreso escolar pendientes.
  Las ocho pasan tras la corrección, incluida una respuesta tardía no cancelable
  en la frontera HTTP y un error de validación ordinario.
- Compose real: 2/2 regresiones inicialmente fallaron por mantener la confirmación
  de cierre y el borrador de revisión después de cambiar la generación. Las dos
  pasan tras aplicar el límite de alcance al contenido docente existente, en API 30.
- Revisión independiente sobre la instantánea de 13 archivos: sin Critical y con
  un Important aceptado, el borrado anticipado del editor al enviar. Una única
  pasada de corrección: dos pruebas de la pantalla real, ViewModel, repositorio y
  HTTP reprodujeron la pérdida tras 400. La verificación posterior incluye
  reintento exitoso y pérdida de permisos tras el error de validación: ambas
  pasan en API 30. No se hizo una segunda revisión.
- Verificación completa inicial: 174 pruebas Android y 218 API/compartidas,
  11 instrumentadas en API 30, sin fallos; lint sin errores, 52 advertencias en
  22 informes; APK debug y release R8 sin firma generados.
- Verificación completa final tras corregir el editor: 174 Android y 218
  API/compartidas, 13 instrumentadas en API 30, sin fallos; lint sin errores y
  52 advertencias en 22 informes; APK debug y release R8 sin firma generados.
  El comando completo terminó con `BUILD SUCCESSFUL` en 4m 45s. Formato y
  escaneo de secretos pasan. El CI del SHA final es obligatorio antes del merge.
- CI inicial: ambas instrumentaciones ejecutaron 13 pruebas y fallaron las dos
  nuevas al buscar una fila todavía no compuesta fuera del viewport. Reproducción
  local a 360×640dp: 2/2 fallos. Se corrigió exclusivamente el recorrido del test:
  desplaza la lista vertical hasta la fila, identificando su eje de scroll estable.
  Pasan las dos regresiones y la batería completa en ese tamaño: 392 unitarias,
  13 instrumentadas, lint sin errores y APK debug/release generados; `BUILD
  SUCCESSFUL` en 1m 42s. El runtime conserva la corrección ya revisada.

## Compatibilidad y pendientes

Sin cambios de contratos, schema, credenciales o configuración local. Una fuente
académica que omita clases históricas no permite revisar esos pases como docente;
el alcance administrativo sigue disponible. No se inventa un proveedor nativo ni
asignaciones: la integración institucional real y la gestión nativa completa de
asignaciones continúan pendientes.

Mongo real, revocación instantánea estando desconectado, el intervalo entre comprobar
la asignación y escribir, y revisiones concurrentes entre instancias no quedan
certificados. Los intentos estudiantiles, tokens previamente emitidos y su revocación
por cancelación requieren su propia auditoría; siguen sujetos a revisión, no se
convierten automáticamente en PRESENTE. #123 mantiene su gate físico y debe
reconciliar estos cambios. No se certifica toda la fase de permisos ni una release.
Revertir restauraría el acceso del propietario histórico después de una revocación.
