# Reporte de diagnóstico adversarial — Impresión tickera (iteración 4)

**Proyecto:** Restaurante 2026 · **Fecha:** 2026-10-09
**Alcance:** Re-verificación de las 14 faltas + hallazgos N1–N12 **contra el código recién modificado por el usuario** (diff respecto a `384bfac`).
**Método:** Lectura de fuentes + `git diff 384bfac`, compilación real (`ant clean jar`, `ant compile-test`). **No se modificó ningún `.java` ni configuración**; sólo se actualiza este `reporte.md`.

> **Advertencia de esta iteración:** por primera vez **`ant compile-test` FALLA** (4 errores en `test/`). La suite de JUnit **no puede ejecutarse**. Varios arreglos de esta iteración introdujeron una **regresión semántica** (el booleano "impreso directo" ahora también es `true` cuando en realidad se degradó al visor) y dos **pruebas rotas** por cambios de firma/borrado de clase. Detalle en §4 (N13, N15, N16).

---

## 0. Qué cambió el usuario en esta iteración (verificado en diff)

| Punto | Cambio realizado | Ubicación |
|---|---|---|
| 7 | `resolverNombreRealImpresora` ahora **devuelve `null`** si no hay coincidencia (antes devolvía el nombre inexistente) | `ServicioImpresionTicket.java:143` |
| 6 / N9 | `procesarSalida` **propaga** el booleano de `abrirVisor` en las degradaciones y en `VISOR_PDF` | `:238,246,250-252` |
| 6 / N9 | `imprimirTicketPrueba` en `TERMICA_DIRECTA` **devuelve** el resultado de `abrirVisor` al fallar | `:531-532` |
| 11 | Los `catch (Exception ignored)` ahora **registran a `FINE`** (siguen sin re-lanzar) | `:76,162,189` |
| 5 / N12 | `PedidoPdfServicio.generar`/`reimprimir` → `boolean`; `PedidosControlador` lo propaga; la UI lo consume | `PedidoPdfServicio.java:47,76,100`, `PedidosControlador.java:51,65` |
| 5 / N12 | `FinalizarPedidoSwingWorker` devuelve `ResultadoFinalizacion(finalizado, impresoDirecto)` y la UI avisa si no hubo impresión directa | `FinalizarPedidoSwingWorker.java:60-73`, `Sistema.java:2674-2691` |
| A3 | **`src/Modelo/DatosTicketFiscal.java` eliminado** (clase muerta) | `git status: D src/Modelo/DatosTicketFiscal.java` |

Sin cambios en esta iteración: `P9` (`.env`), `P10` (`ACCION_IMPRESION`), `P8`/`N5` (`-Wait` y filtro de impresoras virtuales), `P3` (offline), `P13` (80 mm).

---

## 1. Resumen ejecutivo

De las 17 faltas (14 + A1–A3): **8 RESUELTAS**, **5 PARCIALES**, **4 SIGUEN VIGENTES**.
De los hallazgos: **N1, N2, N3, N4, N6, N8, N11 RESUELTOS**; **N5, N9 PARCIALES**; **N10 VIGENTE**; **N12 RESUELTO en estructura pero contaminado por N13**; **N13, N15, N16 NUEVOS (todos ALTA)**.

**Lo resuelto con evidencia:**
- **Punto 7:** `resolverNombreRealImpresora` retorna `null` si no encuentra la cola (`:143`); el test se actualizó a `assertNull`. Ya no se fabrica un nombre inexistente.
- **Punto 11 (mejora):** los 3 `catch (Exception ignored)` ahora loguean `Level.FINE` (`:76,162,189`), dejando rastro en depuración.
- **N12 (estructura):** el `boolean` de `abridor.abrir` ya viaja `PedidoPdfServicio → PedidosControlador → UI` (`FinalizarPedidoSwingWorker:68` → `Sistema.java:2685`; reimpresión `426,2192,3667`). Se eliminó el descarte denunciado en iter. 3.
- **A3 (en `src`):** la clase muerta `DatosTicketFiscal` fue borrada.

