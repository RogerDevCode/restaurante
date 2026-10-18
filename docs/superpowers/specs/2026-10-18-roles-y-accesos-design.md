# Roles y accesos configurables

## Objetivo

Incorporar los roles Mesonero, Cocinero, Cajero, Maitre y Host, y permitir que un administrador gestione desde Configuración qué pantallas y acciones puede usar cada rol. El rol Administrador conserva todos los accesos de forma fija y no se puede modificar desde la matriz.

## Estado actual

- El alta de usuarios ofrece Administrador y Asistente.
- `PoliticaAcceso` reconoce esos dos roles y define sus permisos en código.
- La ventana principal verifica permisos para algunas acciones y servicios; no existe una grilla de permisos.
- La aplicación imprime comandas para cocina, pero no tiene una vista de cocina de solo lectura.
- `configuracion_sistema` ofrece persistencia clave/valor, que permite guardar permisos sin cambios DDL.

## Diseño

### Roles

Se conservará Asistente para mantener compatibilidad con cuentas existentes. Además de Administrador, habrá seis roles operativos: Asistente y los cinco roles pedidos.

| Rol | Permisos iniciales recomendados |
|---|---|
| Administrador | Todos los permisos; acceso fijo y no editable. |
| Asistente | Conserva el acceso actual: consultar salas y platos, registrar pedidos. |
| Mesonero | Consultar salas y platos; registrar y actualizar pedidos abiertos para atención de mesas. |
| Cocinero | Consultar una nueva vista de comandas pendientes y sus detalles; solo lectura. |
| Cajero | Consultar pedidos pendientes, cobrar/finalizar y consultar historial de ventas; sin editar pedidos ni configuración. |
| Maitre | Consultar salas/platos; registrar y gestionar pedidos abiertos, incluida la asignación de mesonero y cancelación de cuentas pendientes. No puede anular facturas cobradas. |
| Host | Consultar salas y disponibilidad/ocupación de mesas; sin pedidos ni edición. |

Los permisos iniciales son valores predeterminados editables desde la matriz, salvo el acceso total del Administrador. Las acciones de anulación de factura, gestión de usuarios y edición de configuración quedan restringidas a Administrador en la configuración inicial.

### Matriz de permisos

Se añadirá una sección **Configuración → Roles y accesos**. Mostrará los roles como filas y un catálogo agrupado por pantallas y acciones como columnas o casillas, con controles para seleccionar un rol, activar/desactivar permisos y guardar. El catálogo cubrirá el acceso a pantallas y operaciones visibles (incluidos botones con acciones protegidas), no cada control decorativo.

El acceso de Administrador se mostrará completo y bloqueado. El sistema no permitirá guardar permisos que reduzcan ese acceso, ni que el Administrador se quite sus propios permisos.

### Autorización y persistencia

- La matriz se guardará en `configuracion_sistema` con claves estables por rol y permiso, sin agregar columnas ni tablas.
- Un catálogo central definirá permisos y nombres visibles para que la interfaz de configuración, la visibilidad de componentes y la política de autorización usen los mismos identificadores.
- Las comprobaciones se harán en la interfaz y en los servicios/casos de uso que ejecutan operaciones; ocultar un botón por sí solo no constituye autorización.
- Las claves inexistentes usarán los valores predeterminados del rol. Permisos desconocidos o roles no reconocidos se denegarán.
- Los cambios se aplicarán desde el próximo inicio de sesión para evitar estados de permiso inconsistentes durante una operación abierta.

### Vista de cocina

Se añadirá una pestaña de consulta para Cocinero que muestre comandas pendientes con mesa, hora, mesonero y detalles/observaciones disponibles. La pantalla será de solo lectura y no cambiará el ciclo operativo ni contable del pedido; las comandas seguirán registrándose desde el flujo actual de atención. Si el modelo no conserva un estado de preparación por ítem, la vista no ofrecerá botones para marcar platos como listos.

## Alternativas consideradas

1. **Permisos codificados por rol:** simple, pero cada cambio de permisos exige recompilar y no cumple la necesidad de gestión desde una grilla.
2. **Matriz configurable con catálogo central (elegida):** permite editar accesos desde la aplicación y mantener la autorización consistente en UI y servicios.
3. **Control visual solo en botones/pestañas:** más rápido, pero las operaciones podrían seguir invocándose sin permiso desde otros flujos; se descarta.

## Alcance y aceptación

- Crear usuarios con cualquiera de los roles conservados y nuevos.
- Mostrar y guardar la matriz para todos los roles excepto que Administrador permanece bloqueado y con acceso total.
- Aplicar los permisos configurados tanto al navegar/usar controles como al invocar operaciones protegidas.
- Permitir que Cocinero consulte comandas sin modificarlas.
- Mantener el comportamiento actual de cuentas Asistente y Administrador.
- Persistir configuración sin cambios de esquema de base de datos.

## Fuera de alcance

- Reservas para Host.
- Flujo de estados de preparación (en preparación/listo/entregado) para cocina.
- Anulación de factura para roles distintos de Administrador.
- Asignación de usuarios a mesoneros o restricción de un Mesonero a sus propias mesas; el sistema actual no tiene ese vínculo.
