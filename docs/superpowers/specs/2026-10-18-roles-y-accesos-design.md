# Roles y accesos configurables

## Objetivo

Incorporar los roles Mesonero, Cocinero, Cajero, Maitre y Host, y permitir que un administrador gestione desde Configuración qué pantallas y acciones puede usar cada rol. El rol Administrador conserva todos los accesos de forma fija y no se puede modificar desde la matriz.

## Estado actual

- El alta de usuarios ofrece Administrador y Asistente.
- `PoliticaAcceso` reconoce esos dos roles y define sus permisos en código.
- La ventana principal verifica permisos para algunas acciones y servicios; no existe una grilla de permisos.
- La aplicación imprime comandas para cocina, pero no conserva en BD los incrementos de cada ronda ni un estado de preparación.
- `configuracion_sistema` ofrece persistencia clave/valor, que permite guardar permisos sin cambios DDL.

## Diseño

### Roles

Se conservará Asistente para mantener compatibilidad con cuentas existentes. Además de Administrador, habrá seis roles operativos: Asistente y los cinco roles pedidos.

| Rol | Permisos iniciales recomendados |
|---|---|
| Administrador | Todos los permisos; acceso fijo y no editable. |
| Asistente | Conserva el acceso actual: consultar salas y platos, registrar pedidos. |
| Mesonero | Consultar salas y platos; registrar y actualizar pedidos abiertos para atención de mesas. |
| Cocinero | Rol creado sin permisos iniciales hasta que exista una consulta fiel de rondas de cocina. El Administrador puede asignarle permisos disponibles en otras pantallas. |
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

### Límite del rol Cocinero

No se mostrará `detalle_pedidos` como una lista de comandas: esa tabla acumula todas las rondas de la mesa y no distingue los ítems ya marchados. Hacerlo podría duplicar platos en cocina. Para activar una pantalla de cocina se necesita una futura cola persistente de rondas con estado de preparación y registro atómico al enviar la comanda. Esa ampliación requiere un diseño separado y cambios de esquema, por lo que queda fuera de esta entrega.

## Alternativas consideradas

1. **Permisos codificados por rol:** simple, pero cada cambio de permisos exige recompilar y no cumple la necesidad de gestión desde una grilla.
2. **Matriz configurable con catálogo central (elegida):** permite editar accesos desde la aplicación y mantener la autorización consistente en UI y servicios.
3. **Control visual solo en botones/pestañas:** más rápido, pero las operaciones podrían seguir invocándose sin permiso desde otros flujos; se descarta.

## Alcance y aceptación

- Crear usuarios con cualquiera de los roles conservados y nuevos.
- Mostrar y guardar la matriz para todos los roles excepto que Administrador permanece bloqueado y con acceso total.
- Aplicar los permisos configurados tanto al navegar/usar controles como al invocar operaciones protegidas.
- Crear Cocinero como rol válido sin conceder acceso a datos que no representan fielmente las comandas.
- Mantener el comportamiento actual de cuentas Asistente y Administrador.
- Persistir configuración sin cambios de esquema de base de datos.

## Fuera de alcance

- Reservas para Host.
- Flujo de estados de preparación (en preparación/listo/entregado) para cocina.
- Cola persistente de rondas de cocina y pantalla de seguimiento para Cocinero.
- Anulación de factura para roles distintos de Administrador.
- Asignación de usuarios a mesoneros o restricción de un Mesonero a sus propias mesas; el sistema actual no tiene ese vínculo.