**Lo que sigue abierto y lo nuevo (lo importante):**
- **N13 (NUEVO — ALTA): falso éxito por conflación semántica.** Al devolver `abrirVisor(...)`, `procesarSalida` retorna `true` cuando la impresión térmica **falló pero el visor se abrió**. Ese `true` se interpreta como "impreso directamente" en toda la UI → el caso *degradado al visor* se reporta como **éxito** y el aviso de degradación es inalcanzable en escritorio normal.
- **N15 (NUEVO — ALTA):** se borró `src/Modelo/DatosTicketFiscal.java` pero `test/Modelo/Java21RecordPatternMatchingTest.java:44-45` aún lo referencia → **`ant compile-test` falla** (`cannot find symbol`).
- **N16 (NUEVO — ALTA):** `test/Vista/FinalizarPedidoSwingWorkerTest.java:51,71` asigna un `ResultadoFinalizacion` a un `AtomicReference<Boolean>` → **no compila** (`ResultadoFinalizacion cannot be converted to Boolean`).
- **P9, P10, P3, P13** sin cambios; **P8/N5** (`-Wait` en `:401,428`) sin cambios.

Evidencia: `ant clean jar` → **BUILD SUCCESSFUL**; `ant -q compile-test` → **BUILD FAILED (4 errores)** → suite JUnit **no ejecutable** en esta iteración.

---

## 2. Tabla de veredictos (faltas)

| # | Falta | Iter. 3 | **Ahora (iter. 4)** |
|---|---|---|---|
| 1 | No usaba la impresora configurada de la BD | RESUELTO | **RESUELTO** |
| 2 | Falso éxito / cascada engañosa | RESUELTO | **RESUELTO** (residual P3) — pero aparece N13 |
| 3 | Impresora offline no detectada | VIGENTE | **SIGUE VIGENTE** |
| 4 | Ticket de prueba falseaba éxito | RESUELTO | **RESUELTO** (residual N13 en la rama visor) |
| 5 | Cierre no señalaba fallo | RESUELTO | **PARCIAL — regresión por N13** (la señal existe pero informa éxito cuando se degradó) |
| 6 | `abrirVisor` no falla en headless | PARCIAL | **PARCIAL** (se propaga en `procesarSalida`; `imprimirTicketPrueba` visor aún lo descarta) |
| 7 | `resolverNombreRealImpresora` devuelve nombre inexistente | VIGENTE | **RESUELTO** (residual: `null` → predeterminada silenciosa) |
| 8 | 7 s sin `destroy()` / sin timeout global | PARCIAL | **PARCIAL** (`-Wait` sigue; `:401,428`) |
| 9 | `.env` sólo relativo | VIGENTE | **SIGUE VIGENTE** |
| 10 | `ACCION_IMPRESION` nunca se respeta | VIGENTE | **SIGUE VIGENTE** |
| 11 | Catches y agujeros silenciosos | PARCIAL | **PARCIAL** (ahora loguean a `FINE`, pero siguen tragando) |
| 12 | Rama `lp` fija/irrelevante | RESUELTO | **RESUELTO** |
| 13 | Tamaño físico no validado (80 mm) | VIGENTE | **SIGUE VIGENTE** (no verificable sin hardware) |
| 14 | Impresora no determinística en Windows | RESUELTO | **RESUELTO** (con `lib/` resuelto) |
| A1 | Combo de impresora vs. reimpresión | RESUELTO | **RESUELTO** |
| A2 | Sin timeouts en `Desktop.print` | RESUELTO | **RESUELTO** (`Desktop.print` eliminado) |
| A3 | `DatosTicketFiscal` muerto | VIGENTE | **PARCIAL — regresión** (borrado en `src`, pero rompe el test → no compila; N15) |

### Tabla de hallazgos

| # | Hallazgo | Iter. 3 | **Ahora (iter. 4)** |
|---|---|---|---|
| N1 | Manifest sin PDFBox | RESUELTO | **RESUELTO** |
| N2 | `.bat` sin PDFBox | RESUELTO | **RESUELTO** |
| N3 | Raza a "DEFAULT" en el combo | RESUELTO | **RESUELTO** |
| N4 | `catch (Throwable)` | RESUELTO | **RESUELTO** |
| N5 | `job.print()` a impresora virtual bloquea | PARCIAL | **PARCIAL** (guarda solo en spooler; PowerShell sin filtrar) |
| N6 | Bloqueo del EDT en prueba | RESUELTO | **RESUELTO** |
| N8 | Linux no usaba spooler / `lp` sin `-d` | RESUELTO | **RESUELTO** |
| N9 | Doble efecto en prueba fallida | VIGENTE | **PARCIAL** (térmica devuelve visor; rama `VISOR_PDF` sigue `return true`) |
| N10 | Cambio de combo "vivo" sin guardar | VIGENTE | **SIGUE VIGENTE** |
| N11 | `dist/` sin `lib/` | RESUELTO | **RESUELTO** |
| N12 | `PedidoPdfServicio` descarta el retorno | NUEVO | **RESUELTO en estructura** (pero el valor hereda N13) |
| N13 | Conflación "impreso directo" vs "visor abierto" | — | **NUEVO (ALTA)** |
| N15 | Test referencia `DatosTicketFiscal` borrado | — | **NUEVO (ALTA)** |
| N16 | Test no adaptado a `ResultadoFinalizacion` | — | **NUEVO (ALTA)** |

