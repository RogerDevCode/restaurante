# Contexto del proyecto — Restaurante 2026

Documento de contexto para asistentes LLM y onboarding humano. Describe qué es el
sistema, su arquitectura, cómo compilarlo/ejecutarlo, el estado del repositorio y la
deuda técnica conocida.

---

## 1. Propósito

Aplicación de escritorio **Java Swing** para la gestión integral de un restaurante:
salas y mesas, pedidos, platos y categorías, usuarios/roles, cierre de caja,
dashboard de estadísticas y facturación térmica (tickets de 80 mm). Persistencia en
**MySQL**. Sustituye a una versión previa y está en fase de producción/migración a
Java 21 LTS.

---

## 2. Stack técnico

- **Lenguaje / JDK:** Java 21 (Corretto 21.0.12.1). Compilado con `source/target = 21`
  y `javac.compilerargs=-proc:none`.
- **UI:** Java Swing + **AbsoluteLayout** (`org.netbeans.lib.awtextra`) para el layout de
  la ventana principal. Look & Feel del sistema (`UIManager.getSystemLookAndFeelClassName()`),
  con overrides de `UIManager` para diálogos e inputs.
- **Persistencia:** JDBC directo, **sin ORM**, con MySQL Connector/J 8.0.31.
- **PDF / impresión:** iTextPDF 5.5.1 + PDFBox/FontBox 2.0.31 (tickets y reportes).
- **Build:** Apache **Ant** (`build.xml`, `nbproject/`).
- **Testing:** JUnit 4.13.2 + Hamcrest. Tests unitarios en `test/` e integración MySQL en
  `test/integracion/` contra Docker (puerto 3307).

Dependencias en `librerias/` y empaquetadas en `dist/lib/`:
`AbsoluteLayout.jar`, `itextpdf-5.5.1.jar`, `mysql-connector-j-8.0.31.jar`,
`pdfbox-2.0.31.jar`, `fontbox-2.0.31.jar`, `commons-logging-1.2.jar`.

---

## 3. Arquitectura por capas

Paquetes bajo `src/`, con el flujo **Vista → Controlador → Servicio → DAO/Repositorio → MySQL**:

- `restaurante/` — **entrypoint**. `Restaurante.main` verifica instancia única (ServerSocket
  49200), configura LAF y `UIManager`, ejecuta la migración de esquema
  (`MigradorEsquemaJdbc`), lanza el backup diario en un hilo virtual y arma el árbol de
  dependencias a mano antes de mostrar `FrmLogin`. `SimularUsuario.java` es soporte de pruebas.
- `Vista/` — UI Swing. `Sistema.java` (~7.159 líneas) es la ventana principal con un
  `JTabbedPane` (Salas, Mesas, Platos, Finalizar Pedido, Historial, Empresa, Usuarios,
  Dashboard, Categorías). `FrmLogin.java` es el login. Los `*SwingWorker.java` cargan datos
  de forma asíncrona (salas, platos, pedidos, dashboard, finalizar). `Eventos.java` y
  `RefrescoVistas.java` implementan un bus Observer para refrescar vistas tras cambios en BD.
  `Tables.java` contiene renderers de tabla y `ManejadorErroresSwing.java` centraliza errores.
- `Controlador/` — controladores finos que delegan en servicios: `LoginControlador`,
  `PedidosControlador`, `PlatosControlador`, `SalasControlador`, `CategoriaControlador`,
  `FavoritoControlador`.
- `Servicio/` — lógica de negocio y casos de uso: `PedidoServicio`, `SalasServicio`,
  `PlatosServicio`, `CategoriaServicio`, `FavoritoServicio`, `AutenticacionServicio`,
  `CierreCajaServicio`, `ConsultaPedidosServicio`, `ConcurrenciaServicio`, `PoliticaAcceso`,
  `ResultadoOperacion`, generadores de PDF/impresión (`PedidoPdfServicio`, `GeneradorPdfPedido`,
  `GeneradorPdfCierre`, `ServicioImpresionTicket`), `ServicioRespaldoBaseDatos`,
  `ServicioMesoneroNombre`.
