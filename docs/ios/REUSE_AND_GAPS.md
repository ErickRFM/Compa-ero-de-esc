# Reutilización real y pendientes iOS

## Código compartido en esta base

| Código | Reutilización real |
| --- | --- |
| `AuthContracts.kt` | Misma fuente original, sin copiarla: login, respuesta, refresh y registro wire. La UI inicial solo implementa login/logout |
| `ApiResponse.kt` | Misma envoltura `data` y requestId del backend |
| `ApiError.kt` | Mismos códigos y SerialName; tests contractuales. La UI usa mensajes propios por estado HTTP |
| `UserSummary.kt` | Mismo DTO y regla `isStaff` |
| `UserRole.kt` | Mismos valores wire y reglas `isStaff/isAdministrative`, incluido teacher_pending |
| Cliente móvil KMP | Nueva lógica común de URL, timeouts, serialización, llamadas, errores y cancelación; se compila para JVM y Apple |
| Framework Apple | Kotlin/Native estático, no un JAR JVM ni un módulo Android |
| V8 | Se reutilizan identidad, colores y dimensiones. El código de las vistas SwiftUI es nuevo |

Android y backend siguen compilando los contratos con su build JVM original. No dependen todavía del cliente móvil ni de SwiftUI. El nuevo build admite targets JVM, iOS dispositivo, simulador ARM e Intel; el workflow inicial verifica el simulador del host. Declarar un target iOS dispositivo no equivale a validarlo en un iPhone.

## Adaptaciones específicas

- Motor Ktor Darwin sobre networking Apple; Android conserva su motor actual.
- DTO operativos nuevos con timestamps ISO string porque los actuales dependen de `java.time.Instant`; probados contra la forma wire existente.
- SwiftUI `NavigationStack/TabView`, ciclo de vida, áreas seguras, tipografía del sistema y actualización UI en el main actor.
- ATS Debug limitado a red local y validación KMP restringida a loopback HTTP; Release HTTPS.
- Xcode framework search paths, fase Gradle previa a fuentes y esquema compartido.
- Rechazo de callbacks antiguos al cambiar endpoint, reemplazar solicitud o cerrar sesión.
- Sin Keychain ni persistencia de tokens. La sesión en memoria no implementa renovación ni cierre automático por expiración. Ningún módulo académico autenticado consume esos tokens en esta fase.

No se reutilizan `shared/models`, `Validators.absoluteHttpUrl`, `ScheduleConflictRules`, `TeacherClassContext`, los repositorios Room/WorkManager, Android Keystore, Hilt ni los ViewModels Android.

## Compose Multiplatform

Es viable para una futura UI compartida, con artefactos multiplataforma y recursos `Res`. Los composables V8 de presentación son candidatos, pero `R.drawable`, `painterResource` Android, dependencias de features, Hilt y servicios de plataforma necesitan separación/adaptación. No se migraron ni recompilaron los composables Android a iOS.

La matriz oficial de Kotlin 2.2.21 llega a AGP 8.11.1; el repo usa AGP 8.13.2. El build KMP independiente evita introducir esa combinación sin verificar ni actualizar Android. Una migración futura requiere pruebas de contratos/fechas, publicación de variantes y revisión de consumo Android/backend.

## No implementado

- Clases reales, agenda académica, agenda personal, calificaciones, avisos, canal institucional, perfiles completos, tutorías y experiencia docente/administrativa.
- Persistencia Keychain, biometría, refresh/expiración/reanudación de sesión y manejo completo de roles.
- Room/caché equivalente, aislamiento de datos offline por usuario, outbox y sincronización.
- Asistencia, QR firmado, cámara, revisión docente y evidencia Wi-Fi/campus.
- Notificaciones, tareas background iOS, adjuntos, PDF/OCR, enlaces profundos.
- Portado del fondo campus, logotipo e icono de app V8; se conserva identidad mediante tokens tipográficos y de color.
- Auditoría de accesibilidad en dispositivos reales, localización completa y validación visual de paridad.
- Firma, TestFlight, certificados, provisioning, distribución física, revisión de privacidad y publicación.

Una compilación de simulador comprueba compilación/enlace y pruebas/arranque en el runner. No prueba los puntos anteriores ni convierte esta base en una aplicación de producción.
