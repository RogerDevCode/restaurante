# Restaurante — Documentación de Arquitectura y QA

> **Motor:** MySQL 8.4.11 (LTS) · **Compilador:** Java 11 (target 11) · **Runtime:** OpenJDK 17 Temurin · **Build:** Apache Ant 1.10.15 · **Tests:** JUnit 4.13.2

---

## Estado del Proyecto

| Indicador | Valor |
|:---|:---|
| Archivos de producción (`src/`) | **48 archivos Java** |
| Archivos de pruebas (`test/`) | **37 archivos Java** |
| Pruebas unitarias (`ant test`) | **91 pruebas, 36 suites · 0 fallos · 0 errores** |
| Pruebas de integración MySQL (`ant integration-test`) | **13 pruebas, 1 suite · 0 fallos · 0 errores** |
| Migraciones de base de datos | **3 scripts aplicados** |
| Rama principal | `main` — árbol limpio, sincronizado con `origin` |

---

## Arquitectura de Capas (MVC + Infraestructura)

```
src/
├── infraestructura/          Acceso a datos JDBC, logging, seguridad
├── Modelo/                   Dominio, entidades, excepciones, contratos
├── Servicio/                 Casos de uso, reglas de negocio, autorización
├── Controlador/              Coordinadores entre servicio y vista
├── Vista/                    Ventanas Swing + SwingWorkers asíncronos
└── restaurante/              Composition root (main)
```

### Capas y reglas de dependencia

| Capa | Puede depender de | No puede depender de |
|:---|:---|:---|
| `infraestructura` | Nada del proyecto | — |
| `Modelo` (dominio) | Nada del proyecto | Todo lo demás |
| `Servicio` | `Modelo`, contratos inyectables | `Vista`, `Controlador`, JDBC directo |
| `Controlador` | `Servicio`, `Modelo` | `Vista` (salvo tipos genéricos) |
| `Vista` | `Controlador`, `Modelo`, `SwingWorker`s | JDBC directo, `Servicio` directo |
| `restaurante.Restaurante` | Toda la aplicación | — (es el composition root) |

---

## Inventario de Archivos de Producción (48)

### `src/infraestructura/` — 4 archivos

| Archivo | Responsabilidad |
|:---|:---|
| `ProveedorConexionJdbc.java` | Abre conexiones JDBC leyendo `.env`, variables de entorno o propiedades de sistema. Soporta sobreescritura para pruebas aisladas. |
| `ConfiguracionLogs.java` | Instala el handler de logging antes de aceptar cualquier evento. Aborta el arranque si no puede inicializar el archivo diario. |
| `ArchivoLogDiario.java` | Rota archivos `logs/restaurante-AAAA-MM-DD.log` diariamente, purga por mes calendario (soporta años bisiestos y cambios de año), y mantiene un canal de emergencia durable ante fallos del handler. |
| `PasswordHasher.java` | PBKDF2-HMAC-SHA256 con 600 000 iteraciones y salt aleatorio de 16 bytes. Comparación en tiempo constante. Soporta migración transparente de contraseñas legacy. |

### `src/Modelo/` — 15 archivos

**Entidades de dominio:**

| Archivo | Responsabilidad |
|:---|:---|
| `Usuario.java` | Entidad de usuario autenticado (id, nombre, correo, rol). |
| `Config.java` | Bean de configuración empresarial (RUC, nombre, teléfono, dirección, mensaje). |
| `Platos.java` | Entidad de plato con precio en `BigDecimal`. |
| `Salas.java` | Entidad de sala con número de mesas. |
| `Pedidos.java` | Encabezado de pedido con total en `BigDecimal`, estado (`PENDIENTE` / `FINALIZADO`), sala y usuario. |
| `DetallePedido.java` | Línea de detalle con precio en `BigDecimal`, cantidad y comentario. |
| `login.java` | Fachada deprecada sobre `Usuario`; mantiene compatibilidad mientras se completa la migración a `Usuario`. |
| `Conexion.java` | Fachada compatible; delega la apertura JDBC a `ProveedorConexionJdbc`. |

**Excepciones de aplicación:**

