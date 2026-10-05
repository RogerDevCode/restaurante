# Plan de QA, Estabilización y Arquitectura — Restaurante

## 1. Objetivo y Alcance

Estabilizar el entorno de base de datos, blindar la integridad transaccional y financiera de los pedidos, proteger la autenticación con hashing criptográfico, desacoplar la interfaz gráfica (Swing) del I/O bloqueante (JDBC), y estructurar el sistema bajo una arquitectura MVC multicapa desacoplada y verificada mediante compuertas de calidad automatizadas (QA).

---

## 2. Quality Gates y Criterios de Aceptación (Definition of Done)

Para considerar cualquier funcionalidad o refactorización como aceptada, se deben cumplir obligatoriamente las siguientes compuertas de calidad:

- [x] **Compilación limpia (`ant clean compile`):** Cero errores de compilación con gestión de dependencias portable (sin rutas absolutas del SO).
- [x] **Suite de pruebas unitarias (`ant test`):** Cobertura exhaustiva de servicios, controladores, manejadores de eventos y modelo mediante dobles/mocks sin depender de la base de datos (actualmente **91 pruebas unitarias en 36 suites, 0 fallos, 0 errores**).
- [x] **Suite de integración MySQL real (`ant integration-test`):** Contenedor MySQL 8.4.11 desechable y aislado en puerto 3307 con base `restaurante_test`, validando transacciones, rollback, concurrencia, restricciones de unicidad y generación física de PDF (actualmente **10 pruebas de integración, 0 fallos, 0 errores**). Guardia Ant de seguridad activo para impedir ejecución destructiva en desarrollo.
- [x] **Criterio transversal de errores y observabilidad:**
  - Preservación del 100% de la cadena causal (`cause`) y tipo de excepción.
  - Registro único del error (sin duplicidad de logs).
  - Rotación diaria de archivos `logs/restaurante-AAAA-MM-DD.log` y purga mensual por calendario (respetando años bisiestos y cambios de año).
  - Canal de emergencia durable ante fallos de I/O en el logger.
  - Ningún secreto ni contraseña expuesto en mensajes de log o vistas.
- [x] **Responsividad de la interfaz (Swing):** Cero operaciones de red/JDBC ejecutadas en el Event Dispatch Thread (EDT). Todas las consultas se canalizan asíncronamente mediante `SwingWorker`s dedicados.
- [x] **Precisión financiera:** Valores de precios y totales gestionados con `BigDecimal` para evitar imprecisiones de coma flotante (`double`).
- [x] **Seguridad y Control de Acceso:**
  - Contraseñas almacenadas exclusivamente con PBKDF2-HMAC-SHA-256 (600.000 iteraciones + salt aleatorio de 16 bytes).
  - Verificación en tiempo constante contra ataques de temporización.
  - Migración transparente de contraseñas legacy al autenticar.
  - Control de acceso basado en roles (RBAC) desacoplado mediante `PoliticaAcceso`.

---

## 3. Inventario Completo de Arquitectura MVC (48 Archivos en `src/`)

El código de producción se encuentra estrictamente dividido por responsabilidades en capas desacopladas:

### A. Infraestructura (4 archivos)
1. `[x]` `src/infraestructura/ProveedorConexionJdbc.java`: Apertura JDBC centralizada, resolución de `.env`/variables de entorno y soporte de sobreescritura para pruebas.
2. `[x]` `src/infraestructura/ConfiguracionLogs.java`: Inicialización de logging antes de aceptar eventos; aborta ante fallos de inicialización.
3. `[x]` `src/infraestructura/ArchivoLogDiario.java`: Rotación diaria de bitácora, retención por mes calendario y canal de emergencia ante fallos del handler.
4. `[x]` `src/infraestructura/PasswordHasher.java`: Hashing criptográfico PBKDF2-HMAC-SHA-256 con verificación en tiempo constante y derivación de sal.

### B. Modelo y Dominio (8 archivos)
5. `[x]` `src/Modelo/Usuario.java`: Entidad de usuario limpia e inmutable.
6. `[x]` `src/Modelo/Config.java`: Bean de configuración empresarial sin dependencias JDBC/UI.
7. `[x]` `src/Modelo/Platos.java`: Entidad plato con precisión monetaria `BigDecimal`.
8. `[x]` `src/Modelo/Salas.java`: Entidad de sala y control de mesas.
9. `[x]` `src/Modelo/Pedidos.java`: Entidad de encabezado de pedido con total `BigDecimal`.
10. `[x]` `src/Modelo/DetallePedido.java`: Entidad de detalle de pedido con precio `BigDecimal`.
11. `[x]` `src/Modelo/Conexion.java`: Fachada compatible hacia `ProveedorConexionJdbc`.
12. `[~]` `src/Modelo/login.java`: Fachada deprecada de compatibilidad que delega en `Usuario`.