- `Modelo/` — entidades y acceso a datos: entidades (`Pedidos`, `DetallePedido`, `Platos`,
  `Salas`, `Categoria`, `Cliente`, `Mesonero`, `Usuario`, `Config`, `CierreCaja`,
  `AuditoriaPedido`, `EstadisticasDashboard`) + **DAO** (`*Dao`) + **repositorios**
  (`*Repositorio`) + `Conexion` + records auxiliares (`CalculoFiscalRecord`,
  `CotizacionMonedaRecord`) + excepciones de dominio (`DataAccessException`,
  `ErrorAplicacionException`, `PedidoPendienteExistenteException`).
- `infraestructura/` — `ProveedorConexionJdbc` (pool ligero con proxy, timeouts
  `connect/socket`, TLS configurable, lectura de `.env`), `MigradorEsquemaJdbc`,
  `PasswordHasher`, `ConfiguracionLogs`, `ArchivoLogDiario`.

`PoliticaAcceso` aplica las reglas por rol del usuario autenticado.

---

## 4. Dominio y base de datos

MySQL 8.4.11 en Docker (`docker-compose.yml`), volumen `restaurante_mysql_data`, con
`BD.sql` montado como script de inicialización. `actualizar_bd.sql` contiene migraciones
idempotentes (`CREATE TABLE IF NOT EXISTS`).

Tablas: `usuarios`, `salas`, `platos`, `pedidos`, `detalle_pedidos`, `clientes`, `config`,
`configuracion_sistema`, `auditoria_pedidos`, `cierres_caja`, `mesoneros`.

Constraint relevante: `uq_pedidos_mesa_pendiente` — una mesa no puede tener dos pedidos
pendientes simultáneos.

### Marchas parciales de cocina

La migración v15 agrega `comandas_marchas` y `comandas_marchas_detalle` como diario
inmutable de lo enviado a cocina. Al crear una cuenta, se guarda la marcha inicial si
la salida está habilitada. Al agregar productos, la siguiente marcha contiene solo el
incremento de cantidad de cada plato/comentario que aún no se ha enviado; precio e
impuestos no disparan una preparación nueva. Reducir una cantidad o cambiar una nota
de preparación produce movimientos `RETIRAR` y `PREPARAR` en un comprobante de ajuste.
Si el operador desactiva la impresión, los cambios se guardan y se acumulan para la
siguiente marcha emitida. Los pedidos pendientes existentes reciben una marcha
`BASE_LEGADA` no imprimible, para impedir que se reenvíe su detalle completo tras
actualizar la aplicación. Los dos flujos Swing imprimen el snapshot transaccional de
la marcha devuelta por `PedidosDao`; no reconstruyen la comanda desde los detalles
acumulados. La consulta por ID permite recuperar una marcha exacta; el historial permite seleccionarla y reimprimirla con auditoría.

---

## 5. Build y ejecución

```bash
# Compilar y empaquetar (genera dist/Restaurante.jar y dist/lib/)
ant -q compile && ant -q jar

# Desplegar el jar en el paquete ejecutable
cp -f dist/Restaurante.jar Restaurante_Ejecutable/Restaurante.jar

# Ejecutar (entorno gráfico)
cd Restaurante_Ejecutable && DISPLAY=:0.0 java -jar Restaurante.jar
```

Flujo de arranque: único proceso → LAF → migración de esquema → backup diario →
`FrmLogin` → al autenticar se construye `Sistema`. Credenciales demo del login:
**admin / admin**. Existe además el modo `java -jar Restaurante.jar --migrate-db` para
validar la migración de esquema y salir.

Scripts de apoyo: `iniciar_restaurante.sh`, `empaquetar_produccion.sh`,
`generar_restaurante_ejecutable.sh`.

Tests de integración: `ant integration-test` (levanta `docker-compose.integration.yml` en
`127.0.0.1:3307` con usuario `restaurante_test_app`; aborta si la URL/credenciales no son
las fijas de `restaurante_test`).

---

## 6. Configuración

`.env` (presente en la raíz y en `Restaurante_Ejecutable/`):