| Archivo | Descripción |
|:---|:---|
| `ErrorAplicacionException.java` | Base para errores de aplicación. Distingue `WARNING` (validaciones, conflictos de negocio) de `SEVERE` (fallos técnicos). Registra exactamente una vez al crearse. |
| `DataAccessException.java` | Error técnico JDBC; preserva la `SQLException` como causa. |
| `PedidoPendienteExistenteException.java` | Conflicto de negocio `WARNING` cuando una mesa ya tiene un pedido pendiente. |

**Contratos de persistencia (interfaces inyectables):**

| Archivo | Operaciones principales |
|:---|:---|
| `AutenticacionRepositorio.java` | `autenticar()`, `autenticarUsuario()` |
| `PedidosRepositorio.java` | `registrarPedidoCompleto()`, `verPedido()`, `verPedidoDetalle()`, `listarPedidos()`, `actualizarEstado()` |
| `PlatosRepositorio.java` | `registrar()`, `listarPorFecha()`, `eliminar()`, `modificar()` |
| `SalasRepositorio.java` | `registrar()`, `listar()`, `eliminar()`, `modificar()` |

### `src/Modelo/` — DAOs JDBC — 4 archivos

| Archivo | Detalles técnicos clave |
|:---|:---|
| `LoginDao.java` | Autenticación con migración PBKDF2 en primer login; captura correo duplicado (MySQL 1062 → `WARNING`); gestiona datos de empresa; lista usuarios. |
| `PedidosDao.java` | Transacción atómica con rollback automático; ID recuperado con `getGeneratedKeys()`; detecta conflicto de mesa (`uq_pedidos_mesa_pendiente`); finalización y listado histórico. |
| `PlatosDao.java` | Filtro por `fecha` y nombre con parámetros SQL; `try-with-resources` en todas las operaciones. |
| `SalasDao.java` | CRUD completo; traduce restricción foránea (MySQL 1451) a mensaje de negocio explícito. |

### `src/Servicio/` — 8 archivos

| Archivo | Responsabilidad |
|:---|:---|
| `AutenticacionServicio.java` | Valida entradas y delega al repositorio; sin SQL ni Swing. |
| `PedidoServicio.java` | Valida y orquesta el registro completo de un pedido. |
| `PlatosServicio.java` | Valida y gestiona el catálogo de platos diarios. |
| `SalasServicio.java` | Valida y gestiona salas y número de mesas. |
| `ConsultaPedidosServicio.java` | Recupera pedidos, estados y detalles para la vista. |
| `GeneradorPdfPedido.java` | Ensambla el PDF con iText desde modelos; sin JDBC ni AWT. |
| `PedidoPdfServicio.java` | Coordina consulta, generación y apertura del PDF con funciones inyectables; propaga fallo del visor como `SEVERE`. |
| `PoliticaAcceso.java` | RBAC: `Administrador` tiene acceso total; `Asistente` solo puede consultar salas/platos y registrar pedidos. |

**Acciones de `PoliticaAcceso`:**
`CONSULTAR_SALAS` · `CONSULTAR_PLATOS` · `REGISTRAR_PEDIDOS` · `GESTIONAR_PEDIDOS` · `GESTIONAR_SALAS` · `GESTIONAR_PLATOS` · `GESTIONAR_USUARIOS` · `EDITAR_CONFIGURACION`

### `src/Controlador/` — 4 archivos

| Archivo | Delega a |
|:---|:---|
| `LoginControlador.java` | `AutenticacionServicio` |
| `PedidosControlador.java` | `PedidoServicio`, `ConsultaPedidosServicio`, `PedidoPdfServicio` |
| `PlatosControlador.java` | `PlatosServicio` |
| `SalasControlador.java` | `SalasServicio` |

### `src/Vista/` — 12 archivos

**Ventanas principales:**

| Archivo | Estado | Pendiente |
|:---|:---|:---|
| `FrmLogin.java` | Recibe `LoginControlador`; usa `AutenticacionSwingWorker`; Look&Feel registrado como `WARNING` si falla. | Retirar método `main()` generado por NetBeans. |
| `Sistema.java` | Recibe `Usuario` y controladores; toda operación JDBC pasa por un `SwingWorker`; autorización vía `PoliticaAcceso`. | — |
| `ManejadorErroresSwing.java` | Captura excepciones no atendidas en EDT y otros hilos; `RuntimeException` se registra y muestra; `Error` se relanza para el handler global. | — |

