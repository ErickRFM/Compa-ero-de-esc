# V9 — Asistencia por doble filtro: entrega técnica

Base: `main@c4cb5acf`. Rama: `feat/v9-campus-attendance-confirmation-20261008`.

## Arquitectura integrada
1. **Ingreso escolar**: QR administrado, red escolar autorizada y jornada activa con hora del servidor (`SchoolPresenceService`).
2. **Consulta del profesor**: `GET /attendance/occurrences/{id}/campus-roster?date=YYYY-MM-DD`; valida que la ocurrencia pertenezca al profesor, resuelve exactamente un grupo académico autorizado y muestra solo miembros de ese grupo. Si no existe mapeo inequívoco se devuelve error, nunca un total falso de cero.
3. **Inicio de pase**: el docente usa el servicio existente de sesiones (`POST /attendance/sessions`). El QR firmado permanece como alternativa.
4. **Aviso dentro de la app**: Android revisa convocatorias disponibles durante la sesión del alumno cada 20 segundos. No equivale a FCM; requiere que la app esté activa y conectada.
5. **Confirmación sin segundo QR**: `POST /attendance/sessions/{id}/confirm` exige token de alumno, inscripción, jornada escolar vigente y Wi-Fi aceptado. Resuelve `PRESENT` o `LATE` con reloj del servidor. Reintentos no duplican el registro canónico.
6. **Recuperación**: `GET /attendance/sessions/{id}/mine` devuelve exclusivamente el registro del alumno autenticado.
7. **Correcciones**: se conserva el servicio de revisión del docente, que separa evidencia y decisión académica.

## Configuración
- `SCHOOL_WIFI_SSIDS` o `SCHOOL_WIFI_BSSIDS` para habilitar verificación escolar.
- `ATTENDANCE_QR_SECRET` para QR de clase firmado y rotativo.
- `ATTENDANCE_GRACE_MINUTES` (opcional, entero de 0 a 15, por defecto 5).
- MongoDB en entornos no locales para persistencia.
- Grupos académicos y membresías aprobadas por administración, con nombres consistentes con la ocurrencia programada.

**Advertencia:** el identificador SSID/BSSID aportado por Android es evidencia declarada por el dispositivo y no prueba criptográfica de ubicación. No usarlo como sanción automática cuando una red falla.

## Límites explícitos pendientes
- **FCM push remoto**: el repositorio tiene registro de tokens de dispositivo, pero no hay despachador Firebase ni credenciales de servicio configuradas. Implementar despachador durable, tokens persistentes, control de reintentos y opt-in antes de declarar notificaciones en segundo plano operativas.
- **Nombre de alumno**: el padrón muestra ID/matrícula; falta una proyección de identidad autorizada con datos nominales y consentimiento/políticas institucionales.
- **Entrada escolar no equivale a asistencia de clase** y una salida/desconexión no equivale automáticamente a ausencia o retardo.
- **Registro de ausencias sin intento**: el backend aún requiere una proyección/cierre sobre el padrón completo para cerrar ausentes sin inventar intentos.
- **Políticas por institución y cambios de calendario**: la tolerancia es de servidor, pero global; no está administrada por curso o institución.
- **Prueba real**: deben verificarse API desplegada, Mongo, Wi-Fi universitario, dos dispositivos Android y condiciones de conectividad.
- **Endurecimiento de autenticidad de red**: evaluar retos de corta duración emitidos en red controlada, no confiar solamente en SSID/BSSID.

## Matriz mínima de aceptación
| Escenario | Resultado |
|---|---|
| QR escolar inválido | Jornada denegada |
| Estudiante sin jornada activa | Confirmación denegada |
| Red no autorizada | Confirmación denegada |
| Estudiante ajeno al grupo | Confirmación denegada |
| Dentro de tolerancia | PRESENT, VERIFIED, CLASS_CALL_CONFIRMED |
| Después de tolerancia y antes de cierre | LATE, VERIFIED, CLASS_CALL_CONFIRMED |
| Confirmación repetida | Un registro canónico |
| Profesor ajeno intenta consultar grupo | Acceso denegado |
| Grupo sin mapeo inequívoco | Error explícito, no lista vacía |
| Push desactivado, app abierta | Convocatoria consultable desde pantalla |
| Sin conexión | No se concede PRESENT automáticamente |

## Criterios de fusión
CI de API y Android verde, correcciones de reviewers resueltas, diferencia con main inspeccionada y APK debug/release generados con SHA. Las pruebas físicas y FCM siguen como criterios externos de release.