### C. Excepciones de Dominio y Técnicas (3 archivos)
13. `[x]` `src/Modelo/ErrorAplicacionException.java`: Base para excepciones de aplicación con severidad diferenciada (`WARNING` para reglas/validaciones vs `SEVERE` para fallos técnicos).
14. `[x]` `src/Modelo/DataAccessException.java`: Envoltura especializada de errores SQL/JDBC que preserva causa técnica.
15. `[x]` `src/Modelo/PedidoPendienteExistenteException.java`: Conflicto de negocio ante intento de duplicar pedido pendiente en una mesa.

### D. Contratos de Persistencia / Repositorios (4 archivos)
16. `[x]` `src/Modelo/AutenticacionRepositorio.java`: Contrato inyectable para validación de credenciales y usuario.
17. `[x]` `src/Modelo/PedidosRepositorio.java`: Contrato para inserción transaccional, consultas y detalle de pedidos.
18. `[x]` `src/Modelo/PlatosRepositorio.java`: Contrato inyectable para operaciones de catálogo de platos.
19. `[x]` `src/Modelo/SalasRepositorio.java`: Contrato inyectable para CRUD de salas.

### E. Implementaciones de Persistencia / DAOs (4 archivos)
20. `[x]` `src/Modelo/LoginDao.java`: Implementación JDBC con migración de claves a PBKDF2, captura de correo duplicado (MySQL 1062) y datos de empresa.
21. `[x]` `src/Modelo/PedidosDao.java`: Implementación transaccional con `getGeneratedKeys()`, rollback automático y mapeo de unicidad pendiente.
22. `[x]` `src/Modelo/PlatosDao.java`: Implementación JDBC para consultas por fecha/nombre y mutaciones parametrizadas.
23. `[x]` `src/Modelo/SalasDao.java`: Implementación JDBC con captura y traducción de restricción foránea (MySQL 1451).

### F. Servicios de Negocio y Aplicación (8 archivos)
24. `[x]` `src/Servicio/AutenticacionServicio.java`: Caso de uso de login con validación de entradas sin tocar base de datos.
25. `[x]` `src/Servicio/PedidoServicio.java`: Orquestación y validación transaccional del registro completo de pedido.
26. `[x]` `src/Servicio/PlatosServicio.java`: Reglas de negocio y gestión del catálogo de platos diarios.
27. `[x]` `src/Servicio/SalasServicio.java`: Reglas de salas y mesas.
28. `[x]` `src/Servicio/ConsultaPedidosServicio.java`: Coordinación de consultas, estados y detalles de pedidos.
29. `[x]` `src/Servicio/PedidoPdfServicio.java`: Orquestación de datos y apertura segura del reporte PDF.
30. `[x]` `src/Servicio/GeneradorPdfPedido.java`: Ensamblaje físico del archivo PDF con iText sin depender de UI.
31. `[x]` `src/Servicio/PoliticaAcceso.java`: Control de autorización basado en roles (`Administrador` vs `Asistente`).

### G. Controladores MVC (4 archivos)
32. `[x]` `src/Controlador/LoginControlador.java`: Mediador entre vista de login y servicio de autenticación.
33. `[x]` `src/Controlador/PedidosControlador.java`: Mediador para alta, consulta, finalización y PDF de pedidos.
34. `[x]` `src/Controlador/PlatosControlador.java`: Mediador para listado, creación y edición de platos.
35. `[x]` `src/Controlador/SalasControlador.java`: Mediador para salas y mesas.

### H. Vista y Componentes Gráficos Swing (12 archivos)
36. `[~]` `src/Vista/FrmLogin.java`: Ventana de login desacoplada, con inyección de controlador y worker asíncrono.
37. `[~]` `src/Vista/Sistema.java`: Ventana principal de administración desacoplada mediante controladores, workers y `PoliticaAcceso`.
38. `[x]` `src/Vista/ManejadorErroresSwing.java`: Gestor de errores no capturados en EDT y visualización segura al usuario.
39. `[x]` `src/Vista/Eventos.java`: Utilidad de eventos de teclado ubicada correctamente en la capa de vista.
40. `[x]` `src/Vista/Tables.java`: Renderizador Swing de tablas ubicado en la vista.
41. `[x]` `src/Vista/AutenticacionSwingWorker.java`: Worker asíncrono para login sin bloquear la ventana.
42. `[x]` `src/Vista/FinalizarPedidoSwingWorker.java`: Worker para actualización transaccional de estado.
43. `[x]` `src/Vista/ListaPedidosSwingWorker.java`: Worker para carga de historial en segundo plano.
44. `[x]` `src/Vista/ListaPlatosSwingWorker.java`: Worker para consulta del menú del día.
45. `[x]` `src/Vista/ListaSalasSwingWorker.java`: Worker para listado de salas en tablas.
46. `[x]` `src/Vista/PanelMesasSwingWorker.java`: Worker para carga dinámica de botones de mesas.
47. `[x]` `src/Vista/PedidoEnPantallaSwingWorker.java`: Worker para renderizar pedidos seleccionados.

