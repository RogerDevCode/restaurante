# Prompt de Contexto — Restaurante 2026
> Documento de onboarding ultrarrápido para LLMs. Leer esto en lugar de releer todo el repo.
> Actualizado: 2026-11-01 (Plan Maestro Fiscal v0.3)

---

## ¿Qué es este sistema?
Aplicación de escritorio **Java 21 + Swing + MySQL 8.4** para gestión integral de restaurante: salas/mesas, pedidos, menú, usuarios/roles, facturación térmica 80 mm, cierre de caja Z, dashboard. Se ejecuta como proceso único (port lock 49200). Producción activa. Repo en `/home/manager/Sync/java_proyects/RETAURANTE2026/Restaurante/`.

---

## Stack en 30 segundos

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 Corretto, `source/target=21`, `javac.compilerargs=-proc:none` |
| UI | Java Swing + AbsoluteLayout (NetBeans) |
| BD | MySQL 8.4.11 Docker, JDBC directo sin ORM, Connector/J 8.0.31 |
| PDF | iTextPDF 5.5.1 (incluye `BarcodeQRCode` nativo) + PDFBox 2.0.31 |
| Build | Apache Ant (`ant -q compile jar`) |
| Tests | JUnit 4.13.2, Docker port 3307 para integración |
| Librerías | `librerias/` → empaquetadas en `dist/lib/` |

---

## Arquitectura de capas (paquetes bajo `src/`)

```
Vista/          → Swing UI: Sistema.java (~7159 líneas, ventana principal con JTabbedPane),
                  FrmLogin, *SwingWorker (carga asíncrona), DialogoCobroPedido,
                  Eventos.java + RefrescoVistas.java (bus Observer), Tables.java, ManejadorErroresSwing
Controlador/    → Finos delegadores: LoginControlador, PedidosControlador, PlatosControlador,
                  SalasControlador, CategoriaControlador, FavoritoControlador
Servicio/       → Negocio: PedidoServicio, CierreCajaServicio, ConsultaPedidosServicio,
                  AutenticacionServicio, GeneradorPdfPedido, GeneradorPdfCierre,
                  ServicioImpresionTicket, ServicioComandaCocina, PoliticaAcceso
Modelo/         → Entidades (Pedidos, DetallePedido, Platos, Salas, Usuario, CierreCaja…)
                  + DAO (*Dao) + Repositorios (*Repositorio)
                  + Records: CalculoFiscalRecord, CotizacionMonedaRecord, PagoPedido, IgtfRecord (v18)
                  + Excepciones: DataAccessException, ErrorAplicacionException
infraestructura/→ ProveedorConexionJdbc (pool + .env), MigradorEsquemaJdbc,
                  PasswordHasher, ConfiguracionLogs, ArchivoLogDiario,
                  seguridad/ServicioLedgerFiscal, impresion/*, api/ServidorRestTablets
restaurante/    → Entrypoint: Restaurante.main (migración esquema → backup diario → daemon fiscal → FrmLogin)
```

---

## Flujo de cobro (CRÍTICO para adecuación fiscal SNAT/2026/00093)

```
DialogoCobroPedido (EDT)
  → SwingWorker.doInBackground() [HILO DE FONDO - NUNCA EL EDT]
      → PedidosControlador.cobrarPedido()
          → PedidosDao.cobrarPedido() [TRANSACCIÓN JDBC ATÓMICA]
              → UPDATE pedidos SET estado='FINALIZADO', estado_fiscal='PENDIENTE_TRANSMISION'
              → INSERT pedido_pagos (con igtf_* si aplica)
              → ServicioLedgerFiscal.registrarFactura()   ← sella bloque inmutable FAC-X
              → COMMIT
      → ServicioTimbradoFiscal.timbrarFactura() [HTTP REST al PTA, timeout 3000ms]
          → [Éxito < 3s]  → pedidosDao.actualizarEstadoFiscal('TRANSMITIDA', nroControl, sello, url)
          → [Falla / TO]  → pedidosDao.actualizarEstadoFiscal('CONTINGENCIA', ..., talonario)
      → PedidosControlador.generarPdfPedidoConResultado(idPedido) [Ticket con QR y N° Control]
  → SwingWorker.done() [DE VUELTA EN EDT]
      → Actualiza UI en pantalla y envía ticket al spooler físico
```

---

## Base de datos — estado actual

- **Esquema versión:** `VERSION_ESQUEMA = 17` (`MigradorEsquemaJdbc.java`)  
- **Migración v18:** definida en el Plan Maestro (facturación digital SENIAT, IGTF, Notas de Crédito)
- **Regla crítica:** `actualizar_bd.sql` (raíz) y `db/migrations/actualizar_bd.sql` **deben ser byte-idénticos** (lo verifica `InstaladoresSeguridadTest`)
- **Tablas relevantes:** `pedidos`, `detalle_pedidos`, `pedido_pagos`, `configuracion_sistema`, `cierres_caja`, `schema_migrations`, `ledger_fiscal`

