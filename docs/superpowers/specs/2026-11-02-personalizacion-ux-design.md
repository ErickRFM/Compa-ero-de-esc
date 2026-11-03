# Personalización, claridad y carga

Base `56411d0a`. El usuario pide investigar referencias, reducir texto y afinar layout, personalización, movimiento y carga. Conserva elección previa de borgoña como valor inicial; los seis acentos y el color dinámico siguen disponibles.

Dirección elegida para implementar: diseño compacto con títulos breves, identidad de perfil sin encabezados duplicados, accesos de Inicio sin párrafos y controles que se reorganizan al ancho disponible. Alternativas descartadas por el agente: imponer una sola paleta (contradice personalización) o sustituir todo el lenguaje visual por un concepto nuevo (pierde consistencia y no resuelve desbordamientos).

La investigación visual contrasta el concepto Student Scheduling de Dribbble, imágenes comerciales de MyStudyLife y las capturas oficiales de Now in Android/Compose Samples. Son referencias de jerarquía y adaptación, no evidencia de superioridad ni pruebas de usuarios. Instagram no entregó contenido verificable. El informe conserva enlaces y límites; no se copian assets.

Corregir parejas de contraste en los seis acentos, preservar primary/onPrimary y primaryContainer/onPrimaryContainer dinámicos y evitar superficies oscuras que ignoran color dinámico. Reutilizar tokens V8. Pruebas a 320/360/430 dp, texto de sistema 200% combinado con Grande 120% (240%), claro/oscuro, contraste y movimiento reducido. Los switches deben tener nombre y activarse desde su fila. Las preferencias existentes conservan valores y persistencia.

Carga: pantalla de apertura identificable mientras session.checking es verdadero; sin demoras mínimas ni progreso porcentual inventado. Contenido de Inicio pendiente usa placeholder en vez de afirmar que no hay clases; una actualización conserva la agenda existente y los accesos. Estado compartido de carga accesible, con alternativa estática al reducir movimiento. La animación de entrada es breve y finita.

No cambia autorización, APIs ni servicios institucionales. No se certifica competitividad comercial, FPS, hardware o TalkBack real sin sus mediciones.