### I. Punto de Entrada (1 archivo)
48. `[x]` `src/restaurante/Restaurante.java`: Composition Root; inicializa logs, captura de errores EDT, controladores e inyección de dependencias hacia `FrmLogin`.

---

## 4. Estado de Implementación por Fases

| Fase | Descripción | Estado | Evidencia de Verificación |
| :--- | :--- | :---: | :--- |
| **Fase 1** | **Entorno Reproducible** | ✅ **Completada** | Docker Compose MySQL 8.4.11 (`healthy`), lectura segura de `.env`, classpath sin rutas absolutas de Windows. |
| **Fase 2** | **Integridad Transaccional y Financiera** | ✅ **Completada** | Transacción atómica en `PedidosDao` con rollback probado ante fallo de detalle; recuperación de ID con `getGeneratedKeys()`; índice único `uq_pedidos_mesa_pendiente` (`db/migrations/001`); importes migrados a `BigDecimal`. |
| **Fase 3** | **Acceso a Datos y Conexiones** | ✅ **Completada** | `try-with-resources` en todos los DAO; eliminación de conexiones compartidas como estado; traducción de errores SQL (1062, 1451). |
| **Fase 4** | **Seguridad y Autorización** | ✅ **Completada** | Cifrado PBKDF2-SHA256 con salt aleatorio en `PasswordHasher`; migración `003_password_hash_capacity.sql`; índice `002_correo_usuario_unico.sql`; autorización con `PoliticaAcceso`. |
| **Fase 5** | **Menú Dinámico e Interfaz Responsiva** | ✅ **Completada** | Filtro de platos con `CURRENT_DATE()`; 7 `SwingWorker`s desacoplando todas las consultas pesadas del EDT de Swing. |
| **Fase 6** | **Regresión y Aceptación** | ✅ **Completada** | 101 pruebas automatizadas (91 unitarias + 10 de integración en MySQL 8.4 real con Docker desechable). |
| **Fase 7** | **Política Transversal de Errores y Logs** | ✅ **Completada** | Logs diarios rotativos con purga por mes calendario (`ArchivoLogDiario`); canal de emergencia probado; manejador global de excepciones Swing. |
| **Fase 8** | **Arquitectura MVC Multicapa** | ✅ **Completada** | 48 archivos organizados en Infraestructura, Dominio, Repositorios, DAOs, Servicios, Controladores y Vistas. |
| **Fase 9** | **Automatización de QA y Pipelines** | ✅ **Completada** | Targets `ant test` (unitarias) y `ant integration-test` (integración MySQL) automatizados e independientes. |

---

## 5. Matriz de Trazabilidad de Pruebas (RTM)

- **Total de pruebas unitarias:** 91 pruebas en 36 archivos `*Test.java` (0 fallos, 0 errores).
- **Total de pruebas de integración:** 10 pruebas en `MySqlIntegrationIT.java` contra contenedor MySQL 8.4.11 (0 fallos, 0 errores).
- **Cobertura clave verificada:**
  - `PasswordHasherTest`: Generación y validación de hash, rechazo de contraseñas incorrectas, longitud de sal y formato PBKDF2.
  - `PoliticaAccesoTest`: Permisos diferenciados por rol para cada una de las 8 acciones del sistema.
  - `PlatosServicioTest` / `SalasServicioTest` / `ConsultaPedidosServicioTest`: Reglas de negocio aisladas con repositorios dobles.
  - `AutenticacionSwingWorkerTest` y restantes tests de workers: Ejecución asíncrona y callbacks seguros en EDT.
  - `MySqlIntegrationIT`: Rollback transaccional inducido mediante trigger, concurrencia de mesas, generación física de PDF y cotejo de esquemas.

---

## 6. Roadmap de Modernización Técnica

1. **Actualización del compilador:**
   - Elevar `javac.source` y `javac.target` en `nbproject/project.properties` a `17` (versión instalada) o `21` (LTS recomendada).
2. **Adopción de Records:**
   - Convertir beans inmutables (`Config`, DTOs de pedidos y detalles) a Java `record` para eliminar código repetitivo.
3. **Bloques de texto (Text Blocks):**
   - Simplificar sentencias SQL multilínea en los DAOs usando sintaxis `"""`.
4. **Hilos Virtuales (Project Loom en Java 21):**
   - Transicionar los `SwingWorker`s a ejecución directa sobre `Virtual Threads` mediante `Thread.startVirtualThread(...)`.