---

## 3. Análisis punto por punto (código actual)

### Punto 5 — Cierre no señalaba fallo → **PARCIAL (regresión por N13)**
- El mecanismo existe: `CierreCajaServicio.imprimirCierre` captura `exito = impresor.abrir(archivo)` (`:88`) y retorna `ResultadoCierre(archivo, exito)` (`:92`); la UI decide con `resultado.impresoDirecto()` (`Sistema.java:4148`).
- **Pero** `impresor` es `ServicioImpresionTicket::procesarSalida`, que ahora devuelve `abrirVisor(...)` en las degradaciones → con un visor funcional `exito == true` **aunque no se imprimió**. La UI muestra *"enviado a la impresora térmica (…)"* en lugar de *"degradado a visor PDF"*. El defecto original reaparece invertido (N13).

### Punto 6 — `abrirVisor` en headless → **PARCIAL**
- `abrirVisor` retorna `false` en headless (`:481-490`) y ahora `procesarSalida` **sí propaga** el retorno (`:238,246,250-252`). Buena parte del punto queda cubierta.
- **Pero** `imprimirTicketPrueba` en la rama `else` (modo `VISOR_PDF`) sigue con `abrirVisor(tempTicket); return true;` (`:534-536`), **descartando** el resultado. Inconsistente con la rama térmica.

### Punto 7 — `resolverNombreRealImpresora` → **RESUELTO (residual)**
- Sin coincidencia retorna `null` (`:143`); el test ahora `assertNull` (`ServicioImpresionTicketTest.java:127-130`).
- **Residual:** `null` se pasa a `imprimirEnWindows`, donde `buscarPrintService(null)` cae a `PrintServiceLookup.lookupDefaultPrintService()` (`:322-325`) y la guarda de PowerShell lo omite (`:398`). Es decir, si el nombre configurado no existe, **se imprime en la predeterminada del SO sin avisar**. Ya no engaña con un nombre falso, pero el desvío es silencioso.

### Punto 8 — PowerShell → **PARCIAL**
- `destroyForcibly()` en todos los timeouts (ya en iter. 3).
- **Pero** `Start-Process ... -Wait` sigue (`:401,428`); `waitFor(7s)` limita a `powershell`, no al hijo `Start-Process`.

### Punto 9 — `.env` → **SIGUE VIGENTE**
- Default relativo `.env`/`..\.env` con override `-Drestaurante.env` (`:61-64`); sin override, arrancar desde otro cwd no encuentra el archivo.

### Punto 10 — `ACCION_IMPRESION` → **SIGUE VIGENTE**
- `obtenerAccionConfigurada()` (`:104-117`) no se invoca en ningún flujo productivo (sólo tests).

### Punto 11 — Catches silenciosos → **PARCIAL**
- Los 3 `catch (Exception ignored)` ahora hacen `LOGGER.log(Level.FINE, …)` (`:76,162,189`). Siguen **tragando** la excepción (no re-lanzan), pero dejan rastro en depuración. Mejora real, no cierre total.

### Punto 12 — Rama `lp` → **RESUELTO** · **Punto 13** → **VIGENTE (no verificable)** · **Punto 14** → **RESUELTO** · **A1/A2** → **RESUELTOS**.

### A3 — `DatosTicketFiscal` → **PARCIAL (regresión)**
- La clase `src/Modelo/DatosTicketFiscal.java` fue **borrada** (correcto: era muerta en producción).
- **Pero** `test/Modelo/Java21RecordPatternMatchingTest.java:44-45` todavía la instancia → `ant compile-test` falla (**N15**).

---

## 4. Hallazgos

### N13 (NUEVO — ALTA) — Falso éxito por conflación "impreso directo" / "visor abierto"
En `procesarSalida` las tres degradaciones devuelven el resultado de `abrirVisor`, y `VISOR_PDF` también:

```java
case TERMICA_DIRECTA -> { … return abrirVisor(archivoPdf); }   // :238
case PDF24_CREATOR   -> { … return abrirVisor(archivoPdf); }   // :246
case VISOR_PDF       -> { return abrirVisor(archivoPdf); }     // :250-252
```