**Utilidades de vista:**

| Archivo | Descripción |
|:---|:---|
| `Eventos.java` | Utilidades de eventos de teclado; reubicado de `Modelo` a `Vista`. |
| `Tables.java` | Renderizador de celdas de tablas Swing; reubicado de `Modelo` a `Vista`. |

**SwingWorkers asíncronos (7) — ninguna consulta JDBC bloquea el EDT:**

| Archivo | Operación asíncrona |
|:---|:---|
| `AutenticacionSwingWorker.java` | Login |
| `FinalizarPedidoSwingWorker.java` | Cambio de estado del pedido |
| `ListaPedidosSwingWorker.java` | Historial de pedidos |
| `ListaPlatosSwingWorker.java` | Menú del día |
| `ListaSalasSwingWorker.java` | Listado de salas en tabla y panel |
| `PanelMesasSwingWorker.java` | Generación dinámica de botones de mesas |
| `PedidoEnPantallaSwingWorker.java` | Detalle de pedido seleccionado |

### `src/restaurante/` — 1 archivo

| Archivo | Responsabilidad |
|:---|:---|
| `Restaurante.java` | Composition Root. Instala logs y manejador de errores Swing. Construye e inyecta todos los controladores. Abre `FrmLogin` en el EDT. |

---

## Infraestructura de Base de Datos

### Motor
- **Imagen Docker:** `mysql:8.4.11` (LTS)
- **Compose de desarrollo:** `docker-compose.yml` — volumen persistente, puerto `127.0.0.1:3306`, healthcheck cada 5 s, credenciales desde `.env` (excluido de Git).
- **Compose de integración:** `docker-compose.integration.yml` — base `restaurante_test`, puerto `127.0.0.1:3307`, volumen y red aislados, destruidos automáticamente al terminar.

### Esquema (6 tablas)

| Tabla | Columnas clave | Restricciones |
|:---|:---|:---|
| `usuarios` | `id`, `nombre`, `correo`, `pass` (varchar 255), `rol` | `uq_usuarios_correo` — correo único |
| `salas` | `id`, `nombre`, `mesas` | — |
| `platos` | `id`, `nombre`, `precio` (decimal 10,2), `fecha` | — |
| `pedidos` | `id`, `id_sala`, `num_mesa`, `fecha`, `total` (decimal 10,2), `estado`, `usuario` | `uq_pedidos_mesa_pendiente` — columnas generadas STORED; FK → `salas.id` |
| `detalle_pedidos` | `id`, `nombre`, `precio` (decimal 10,2), `cantidad`, `comentario`, `id_pedido` | FK → `pedidos.id` |
| `config` | `id`, `ruc`, `nombre`, `telefono`, `direccion`, `mensaje` | — |

### Migraciones aplicadas

| Script | Descripción |
|:---|:---|
| `db/migrations/001_un_pedido_pendiente_por_mesa.sql` | Índice único condicional sobre columnas generadas STORED; impide más de un pedido `PENDIENTE` por sala/mesa. |
| `db/migrations/002_correo_usuario_unico.sql` | Índice único `uq_usuarios_correo`; evita altas duplicadas. |
| `db/migrations/003_password_hash_capacity.sql` | Amplía `pass` a `varchar(255)` para acomodar los hashes PBKDF2. |

---

## Suites de Pruebas Automatizadas

### Pruebas unitarias — `ant test` (sin MySQL, sin Docker)

**91 pruebas · 36 suites · 0 fallos · 0 errores**

