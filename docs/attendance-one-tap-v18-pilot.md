# Piloto aislado de asistencia en un toque (V18)

El ensayo automatizado de Wi-Fi doméstico vive en `services/api/src/test`. Usa almacenamiento en memoria, la identidad sintética `pilot-student-0001`, la institución `pilot-only-institution`, el SSID ficticio `Compa-ero-Hogar-Piloto` y un QR sintético. No crea cuentas reales ni datos MongoDB.

Ejecutar desde la raíz del repositorio:

```powershell
.\gradlew.bat :services:api:test --tests org.companerodeescuela.api.presence.AttendanceHomeWifiPilotTest
```

El test comprueba que el Wi-Fi doméstico por sí solo no inicia la jornada, que sigue siendo obligatorio un QR institucional y que la coincidencia de SSID declarada por Android queda con `networkVerified=false`. Este ensayo no demuestra presencia física ni habilita la asistencia automática; la confirmación de clase continúa en revisión docente cuando no hay un testigo de red independiente.

Para un servidor efímero de QA existe un bloque dedicado de variables `ATTENDANCE_PILOT_*`; no reutiliza `SCHOOL_*`. Ejemplo PowerShell con valores sintéticos:

```powershell
$env:APP_ENV = 'development'
$env:ATTENDANCE_PILOT_MODE = 'true'
$env:ATTENDANCE_PILOT_INSTITUTION_ID = 'pilot-only-institution'
$env:ATTENDANCE_PILOT_WIFI_SSIDS = 'Compa-ero-Hogar-Piloto'
$env:ATTENDANCE_PILOT_ENTRY_QR_SHA256 = [Convert]::ToHexString(
  [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes('pilot-only-institutional-entry-qr'))
).ToLowerInvariant()
```

El cargador de configuración falla al iniciar si `ATTENDANCE_PILOT_MODE=true` con `APP_ENV=production`, si faltan esos tres ajustes sintéticos o si se mezclan con `SCHOOL_PRESENCE_*`/`SCHOOL_WIFI_*`. El SSID ficticio solo se acepta en el bloque de piloto; producción sigue leyendo exclusivamente la configuración escolar institucional normal.

## Ensayo físico pendiente

Para una prueba con teléfonos, hace falta una instancia de desarrollo/QA aislada, Firebase de QA y cuentas sintéticas administradas por el equipo. Configurar las variables `ATTENDANCE_PILOT_*` únicamente en ese entorno efímero y usar un QR sintético; mantenerlas fuera de variables, secretos y archivos de producción. No copiar el SSID de piloto a `SCHOOL_WIFI_SSIDS`. La aplicación puede entregar un intento de clase, pero el servidor conserva el estado `REVIEW_REQUIRED` hasta que un docente lo revise.

Protocolo físico: abrir pase con docente sintético; comprobar que el dispositivo alumno recibe el aviso en foreground y background; tocarlo con la app cerrada y abierta; verificar que la app solicita la sesión autenticada y que el servidor valida la presencia y matrícula; confirmar que el roster docente se actualiza; reiniciar la app y comprobar que el registro vuelve desde el API/Mongo; repetir con sesión revocada, cuenta cambiada, permiso de notificaciones denegado, sesión vencida y dispositivo/token revocado. La ruta Inicio → Asistencia permanece disponible cuando las notificaciones están desactivadas.

## Límites

El test de piloto es reproducible y no usa infraestructura externa. La recepción física FCM, el comportamiento OEM con la app terminada y la revocación en un teléfono real siguen pendientes hasta disponer del proyecto Firebase de QA y dispositivos controlados. La prueba no reemplaza el QR de campus administrado por la institución.
