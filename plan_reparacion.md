# Plan de Reparación Arquitectónica - Subsistema de Impresión (Restaurante 2026)

Este plan aborda las vulnerabilidades remanentes (Puntos 3, 6, 7, 8, 9, 10, 11, 13, A3, N5, N9, N10 y N12) identificadas en el diagnóstico `reporte.md`.

## Fase 1: Control de Flujo y Saneamiento de API (N12, P6, P10, A3)
1. **Consumo de Retorno (N12, P6):** 
   - En `ServicioImpresionTicket.procesarSalida`, el caso `VISOR_PDF` y las rutas de degradación (catch blocks) deben retornar estrictamente el resultado de `abrirVisor(archivo)`.
   - En `PedidoPdfServicio.finalizar` y `reimprimir`, capturar el `boolean` devuelto por `abridor.abrir(archivo)` y, si es `false` (impresión térmica fallida), reportar en consola (o notificar a la interfaz Swing) la degradación al visor.
2. **Eliminación de Código Muerto (P10, A3):**
   - Eliminar `ACCION_IMPRESION` y el método `obtenerAccionConfigurada()` de `ServicioImpresionTicket` ya que el enrutamiento lo rige `MODO_SALIDA_TICKETS`.
   - Eliminar físicamente `src/Modelo/DatosTicketFiscal.java` al carecer de uso productivo.

## Fase 2: Blindaje de Invocación y Spooling (P3, N5, P8, P7)
1. **Resolución Estricta (P7):** En `resolverNombreRealImpresora`, devolver `null` si no hay coincidencia exacta o parcial, en lugar de retornar ciegamente el texto original.
2. **Guarda Virtual en PowerShell (N5):** Implementar la misma lista negra de impresoras ("pdf", "xps", "onenote") utilizada en el Java Spooler antes de lanzar las llamadas nativas de PowerShell, evitando disparar ventanas de diálogo del OS.
3. **Control de Ciclo de Vida PowerShell (P8):** Modificar la ejecución de PowerShell. En lugar de colgar el comando con `-Wait`, se encapsulará en un bloque `Start-Job` o se forzará la salida asíncrona, apoyándose en la destrucción del proceso Java o matando el hijo nativamente desde PS.
4. **Estado de Cola (P3):** Implementar un "Best-Effort" en el spooler de Java. Luego de `job.print()`, inspeccionar los atributos `JobStateReasons` para advertir sobre problemas físicos si el driver expone estados como `PRINTER_OFFLINE` o `OUT_OF_PAPER`.

## Fase 3: Higiene de Entorno y UX Adversarial (P9, P11, N9, N10)
1. **Resolución CWD / `.env` (P9):** Buscar el `.env` priorizando el directorio desde donde se invocó el proceso (usando `System.getProperty("user.dir")`) o desde donde se lanzó el JAR, garantizando encontrarlo fuera de NetBeans.
2. **Trazabilidad de Errores (P11):** Remplazar los `catch (Exception ignored)` silenciosos con trazabilidad en consola (`e.printStackTrace()` o logging estructurado) en las líneas señaladas.
3. **Estado de Prueba (N9):** En `imprimirTicketPrueba`, si el envío térmico falla pero el PDF se abre con éxito en el visor (`abrirVisor(...) == true`), cambiar la notificación final o permitir que retorne éxito, informando explícitamente "Ticket generado en PDF (Impresora falló)".
4. **Desacoplamiento de Vista (N10):** En `Sistema.java`, remover la invocación a `setImpresoraGlobal(...)` y `setModoSalidaGlobal(...)` de los listeners `ItemStateChanged` de los ComboBox. La configuración global solo debe mutar en RAM al hacer click en el botón "Guardar Configuración".