### Métodos de pago (enum en `PagoPedido` y `PlanPagosCobro`)
`EFECTIVO_BS` | `EFECTIVO_USD` | `TRANSFERENCIA` | `TARJETA` | `PAGO_MOVIL` | `CORTESIA` | `MIXTO`
> **IGTF (3%):** Aplica a pagos en divisas (`EFECTIVO_USD`) si el restaurante está configurado como Sujeto Pasivo Especial (`fiscal_es_contribuyente_especial = true`).

---

## Configuración dinámica (`configuracion_sistema`)

Clave/valor en BD. Se accede con `ConfiguracionRepositorio` usando constantes de `ConfigClaves`.  
En v18 se agregan claves `fiscal_*` (URL PTA, Token, timeout, contribuyente especial, alícuota IGTF, rangos de talonarios de contingencia autorizados).

---

## Ledger fiscal inmutable (`ServicioLedgerFiscal`)

- Sella `FAC-{id_pedido}` **dentro de la transacción JDBC** antes del commit.
- Nuevo evento post-timbrado: `TIMBRADO_FISCAL` encadena el `numero_control` externo al bloque previo sin alterar la inmutabilidad.
- **Bug L1317 corregido en Fase 1:** PedidosDao.java pasaba `doc` en vez del RIF de la empresa en `rucEmpresa`.
- **Anulaciones:** Se sella evento `NOTA_CREDITO` vinculado al comprobante transmitido al PTA.

---

## Reglas de concurrencia (OBLIGATORIAS)

- **PROHIBIDO** bloquear el EDT con I/O, JDBC o HTTP.
- Operaciones de cobro, red y generación de archivos van en `SwingWorker.doInBackground()`.
- `SwingWorker.done()` → solo actualizar UI, nunca hacer red/BD directamente.
- Java 21 Virtual Threads para daemons en fondo (backup diario, `ServicioDaemonFiscal`).

---

## PDF, Altura de Ticket y Código QR

- `GeneradorPdfPedido` usa iText 5.5.1.
- `BarcodeQRCode` de iText es **nativo** — NO agregar ZXing.
- **Altura dinámica (`calcularAltoTicket80mm`):** Debe recalcularse sumando +85pt para el QR de 60pt y +65pt para IGTF, N° de Control y leyenda legal SNAT/2026/00093, evitando que el ticket se divida en 2 páginas o corte el QR.
- Tickets se guardan en `facturas/<AAAA-MM>/` — no son temporales.

---

## Cierre Z y Libro de Ventas Mensual

- `GeneradorPdfCierre` + `CierreCajaDao`: discrimina transmitidas vs. contingencia vs. notas de crédito, rango de N° Control e IGTF diario.
- `ServicioLibroVentasFiscal`: nuevo exportador mensual (CSV/PDF) con las 18 columnas reglamentarias del SENIAT (Reglamento IVA Art. 76).

---

## Mock de Pruebas para CI/CD

- `MockProveedorTecnologicoClient`: permite correr `ant test`, `ant integration-test` y `./e2e_virtual.sh` sin red ni contratos de PTA externos, simulando timbrado exitoso, contingencia y rechazos de forma determinista.

---

## Comandos de uso frecuente (desde raíz del repo)

```bash
# Compilar y desplegar
ant -q compile jar && cp -f dist/Restaurante.jar Restaurante_Ejecutable/Restaurante.jar

# Tests unitarios (BD 3307 desechable)
ant test -DDB_URL="jdbc:mysql://127.0.0.1:3307/restaurante_test" ...

# BD producción (no hay mysql CLI en host)
docker exec restaurante-db-1 mysql -uadmin -padmin restaurante -e "..."

# Arrancar app
systemd-run --user --collect --unit=restaurante-app --working-directory=<Restaurante_Ejecutable> \
  --setenv=DISPLAY=:0.0 java -jar Restaurante.jar
```

---

## Convenciones del código

- Idioma: **español** en código y nombres; paquetes con mayúscula inicial.
- DI por constructor; ensamblaje en `restaurante.Restaurante` (entrypoint).
- No instanciar DAOs/Servicios dentro de Vistas.
- Dinero: `BigDecimal` con `scale=2`, HALF_UP, sin subir decimales a BD para cuadrar.
- Transacciones JDBC: toda conexión pooled devuelta con autocommit=false debe confirmar o revertir explícitamente.
- Scripts `.bat`/`.ps1`/`.vbs` Windows: CRLF, ASCII puro sin BOM (`.bat`), UTF-8 con BOM (`.ps1`).
