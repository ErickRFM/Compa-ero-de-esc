# V10.1 — privacidad de expedientes Tutor

Base: main `0ac109118e87740872b6275dc8f6836047115a0a`. Alcance parcial de #115 y #116; conserva los componentes y la navegación existentes.

## Comportamiento

Los roles ADMIN y SUPER_ADMIN permitían listar todos los expedientes y omitir la asignación al leer, crear o añadir notas. Los expedientes privados ahora requieren identidad activa, rol TUTOR y asignación activa/no revocada al grupo activo. Una cuenta con TUTOR y ADMIN/SUPER_ADMIN debe cumplir la misma asignación. Los permisos administrativos para gestionar asignaciones se conservan.

No existe todavía un modelo de delegación excepcional autorizado/auditable. Hasta implementarlo, un administrador sin asignación Tutor carece de acceso a notas internas. Las notas publicadas al alumno mantienen su endpoint separado y ligado al estudiante; la evidencia previa no se elimina cuando se revoca la asignación.

Android descarta expedientes, solicitudes, padrón y sus identificadores al iniciar una revalidación de ámbito, al cambiar cuenta/sesión/roles y ante rechazo 401/403. Si el ámbito no puede revalidarse, no conserva la información privada. Cancela operaciones anteriores y verifica su generación antes de aceptar respuestas, para que una respuesta tardía no repueble datos revocados. Filtra las respuestas por los grupos autorizados vigentes.

Las mutaciones existentes de seguimiento, observaciones y revisión de solicitudes ahora envían Content-Type JSON. Una regresión comprobó que la solicitud de nota antes no alcanzaba el HTTP (0 solicitudes), debido al transporte del DTO.

Los diálogos, selecciones y borradores de las pantallas existentes se descartan cuando cambia la generación de ámbito. La revisión exige una solicitud pendiente/en revisión perteneciente a un grupo vigente y la generación que abrió el diálogo. Creación y notas rechazan asimismo confirmaciones de generaciones anteriores, incluso si la cuenta nueva tiene autorización sobre el mismo recurso. Los borradores de seguimiento se limpian al cambiar alumno/grupo. Se conservan los componentes V8 y los endpoints existentes.

## Evidencia

- API: 3 regresiones observadas fallar antes de corregir acceso administrativo, revocación y cuenta inactiva. Pasan después, incluida cuenta con rol mixto y conservación de notas previas.
- Android: 5 regresiones observadas fallar antes de corregir ámbito revocado/no disponible, ámbito vacío, cambio de cuenta, respuesta tardía y mutación rechazada. Pasan después. El fallo del import de Content-Type fue un error de compilación reparado, no evidencia funcional.
- Primera suite local: 184 API +16 contratos +7 validación =207, cero fallos; Tutor Android 5/5. Log: `build/v10-1-qa/tutor-privacy-green.log`.
- Revisión final independiente: ningún Critical; un Important sobre diálogos/borradores y confirmaciones de ámbito antiguo. Corregido en una única pasada. La regresión de revisión ajena falló con 1 solicitud HTTP en vez de 0; ambas pruebas Compose fallaron porque el diálogo y el borrador seguían presentes. Después: Tutor Android 8/8 y Compose 2/2 en emulador API30, incluida decisión válida que sí alcanza HTTP. Log: `build/v10-1-qa/tutor-review-fix-green.log`. Los errores de imports/nombre del selector instrumentado se repararon y no cuentan como evidencia funcional.
- CI incluye ahora explícitamente la suite unitaria Tutor; la instrumentación existente ejecuta las nuevas pruebas Compose de las pantallas reales con identidades/datos exclusivos de prueba.
- Suite final completa: 152 Android +184 API +16 contratos +7 validación, cero fallos/errores/omitidos; lint 0 errores/52 advertencias en 22 informes. APK debug y release R8 sin firma compilados. Instrumentación: 5/5 pruebas de aplicación en cada uno de dos emuladores API30 (las mismas cinco, no diez escenarios distintos ni teléfonos físicos). Log: `build/v10-1-qa/tutor-final-scoped-verification.log`, BUILD SUCCESSFUL. Una ejecución global previa se canceló al atascarse en una librería sin pruebas; se repitió con la tarea de aplicación que usa CI. Futuras ejecuciones fijan ANDROID_SERIAL para no ocupar emuladores de otros chats.
- Escaneo de secretos y whitespace: PASS con índice temporal del paquete. CI del SHA publicado: pendiente; debe aprobar antes del merge.

| Escenario de este paquete | Resultado local |
| --- | --- |
| Admin/Super Admin sin asignación intenta leer/escribir expedientes privados | PASS |
| Cuenta mixta Tutor/Admin, asignación activa y posterior revocación | PASS |
| Cuenta Tutor inactiva, evidencia previa conservada | PASS |
| Ámbito vacío/403/no disponible, cambio de cuenta y respuesta tardía | PASS |
| Nota denegada por HTTP borra datos privados | PASS |
| Revisión ajena denegada localmente; pendiente autorizada llega a API | PASS |
| Confirmación de generación antigua frente a recurso igualmente visible en cuenta nueva | PASS |
| Diálogo abierto tras revocación y borrador tras reemplazo de cuenta, Compose API30 | PASS |
| Mongo real, identidad institucional, delegación y QA físico/TalkBack | NOT_RUN |

## Límites y compatibilidad

No cambia DTO ni introduce un sistema visual nuevo. La restricción administrativa es intencional; un cliente antiguo recibirá 403 cuando carezca de asignación Tutor. Rollback: revertir el squash completo, sabiendo que reabriría el acceso implícito y la retención privada insegura.

Revocación remota durante desconexión total no puede descubrirse instantáneamente: al revalidar o cambiar la sesión se borra el estado privado. No se garantiza retirar una respuesta que ya fue autorizada y entregada antes de revocar. Identidad Tutor real/provisión de usuarios, Mongo productivo, permisos por institución/delegación, ficha completa, documentos, citas/reportes y QA físico/TalkBack permanecen NOT_RUN. Este paquete no declara completados Tutor, F2, F5 ni V10.1.
