# V10.1 — ejecución F0 del QR docente

Base integrada auditada: `origin/main@97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca`.
Trabajo de contingencia: PR #123, rama `feat/attendance-offline-teacher-qr-fallback-20261008`.
Este informe documenta comprobaciones de código; **no autoriza todavía el merge ni una entrega V10.1**.

## Correcciones reproducidas

| Caso | Antes | Resultado comprobado |
| --- | --- | --- |
| API rechaza al docente con 401/403/409 | La caché evitaba la consulta al servidor | Se consulta primero; un rechazo definitivo invalida el paquete |
| Logout/cambio de cuenta | Se podía leer el QR guardado sin identidad local | Se exige propietario docente; el paquete se vincula también al ID de login |
| Nuevo login de la misma cuenta | Reutilizaba firmas de otro login | La nueva sesión no puede leer el paquete anterior |
| Reloj atrasado | Rehabilitaba un QR ya vencido | Se invalida la caché; ancla persistida de reloj, tiempo monotónico y arranque |
| Reinicio de proceso | Se restauraban paquetes expirados | Se filtran las franjas expiradas; proceso en el mismo arranque puede restaurar franjas válidas |
| Reinicio del teléfono | No había control del arranque | Requiere volver a descargar un paquete online |
| Limpieza sin sesión recordada | Quedaban paquetes huérfanos | Se borran todos los paquetes y sesiones del propietario |
| Cola con cuentas A/B | A podía bloquear la entrega pendiente de B | Room selecciona únicamente filas de la cuenta autenticada; A se conserva |
| Caducidad QR en la frontera exacta | `expiresAt` todavía se consideraba válido | En ese instante ya se clasifica como expirado |

No se cambió el secreto HMAC ni se incorporó a Android. No se mezclaron las evidencias de entrada escolar, clase o eventos. Un QR firmado sin la validación escolar completa continúa produciendo `REVIEW_REQUIRED / OFFLINE_NETWORK_QR_REVIEW`, sin disposición PRESENTE automática.

## Evidencia automatizada

Todas las correcciones anteriores se reprodujeron antes de corregirlas. Los logs locales están en `build/v10-1-qa/`, excluidos de Git.

- `AttendanceRepositoryTest`: 12 pruebas, sin fallos tras corregir propietario, login y retroceso de reloj.
- API y contratos/validaciones compartidos: 196 pruebas, sin fallos.
- Almacén Android real y worker real con Room: 6 pruebas en AVD API 30, sin fallos. Incluyen restauración válida, cuenta distinta, expiración, reloj, arranque y aislamiento de cola.
- Verificación completa Android, lint, APKs y smoke de autenticación: pendiente de registrar resultado final.

El test del worker usa el patrón oficial de [TestListenableWorkerBuilder](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl), con el worker y Room reales; solo sustituye la frontera HTTP por respuestas sintéticas.

## Gates todavía abiertos

| Matriz | Estado | Motivo |
| --- | --- | --- |
| GIT03 | PASS | #112 y #124 incorporados en esta rama sin conflictos; CI del baseline `97e68e1` correcta |
| QR04 / QR05 / QR07 | BLOCKED para aceptación completa | Falta la prueba obligatoria con dos teléfonos, avión, restauración, reloj y revocación; los casos automatizados indicados arriba sí pasaron |
| UX05 | BLOCKED para cotejo real | Existe fixture 9A; no se encontró el PDF original entre los adjuntos. Se solicitó su ruta local |
| QR03 | NOT_RUN | #125 todavía requiere un outbox escolar separado y un formato de QR verificable con clave pública |
| AUTH01 / AUTH02 | NOT_RUN en esta fase | Auditoría confirma que el plugin JWT todavía omite generación y autoridad actual de la cuenta; debe corregirse en una rama de seguridad independiente |
| AUTH05 / GUEST / EVENT | NOT_RUN | Contratos propuestos; no hay modelo institucional/grants/eventos productivo que permita declararlos implementados |
| RC01 | BLOCKED | PR #123 sin merge, prueba física pendiente y configuración de release no acreditada |

`SSID/BSSID` declarado por el dispositivo no constituye prueba física independiente. El gate de redes confiables sigue abierto. No se verificó despliegue Render/Mongo real ni sincronización institucional de calificaciones.