El contrato documentado es *"true si se imprimió o despachó directamente; false si se degradó al visor"* (`:215-216`), pero el código ahora devuelve `true` cuando **se degradó y el visor abrió**. Consecuencias en la UI, todas con mensaje invertido en el caso *fallo térmico + visor OK* (entorno de escritorio normal):
- **Finalizar pedido:** `Sistema.java:2685` `if (impresoDirecto)` → muestra *"Pedido finalizado y PDF generado…"* (éxito) en vez de la advertencia de degradación.
- **Cierre de caja:** `Sistema.java:4148` → *"enviado a la impresora térmica (…)"* en vez de *"degradado a visor PDF"*.
- **Reimpresión / ticket (P5/P6/N9/N12):** el aviso `if (!impreso) "degradado a visor PDF"` (`Sistema.java:426,2192,3667`) es **inalcanzable** siempre que `Desktop.open` funcione, y cuando por fin se muestra (visión headless) el texto es falso, porque el visor *no* abrió.
- **Ticket de prueba:** `imprimirTicketPrueba` `TERMICA` devuelve `abrirVisor` (`:531-532`) → un fallo de impresión con visor OK reporta *éxito* en la UI.

**Fix sugerido:** separar las dos semánticas. `procesarSalida` debe seguir retornando `false` en toda degradación (para no perder la señal de "no se imprimió directo"); que `abrirVisor` comunique su propio éxito por otra vía (estado distinto, p. ej. enum `ResultadoSalida{IMPRESO, VISOR_OK, FALLIDO}`). Revisar los tres consumidores (`ResultadoCierre.impresoDirecto`, `ResultadoFinalizacion.impresoDirecto`, callbacks de reimpresión).

### N9 (PARCIAL) — Doble efecto / inconsistencia en la prueba
- Rama térmica: `if (!ok) return abrirVisor(tempTicket);` (`:531-532`). Ya no hay "error aunque viste el ticket", pero cae en N13 (reporta éxito).
- Rama `VISOR_PDF` (`:534-536`): `abrirVisor(tempTicket); return true;` sigue **ignorando** el resultado; en headless reporta éxito sin abrir nada.

### N12 (RESUELTO en estructura) — pero hereda N13
El `boolean` ya se propaga (`PedidoPdfServicio.generar/reimprimir` → `PedidosControlador.generarPdfPedido/reimprimirPdfPedido` → `Sistema.java:426,2192,3667` y `FinalizarPedidoSwingWorker:68` → `Sistema.java:2685`). El descarte de iter. 3 está corregido. **No obstante**, como el valor es `true` en las degradaciones (N13), los nuevos avisos de degradación no se disparan en el caso real.

### N15 (NUEVO — ALTA) — Test referencia clase borrada
`test/Modelo/Java21RecordPatternMatchingTest.java:44-45` usa `DatosTicketFiscal`, borrado de `src/`. `ant compile-test` falla: `cannot find symbol: class DatosTicketFiscal`. **Fix:** eliminar el test `testDatosTicketFiscalRecord` (o restaurar la clase si se desea conservar el record).

### N16 (NUEVO — ALTA) — Test no adaptado a `ResultadoFinalizacion`
`test/Vista/FinalizarPedidoSwingWorkerTest.java:51,71` hace `finalizado.set(resultado)` / `assertTrue(finalizado.get())` con `AtomicReference<Boolean>`, pero el `SwingWorker` ahora emite `ResultadoFinalizacion`. Falla: `incompatible types: ResultadoFinalizacion cannot be converted to Boolean`. **Fix:** tipar los `AtomicReference`/`Consumer` como `FinalizarPedidoSwingWorker.ResultadoFinalizacion` y asertar `resultado.finalizado()` / `resultado.impresoDirecto()`.

### N5 (PARCIAL) — sin cambios
`imprimirDirectoJavaSpooler` aborta con impresoras virtuales (`:363-367`), pero `PrintTo` (`:401`) y `Print` (`:428`) de PowerShell no filtran virtuales y mantienen `-Wait`.

### N10 (VIGENTE) — sin cambios
`setImpresoraGlobal` en el listener del combo (`Sistema.java:595`) muta el runtime aunque el usuario cancele.

---

## 5. Evidencia de esta iteración

