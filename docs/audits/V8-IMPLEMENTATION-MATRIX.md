# Matriz de integración V8

PASS significa únicamente la comprobación descrita; PARCIAL y NO VALIDADO impiden considerar la pantalla completa. Referencias y comparativas en [auditoría](V8-UI-EMULATOR-AUDIT.md); resultados de ejecución en [QA](V8-ANDROID-QA-REPORT.md).

| Pantalla / componente | Integración real | Resultado |
|---|---|---|
| Login: fondo / logo / slogan | `V8CampusBackdrop`, Canvas de birrete, `V8BrandHeader(stacked=true)` | PARCIAL: asset distinto, decoración inferior faltante |
| Login: título / vidrio / CTA | Compose nativo, `V8AcademicField`, `V8GlassCard`, `V8RedPrimaryButton` | PASS render; PARCIAL fidelidad |
| Login: identidad / password / mostrar | Sanitización y validadores existentes, IME Next/Done, visibility saveable | PASS flujo de cuenta QA y teclado |
| Login: errores / carga / persistencia | `SessionViewModel`, repositorio JWT, controles deshabilitados al enviar | PASS sesión QA persiste; estados transitorios no equivalen a producción |
| Login: registro | Navegación a `RegistrationScreen`, validación institucional existente | PASS navegación instrumentada; NO VALIDADO alta completa externa |
| Login: recuperar contraseña | Diálogo informa consultar administración | NO IMPLEMENTADO endpoint; no envío fingido |
| Inicio: saludo / fecha | `HomeViewModel` / `AcademicRepository`, fecha local | PASS datos QA reales del proveedor local |
| Inicio: avatar / notificaciones / avisos | Solo acceso nativo a Perfil, no API de foto/avisos agregados | PARCIAL / NO IMPLEMENTADO integración de referencia |
| Inicio: indicadores | Conteo diario y siguiente materia de agenda fusionada | PASS esos datos; NO IMPLEMENTADO porcentaje y pendientes sin fuente |
| Inicio: timeline / próxima | `TodayOverview` y `V8DailyClassRow` | PASS; importación personal visible al regresar mediante ON_RESUME |
| Horario: Semana / Día / días | `ScheduleScreen`, selector controlado, semana nativa | PASS recorrido; fin de semana según datos |
| Horario: periodo | Sin periodos en contrato actual de carga | NO IMPLEMENTADO; no inventar semestre |
| Horario: filtros | Chips por materias reales, Todas | PASS integración; conflictos usan agenda completa |
| Horario: posición / duración / simultáneas | `agendaGridLayout`, `WeeklyAgendaGrid`, `DayTimeGrid` | PASS pruebas geométricas; texto completo en detalles/lista |
| Horario: detalles / editar / borrar | Metadatos completos, editor y confirmaciones; institucional protegido | PASS restricciones por código/tests; NO VALIDADO ciclo completo de edición/borrado en runtime |
| Horario: arrastre | Long press + propuesta con snap15 y confirmación existente | PASS reglas/carriles en tests; NO VALIDADO arrastre completo en runtime |
| Horario: PDF / imagen | OpenDocument → PdfRenderer/ML Kit → parser → revisión → repositorio personal | PASS PDF e imagen de QA: selector, OCR, revisión y confirmación; otros formatos institucionales NO VALIDADO |
| Horario: persistencia / deshacer | Room por cuenta, `PersonalScheduleRepository`, undo existente | PASS pruebas existentes; sin inferir todos los escenarios runtime |
| Canal: acceso / selector | Clases → Canal de clase, canales de servidor | PASS navegación QA |
| Canal: materia / docente / grupo | `ClassChannelSummary`, publicaciones de docente real local | PASS fuente; aula ausente del contrato de canal, no inventada |
| Canal: Avisos / Chat | Mismo feed; Chat permite respuestas del post, Avisos lectura | PASS diferencia funcional; no chat libre |
| Canal: docente publica / alumno limitado | Permisos `canPublish` y autorización API | PASS HTTP403 alumno y respuesta permitida aceptada |
| Canal: recursos | `LocalUriHandler.openUri` con URL provista | NO VALIDADO apertura de recurso externo válido en este entorno |
| Canal: avatar / reacciones / fechas / lectura | No se añadieron estados que el contrato no representa | PARCIAL frente al mockup |
| Pase: estado red / jornada | Evidencia de respuesta escolar vigente del servidor | PASS política; NO VALIDADO Wi-Fi/QR escolar reales |
| Pase: ubicación | “No utilizada” | PASS honestidad; no supuesto GPS |
| Pase: panel / cámara / galería / pegar | Dialog scanner nativo, OpenDocument, decoder y API existente | Acciones comprobadas, ver informe por permiso/decodificación |
| Pase: filtro1 | QR institucional + SSID/BSSID autorizados | PASS suites backend; NO VALIDADO infraestructura escolar |
| Pase: filtro2 | Sesión docente, inscripción/tiempo, outbox/Room/WorkManager/veredicto API | PASS suites; sesión local QA no prueba temporal escolar real |
| Pase: confirmación / recientes / cohortes | Solo estados/filas retornadas; no métricas inventadas | PARCIAL visual, NO VALIDADO asistencia real completa |
| Compartido: cinco destinos / insets | `CompaneroBottomBar`, `CompaneroScaffold` | PASS roles/tests/recorrido; QR central circular |
| Compartido: alto contraste / fuente / movimiento | Preferencias existentes preservadas, outline V8 y motion | PASS integración; configuraciones runtime en QA |
| Compartido: componentes sin uso | Búsqueda de consumidores antes de retirarlos; sección V8 usada por Home | PASS inspección de dependencias |

No hay pantalla declarada completa: faltan fidelidad de assets, fuentes de ciertos elementos y verificación institucional real.
