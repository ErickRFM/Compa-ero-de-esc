# Plan de auditoría V8 en emulador

Contrato: pedido del usuario del 7 de octubre de 2026 y cinco imágenes adjuntas. Base remota confirmada: `9445d2487c0b0b682b98420ba4266df134d342ff`. Ejecución autónoma autorizada por el usuario.

Conservar Compose nativo, MVVM, autenticación y fuentes de datos. Las fixtures existentes se ejecutan exclusivamente contra API local con JWT; nunca se consideran validación de producción. No incorporar mockups completos a recursos.

- [x] Inspeccionar Git, PR, JDK, SDK, AVD y referencias; crear rama desde main vigente.
- [x] Compilar APK inicial y arrancar AVD/API local QA.
- [x] Capturar baseline disponible de Login, Inicio y horario. Canal/QR eran inaccesibles; se documenta la ausencia. Estados restantes se comprobaron al integrar navegación.
- [x] Corregir navegación V8 en `core/navigation`: Inicio, Horario, QR, Clases y Perfil; acceso al canal desde Clases. Verificar roles con `RoleExperienceResolverTest`.
- [x] Corregir Login y componentes compartidos: formulario con iconos, jerarquía, transparencia, insets, colores legibles; conservar validación y teclado. El backend carece de recuperación: documentar explícitamente la limitación sin crear una acción ficticia.
- [x] Corregir composición de Inicio, horario semanal proporcional y asistencia; no fabricar métricas ni validaciones. Eliminar componentes huérfanos únicamente tras buscar dependencias.
- [x] Ejecutar `compileDebugKotlin`, `assembleDebug`, `lint`, suites JVM y `connectedDebugAndroidTest`; actualizar smoke test obsoleto tras reproducir su fallo.
- [x] Capturar resultado y configuraciones 360/390/430 dp, fuente ampliada y tablet; comparar referencias recortando marco y normalizando escala. Guardar originales fuera de producción.
- [x] Generar auditoría, matriz e informe con PASS/FAIL/NO VALIDADO y evidencia reproducible; identificar APK con SHA-256 y commit.
- [x] Revisar diff, commit, PR con evidencia; integrar solo si compilan y pasan las regresiones requeridas. Con bloqueos, dejar PR listo y describirlos sin afirmar cierre.

Riesgos a comprobar: texto largo/fuente grande, periodos y fines de semana, clases simultáneas o cortas, permisos/QR expirado, API desconectada y persistencia. Capturas son evidencia de renderizado; datos QA y CI verde no prueban fidelidad ni infraestructura escolar real.

La marca indica ejecución de la fase y entrega de su evidencia, no fidelidad terminada. Se reconcilió main75b436d dentro de la rama. Permanecen PARCIAL/NO VALIDADO los estados y dependencias enumerados en los tres informes; PR borrador sin merge. La ejecución de algunos recorridos (TalkBack, CRUD/arrastre completo, asistencia real) permanece abierta explícitamente.