- `ant -q clean jar` → **BUILD SUCCESSFUL** (compila `src/`, incluye el borrado de `DatosTicketFiscal`).
- `ant -q compile-test` → **BUILD FAILED — 4 errores**:
  1. `test/Modelo/Java21RecordPatternMatchingTest.java:45` — `cannot find symbol: class DatosTicketFiscal` (×2).
  2. `test/Vista/FinalizarPedidoSwingWorkerTest.java:51` — `incompatible types: ResultadoFinalizacion cannot be converted to Boolean`.
  3. `test/Vista/FinalizarPedidoSwingWorkerTest.java:71` — idem.
- Por el fallo anterior, **no se ejecutó la suite JUnit** esta iteración (contraste con iter. 3, que compilaba y pasaba 7/5/6/7).
- Base de P6/N9: `ServicioImpresionTicket.java:531-536` (térmica propaga, visor descarta) y `:238,246,250-252` (conflación).
- Base de N12: `PedidoPdfServicio.java:47,76,100`, `PedidosControlador.java:51,65`, `FinalizarPedidoSwingWorker.java:60-73`, `Sistema.java:2685,4148`.

---

## 6. Prioridad de corrección sugerida

1. **N13 (ALTA):** desacoplar "impreso directo" de "visor abierto". Devolver `false` cuando hubo degradación y exponer el éxito del visor por separado; revisar `ResultadoCierre`, `ResultadoFinalizacion` y los avisos de reimpresión.
2. **N15 + N16 (ALTA):** restaurar la compilación de tests (quitar `testDatosTicketFiscalRecord`; adaptar `FinalizarPedidoSwingWorkerTest` al record `ResultadoFinalizacion`). Sin esto **no hay verificación automatizable**.
3. **P6/N9 (MEDIA):** en `imprimirTicketPrueba`, la rama `VISOR_PDF` debe consumir y devolver el resultado de `abrirVisor` (no `return true` fijo).
4. **N5/P8 (MEDIA):** extender la guarda de impresora virtual al `PrintTo`/`Print` de PowerShell y acotar/eliminar `-Wait` (limitar por PID).
5. **P3 (MEDIA):** detección real de fallo de cola (`JobStateReason`/estado) o degradación con señal explícita; hoy `exitValue()==0` sólo mide la invocación.
6. **P7 residual (BAJA):** avisar/elegir explícitamente cuando el nombre configurado no existe (hoy cae en silencio a la predeterminada).
7. **P9, P10, P13, N10 (BAJA):** `.env` por ruta absoluta/override en lanzadores; usar o eliminar `ACCION_IMPRESION`; validar 80 mm con hardware; no mutar el global del combo hasta guardar.

---

## Anexo — Líneas clave verificadas

`ServicioImpresionTicket.java:` `cargarConfiguracionEnv 58-80 (override -Drestaurante.env 61-64)`, `obtenerImpresoraConfigurada 86-99`, `obtenerAccionConfigurada 104-117`, `resolverNombreRealImpresora 123-143 (return null 143)`, `procesarSalida 208 / 217-257 (TERMICA 229-237, PDF24 238-245, VISOR_PDF 250-252)`, `buscarPrintService 322-343 (null→default 323-325)`, `imprimirDirectoJavaSpooler 351-383 (guarda virtual 363-367, catch Exception|LinkageError 378)`, `imprimirEnWindows 385-478 (PrintTo 401, Print 428, lp -d 451-475)`, `abrirVisor 481-490`, `imprimirTicketPrueba 494-549 (térmica 528-533, PDF24 534, visor 535-536)`.
`catch (Exception ignored) → LOGGER.log(FINE)`: `:76,162,189`.
`PedidoPdfServicio.java:` `generar→boolean 47`, `reimprimir→boolean 76`, `abridor.abrir 100`, `AbridorPdf 159-163`.
`CierreCajaServicio.java:` `ResultadoCierre 51`, `impresor.abrir 88-92`, `visor.abrir 135-139`.
`PedidosControlador.java:` `generarPdfPedido 49-51`, `reimprimirPdfPedido 58-72`.
`FinalizarPedidoSwingWorker.java:` `ResultadoFinalizacion 73`, `impresoDirecto 68`.
`Sistema.java:` `finalizar pedido 2674-2691 (mensaje 2685)`, `reimpresión 426,2192,3667`, `cierre ResultadoCierre 4148`.
Tests: `ServicioImpresionTicketTest.java:127-130`, `Java21RecordPatternMatchingTest.java:44-45`, `FinalizarPedidoSwingWorkerTest.java:51,71`.