| Suite | Tests | Qué verifica |
|:---|---:|:---|
| `Controlador.LoginControladorTest` | 1 | Delegación al servicio de autenticación |
| `Controlador.PedidosControladorTest` | 3 | Registro, consulta y PDF de pedidos |
| `Controlador.PlatosControladorTest` | 2 | Delegación y propagación de fallos |
| `Controlador.SalasControladorTest` | 2 | Delegación y propagación de fallos |
| `Modelo.DetallePedidoTest` | 1 | Validación de `BigDecimal` en precio |
| `Modelo.ErrorAplicacionExceptionTest` | 6 | Severidad, causa preservada y log único |
| `Modelo.LoginCompatibilidadTest` | 1 | Alias `pass` en fachada deprecada |
| `Modelo.PedidosDaoTest` | 2 | Rechazo de argumentos nulos antes de conectar |
| `Modelo.PedidosTest` | 1 | Validación de `BigDecimal` en total |
| `Modelo.PlatosDaoTest` | 2 | Rechazo de fecha nula y nombre; sin BD |
| `Modelo.PlatosTest` | 1 | Validación de `BigDecimal` en precio |
| `Modelo.SalasDaoTest` | 1 | Rechazo de argumento inválido; sin BD |
| `Modelo.UsuarioTest` | 1 | Construcción y accesores de entidad |
| `Servicio.AutenticacionServicioTest` | 5 | Validaciones, delegación, rechazo y advertencia |
| `Servicio.ConsultaPedidosServicioTest` | 4 | Consulta de pedidos, detalles y estado |
| `Servicio.GeneradorPdfPedidoTest` | 3 | Generación PDF con datos de prueba; firma `%PDF-` |
| `Servicio.PedidoPdfServicioTest` | 2 | Delegación y propagación de fallo del visor |
| `Servicio.PedidoServicioTest` | 4 | Validaciones, delegación, rechazo y propagación técnica |
| `Servicio.PlatosServicioTest` | 4 | Reglas de negocio sin BD |
| `Servicio.PoliticaAccesoTest` | 3 | Permiso de Administrador, restricciones de Asistente |
| `Servicio.SalasServicioTest` | 4 | Reglas de negocio sin BD |
| `Vista.AutenticacionSwingWorkerTest` | 2 | Ejecución asíncrona y callback en EDT |
| `Vista.EventosTest` | 1 | Registro de evento de teclado |
| `Vista.FinalizarPedidoSwingWorkerTest` | 4 | Éxito, fallo y callback seguro |
| `Vista.ListaPedidosSwingWorkerTest` | 2 | Carga asíncrona y callback |
| `Vista.ListaPlatosSwingWorkerTest` | 2 | Carga asíncrona y callback |
| `Vista.ListaSalasSwingWorkerTest` | 2 | Carga asíncrona y callback |
| `Vista.ManejadorErroresSwingTest` | 6 | RuntimeException, Error, fallo de diálogo, sin datos sensibles |
| `Vista.PanelMesasSwingWorkerTest` | 2 | Generación dinámica y callback |
| `Vista.PedidoEnPantallaSwingWorkerTest` | 2 | Detalle de pedido y callback |
| `Vista.TablesTest` | 1 | Renderizador de celda |
| `infraestructura.ArchivoLogDiarioTest` | 7 | Rotación, purga mensual, canal de emergencia y fallo cerrado |
| `infraestructura.ConexionTest` | 2 | Error de `.env` malformado propagado como `SQLException` |
| `infraestructura.ConfiguracionLogsTest` | 1 | Arranque aborta si no puede crear el log diario |
| `infraestructura.PasswordHasherTest` | 2 | Hash, verificación y rechazo de hash corrupto |
| `infraestructura.ProveedorConexionJdbcTest` | 2 | Precedencia de propiedades y error de `.env` |

### Pruebas de integración MySQL — `ant integration-test`

**13 pruebas · 1 suite · 0 fallos · 0 errores**
Ejecutadas contra `mysql:8.4.11` en contenedor desechable (`restaurante_test`, puerto 3307). Guardia Ant impide ejecución sobre la base de desarrollo.

