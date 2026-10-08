# Auditoría V8 — rutas, regreso a Inicio y estado de pestañas

**Base:** PR #100 `codex/v8-emulator-audit-20261007` (`dfc1db3`). **Incidencia:** desde Horario, el usuario toca Inicio en el emulador y no se produce el regreso esperado. **Estado:** el reporte es real; la causa exacta no se ha demostrado con trazas de interacción ni prueba de UI autenticada.

## Trazabilidad

- `MainActivity` registra las rutas principales `home`, `schedule`, `attendance`, `classrooms`, `profile` y rutas complementarias de docente, tutor, administración y ajustes.
- `RoleExperienceResolver` define qué destinos son tabs, por experiencia, y el destino raíz del gráfico. Alumno tiene Inicio/Horario/QR/Clases/Perfil **en #100**, pero solo tres tabs en el `main` base.
- `CompaneroBottomBar` navega desde las tabs, usando estado de Navigation Compose. Antes, el índice para rutas desconocidas se forzaba a 0, atribuyendo falsamente la selección a Inicio.
- `CompaneroScaffold` trataba Perfil como secundaria aunque #100 la agrega como tab, y no identificaba todas las secundarias (por ejemplo, integración y calificaciones), dejando una barra no relacionada o iconos redundantes.
- Las pruebas existentes cubren resolución de roles, autenticación y smoke de login. CI verde no demuestra que se pueda pulsar Inicio desde Horario en una sesión real.

## Cambios aislados

1. Acceso a destino raíz: `navigateToTopLevel` hace `popBackStack` al root existente cuando lo encuentra y usa una única navegación de respaldo si no está en la pila. Otras tabs conservan la política `popUpTo/saveState/restoreState/launchSingleTop`.
2. La selección de tabs ahora permite **ninguna seleccionada** si la ruta no coincide. No se pinta Inicio durante una ruta secundaria.
3. La barra inferior permanece en Perfil **solo para los roles donde Perfil es realmente tab**. Para el resto, Perfil tiene barra superior y acción Volver; sucede lo mismo con Integraciones, Canal y Calificaciones cuando son secundarias.
4. Se elimina el botón flotante duplicado de Perfil del estudiante; otros perfiles conservan su entrada a Perfil.
5. Pruebas JVM de política de pantalla secundaria en experiencias estudiante, docente, tutor y sin rol.

## Matriz de regresión requerida en APK instalada

| Flujo | Esperado |
| --- | --- |
| Alumno: Inicio → Horario → Inicio | Vuelve a Inicio inmediatamente, sin tocar dos veces ni abrir dos Homes |
| Alumno: Inicio → Horario → QR → Horario → Inicio | Retorno al Inicio, sin estado de QR sobrepuesto |
| Alumno: Horario → Perfil → Inicio | Perfil solo una vez en barra; Inicio responde |
| Alumno: Clases → Canal → Atrás | Se retorna a Clases y Canal no marca Inicio como seleccionado |
| Docente: Hoy → Horario → Hoy | Regresa a TeacherHome, sin saltar a Home de estudiante |
| Docente: Hoy → Calificaciones → Atrás | Retorna a Hoy con encabezado y botón Volver |
| Tutor: Tutoría → Solicitudes → Tutoría | Regresa a TutorHome y mantiene tab correcta |
| Coordinación / Admin: cualquiera de sus tabs → Home del rol | Nunca salta al Home de estudiante |
| Ajustes de integración → Atrás | Regresa a Perfil con navegación secundaria, sin barra falsa |
| Cambio de rol + logout/login | Reinicialización correcta de experiencia, destino raíz y barra |
| Emulador compacto / tableta / fuente 200% | Todos los targets reciben toque; no interceptan overlays |

## Evidencia solicitada

Registrar video corto, identificación de APK instalada (build SHA), `adb logcat` en el momento exacto de tocar Inicio y captura de pantalla final. Validar con sesión autenticada y los cinco tabs de #100. Las dos pruebas smoke actuales de login no cubren estas transiciones.

Comandos útiles:

```powershell
adb devices
adb shell pm path org.companerodeescuela
adb logcat -c
adb logcat -v time > nav-return-home.log
# Reproducir Horario -> Inicio; detener logcat con Ctrl+C
```

**Cierre:** no declarar FIXED ni fusionar sobre `main` hasta que CI de esta rama pase y el flujo de navegación completo se verifique visual y táctilmente en el emulador. El PR #100 sigue siendo dependencia para la navegación de cinco destinos.
