# V10 Smart Role Login — auditoría y ejecución
Base origin/main: 0ac109118e87740872b6275dc8f6836047115a0a.
Rama: feat/v10-smart-role-login. Checkout principal con trabajo ajeno; worktree independiente.

| Componente | Clasificación | Acción |
|---|---|---|
| LoginScreen/AuthV8Layout/formulario | EXISTE / MODIFICAR | Pager de cinco accesos encima de un formulario estable; sin servicios duplicados |
| RoleExperienceResolver/AppExperience/UserRole/Destination | REUTILIZAR | Validar intención contra roles devueltos por backend; selector existente ante desacuerdo |
| Perfil/ActiveExperiencePreferences | REUTILIZAR | Preferencia por usuario autorizada; cambio sin otra sesión |
| Ktor/JWT/Keystore/refresh/logout | REUTILIZAR / MODIFICAR | Preservar protocolos; verificar auth/me al restaurar; corregir duplicados/cancelación con regresiones |
| V8FrostedGlassPanel/fondo campus/tokens V9 | REUTILIZAR | Adaptación de login al tema existente sin cambiar otras superficies |
| AppearancePreferences/CompaneroTheme/movimiento | REUTILIZAR | Preferencias existentes y recursos Android es/en |
| Carrusel/flechas/indicadores | FALTA | HorizontalPager, objetivos táctiles, semántica y transición 250 ms/reducida |
| Autoridad servidor V10.1 | EXISTE | auth/me, generación, actividad/roles vigentes, revocación atómica; no reescribir endpoints |
| Limitación intentos | EXISTE | LoginAttemptLimiter en AuthRoutes; conservar 429/Retry-After |
| MFA/proveedor institucional productivo | FALTA | No declararlos implementados ni producción segura |
| Pruebas/capturas login V10 | FALTA | Regresiones JVM, instrumentación Compose, APK y capturas reales |

Línea base aislada: BUILD SUCCESSFUL en 2m38s, auth/navegación/movimiento/API y APK Debug (v10-baseline.log ignorado).
Orden: pruebas de intención/cancelación/duplicados → implementación → instrumentación/visual → regresión completa → revisión → PR/merge condicionado a checks.
El usuario pidió ejecutar todas las fases autónomamente; se conserva su diseño descrito y no se añaden puertas de aprobación de especificación/plan.
Las pruebas y límites finales se registrarán por SHA; MFA pendiente no es evidencia de seguridad productiva.