| Prueba | Escenario validado |
|:---|:---|
| `verificaVersionCatalogoYEsquemaMysqlReal` | Catálogo, esquema y versión 8.4.x del motor |
| `autenticaUsuarioSemillaRechazaCredencialesYConsultaMenu` | Login correcto, credencial inválida, consulta de menú del día y migración de clave legacy a PBKDF2 |
| `rechazaUnRolNoReconocidoAntesDeCrearLaSesion` | `PoliticaAcceso` rechaza rol desconocido sin llegar a BD |
| `altaGuardaHashYAutenticacionVerificaLaClaveSinTextoPlano` | Alta de usuario, verificación con hash y negativa a comparar en texto plano |
| `correoDuplicadoEsConflictoWarningConCausaYLogUnico` | MySQL 1062 → `WARNING` con causa preservada y sin exponer contraseña |
| `generaPdfDesdeDatosMySqlSinAbrirAplicacionExterna` | PDF generado desde datos reales; cabecera `%PDF-` verificada |
| `falloAlAbrirPdfPreservaCausaYQuedaRegistradoUnaSolaVez` | Fallo del visor → `SEVERE` con causa y log único |
| `ejecutaCrudRealDeSalasYPlatos` | CREATE, READ, UPDATE, DELETE en MySQL real |
| `noPermiteBorrarSalaConHistorialYRegistraConflictoComoWarning` | MySQL 1451 → mensaje de negocio explícito |
| `transaccionRegistraPedidoDetallesYFinalizacion` | Inserción atómica, `getGeneratedKeys()`, cambio de estado |
| `falloForzadoEnDetalleHaceRollbackYDejaUnaEntradaDeLog` | Rollback comprobado; encabezado limpio en BD |
| `dosSesionesConcurrentesSoloDejanUnPedidoPendiente` | Índice único condicional previene duplicado bajo concurrencia |
| `erroresDeConexionYCredencialesSePropaganYQuedanEnLog` | Conexión detenida y credencial incorrecta propagadas con log único |

---

## Comandos de Referencia

```bash
# Levantar la base de datos de desarrollo
docker compose up -d

# Compilar
ant clean compile

# Ejecutar pruebas unitarias (sin Docker)
ant test

# Ejecutar pruebas de integración MySQL (levanta y destruye contenedor aislado)
ant integration-test

# Ejecutar la aplicación
ant run

# Construir JAR distribuible
ant jar
```

### Flujo de trabajo Git

```bash
# Antes de subir cualquier cambio:
ant clean test        # debe pasar en verde
git add .
git commit -m "tipo(alcance): descripción breve"
git push origin main
```

---

## Restricciones de Calidad Activas

Estas restricciones se verifican en cada cambio. Un cambio que las rompa no se acepta:

1. **Ningún `catch` vacío ni `System.err`** en código de producción.
2. **Un único registro de log por error** — sin duplicidad de stack traces entre handlers.
3. **Ningún secreto en logs** — contraseñas, tokens ni claves nunca aparecen en los archivos de log.
4. **Ninguna operación de I/O en el EDT de Swing** — toda consulta JDBC pasa por un `SwingWorker`.
5. **Ningún `double` para precios o totales** — solo `BigDecimal` con validación de nulos.
6. **Toda excepción preserva su causa original** — sin swallow ni re-envueltas sin causa.
7. **`ant test` en verde antes de hacer `git push`** — sin commits que rompan las pruebas unitarias.
8. **La suite de integración usa solo credenciales fijas de `restaurante_test`** — el guardia Ant aborta ante cualquier otra URL o credencial.

---

## Deuda Técnica y Próximos Pasos

### Pendiente técnico (prioridad alta)

| Ítem | Descripción |
|:---|:---|
| Retirar `login.java` como fachada deprecada | Requiere migrar todos los consumidores restantes a `Usuario`. |
| Eliminar `main()` en `FrmLogin.java` | El método `main()` generado por NetBeans queda como acceso directo no controlado; debe eliminarse. |
| Elevar `javac.source` y `javac.target` a `17` o `21` | El runtime actual es OpenJDK 17 y el proyecto compila en source/target 11. Elevar a 17 habilita `Text Blocks`, `Records`, `Pattern Matching` y `sealed interfaces` sin instalar nada adicional. |

### Roadmap de modernización (Java 17 → 21)

| Característica | Aplicación concreta |
|:---|:---|
| **Text Blocks** (Java 15+) | Simplificar las sentencias SQL multilínea de los DAOs. |
| **Records** (Java 16+) | Convertir `Config`, `Salas`, `Platos` y otros beans inmutables a `record`. Elimina getters, constructores y `equals`/`hashCode` repetitivos. |
| **Pattern Matching** (Java 17+) | Simplificar el tratamiento de `instanceof SQLException` en los DAOs y handlers de error. |
| **Virtual Threads** (Java 21) | Sustituir los `SwingWorker`s por `Thread.startVirtualThread(...)`. Un hilo virtual por operación de I/O, con coste de memoria despreciable. |