```
MYSQL_DATABASE=restaurante
MYSQL_USER=admin
MYSQL_PASSWORD=admin
MYSQL_ROOT_PASSWORD=admin
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306

PDF_FORMATO=80MM
IMPRESORA_TICKETS=XP-80T
ACCION_IMPRESION=IMPRIMIR
```

`ProveedorConexionJdbc` resuelve cada clave por prioridad: **system property → variable de
entorno → `.env`** (con fallback a `../.env`). Acepta alias de variables (`DB_NAME`,
`DB_USER`, `DB_PASSWORD`/`DB_PASS`, `DB_HOST`, `DB_URL`) y opciones de pool/timeouts
(`DB_POOL_ENABLED`, `DB_POOL_MAX_SIZE`, `DB_CONNECT_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_MS`,
`DB_SSL`/`MYSQL_SSL`).

---

## 7. Estado del repositorio

- Rama: `main`.
- Commits recientes centrados en UX: cabeceras de panel con color, botones contextuales
  "volver", logo CAPIP Sistemas + botón Inicio, mejora de diálogos/inputs.
- **Modificados sin commitear:** `src/Vista/FrmLogin.java`, `src/Vista/Sistema.java`,
  `src/restaurante/Restaurante.java`.
- **Nuevos sin trackear:** `src/Vista/RefrescoVistas.java` (bus de refresco) y varios
  archivos scratch de prueba en la raíz (`TestSistema.java`, `TestTabbedPane.java/.class`,
  `test_tab*.java/.class`, `debug.log`, `target.txt`).

---

## 8. Trabajo reciente

Mejoras de layout en `Sistema.java`:

- Ventana **maximizada y responsiva** (`configurarVentanaResponsiva` / `reacomodarComponentes`,
  constantes `ANCHO_BARRA_LATERAL=150`, `ALTO_CABECERA=90`).
- Cada pestaña envuelta en un `JScrollPane` cuya vista es `PanelScrollable`
  (`implements Scrollable`): rellena el viewport cuando hay espacio y muestra scroll H/V
  cuando el contenido es mayor.
- **Todas las tablas** con columnas redimensionables y scroll H/V
  (`configurarTablaIndividual` / `configurarTodasLasTablas`).
- **Texto de la barra lateral centrado** (`centrarTextoBarraLateral`) y helper
  `iconoBarra` que escala todos los iconos del menú a 32×32. Causa raíz del descuadre:
  `btnInicio` usaba `/Img/salas.png` (128×128, casi negro) y forzaba el ancho mínimo de la
  columna del layout a 200 px dentro de una barra de 150 px.
- Botones de peligro unificados al color `#C56B62`.
- Bus `RefrescoVistas` para refrescar vistas tras escrituras exitosas en BD.

---

## 9. Deuda técnica / notas conocidas

- **Bug abierto:** en `Sistema.btnModificarPedidoActionPerformed`, `LimpiarTableMenu()`
  resetea `idPedidoEdicion = -1`, lo que rompe el flujo "Agregar Productos" al reintentar
  agregar a un pedido pendiente (dispara `PedidoPendienteExistenteException` por la
  constraint `uq_pedidos_mesa_pendiente`). `PedidosDao.actualizarPedidoCompleto` es correcto.
- `Sistema.java` es un archivo monolítico (~7.159 líneas): candidato a refactor por pestañas.
- Documentos de planificación en la raíz: `plan.md`, `plan_reparacion.md`,
  `MIGRATION_PLAN_JAVA_21.md`, `plan_multihibrido_cantidades.md`, `QA_Adversarial_Report.md`,
  `reporte.md` (este último enfocado en el subsistema de impresión de tickera).

---

## 10. Convenciones

- Código y nombres en **español**; paquetes con mayúscula inicial
  (`Vista`, `Modelo`, `Servicio`, `Controlador`, `infraestructura`).
- Programación defensiva: los constructores validan dependencias nulas y lanzan
  `ErrorAplicacionException`.
- Cargas de datos desde la UI mediante `SwingWorker` para no bloquear el EDT.
- El logging de la aplicación se escribe en un archivo diario (`ArchivoLogDiario` /
  `ConfiguracionLogs`).
