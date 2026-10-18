# E2E virtual de jornada completa y combinatoria fiscal

## Objetivo

Agregar una prueba de aceptación que recorra una jornada determinista de servicio con la ventana Swing real, mouse y teclado virtuales propios, persistencia MySQL real y una base de datos desechable. La prueba debe complementar las 52 operaciones de `Vista.CenaLifecycleE2E`, que hoy ejercita DAOs y servicios pero no conduce la aplicación desde la interfaz.

## Contexto actual

- `./e2e_virtual.sh [clase]` compila las pruebas y ejecuta la clase en Xvfb `:99` a 1366×711. La pantalla del operador `:0` permanece independiente.
- `Vista.E2EVirtual`, `Vista.PedidoRapidoE2EVirtual` y otras pruebas de `Vista` construyen `Sistema` directamente y usan `java.awt.Robot`; los tests de operación pueden seguir ese patrón.
- `Vista.CenaLifecycleE2E` cubre seis perfiles fiscales, cambios de tasa e IVA, pagos mixtos, vuelto, pagos diferidos y conciliación, pero ejecuta esos flujos por DAO.
- `docker-compose.integration.yml` prepara MySQL en `127.0.0.1:3307`, con esquema y semilla conocidos. El target `ant integration-test` elimina su propio proyecto Compose y volumen al finalizar.
- `e2e_virtual.sh` permite pasar `DB_URL`, `DB_USER` y `DB_PASSWORD`; sin esos valores el proveedor JDBC puede resolver el `.env` habitual. La nueva prueba no debe aceptar esa resolución implícita.

## Enfoques considerados

1. **Ampliar `CenaLifecycleE2E`:** conserva la prueba rápida de volumen, pero sigue sin ejercitar controles Swing ni `Robot`.
2. **Crear una prueba Swing con repositorios en memoria:** mejora la cobertura de interacción, pero no verifica persistencia fiscal, pagos ni cierre contra MySQL.
3. **Crear un recorrido híbrido sobre `Sistema` y MySQL aislado:** usa DAOs para preparar fixtures y consultar resultados, `Robot` para las acciones operativas y una BD desechable para que toda la jornada sea repetible. Es el enfoque elegido.

## Componentes

### `test/Vista/DiaCompletoFiscalE2EVirtual.java`

Construye `Sistema` con sesión administradora y controladores conectados al proveedor JDBC. Prepara una sala y seis platos con nombres únicos por ejecución. La preparación de fixtures y las consultas de comprobación pueden usar servicios/DAOs; las acciones que representan al personal deben hacerse con clics y pulsaciones de un `Robot`.

La reflexión sobre componentes Swing se limita a localizar controles y leer su estado para aserciones. No se deben invocar `doClick`, handlers privados ni mutar tablas para sustituir interacciones del operador.

La prueba requiere `DB_URL`, `DB_USER` y `DB_PASSWORD` explícitos y verifica que `DB_URL` use `127.0.0.1:3307/restaurante_test`. Si falta la configuración o apunta a otra base, termina antes de abrir la ventana o escribir datos.

### `e2e_dia_completo_virtual.sh`

Usa `docker-compose.integration.yml` con nombre de proyecto `restaurante-dia-e2e-$$` (sufijo PID), espera a que MySQL esté saludable, exporta las credenciales fijas de `restaurante_test` y llama a `./e2e_virtual.sh Vista.DiaCompletoFiscalE2EVirtual`. Un `trap` elimina únicamente el proyecto y el volumen creados por este lanzador, incluso si falla la prueba. No debe ejecutar `ant integration-test`, cuyo ciclo tiene su propio proyecto y elimina ese volumen.

El puerto host 3307 no se comparte con otra suite en paralelo. Si ya está ocupado, el lanzador debe fallar sin detener ni eliminar el contenedor que lo ocupa. El `trap` solo puede bajar el nombre de proyecto que él mismo creó.

## Datos fiscales de la matriz

La carta de prueba cubre el producto cartesiano **moneda base × tratamiento de IVA**:

| Moneda base | Exento | IVA añadido al precio | IVA incluido en el precio |
|---|---|---|---|
| USD | Sí | Sí | Sí |
| VES (bolívares) | Sí | Sí | Sí |

Cada plato conserva su moneda base y sus precios secundarios calculados con la tasa de la jornada. Los pedidos combinados incluyen líneas de distintas monedas y tratamientos de IVA. Los importes fiscales y de conversión se esperan con dos decimales, según la política monetaria del proyecto.

## Recorrido de jornada

1. **Apertura y primera ronda:** preparar la sala y la carta; desde la interfaz abrir varias mesas y crear seis cuentas de un solo producto (una por perfil fiscal) y cuentas combinadas. Incluir cantidades mayores que uno, un plato en USD y otro en VES, y líneas exentas, gravadas con IVA agregado y gravadas con IVA incluido.
2. **Segunda ronda:** reabrir una cuenta pendiente, agregar productos con `Agregar Productos` y actualizarla. Verificar que la cuenta acumule la ronda nueva sin duplicar las líneas anteriores y que sus montos visibles coincidan con la BD.
3. **Cobros de la jornada:** cerrar pedidos separados con pago solo en VES, solo en USD y mixto VES+USD. Validar en la interfaz montos exactos y que un monto descuadrado no permita finalizar. La tabla de pagos Swing registra el monto aplicado y no captura por separado recibido/vuelto; el caso de vuelto permanece cubierto por `CenaLifecycleE2E` a nivel de servicio/BD. Verificar métodos, moneda, montos aplicados y equivalencia en VES; la suma de pagos y ajustes debe conciliar con el total persistido.
4. **Cambio de condiciones a mitad de jornada:** cambiar tasa e IVA desde `Datos de la Empresa` usando la interfaz. Confirmar con `Robot` el diálogo que aparece al cambiar la tasa. Comprobar que las cuentas pendientes abiertas recalculan montos con la nueva tasa y conservan el IVA guardado en sus líneas, que las facturas finalizadas mantienen sus snapshots y que los pedidos nuevos usan el nuevo porcentaje global de IVA.
5. **Cancelación de cuenta pendiente:** anular una cuenta abierta mediante la acción de fuga/cancelación. Comprobar que deja de ocupar la mesa y no registra pagos ni ventas finalizadas.
6. **Cierre Z:** desde la interfaz generar el cierre definitivo de la fecha actual. El servicio de cierre se construye con un adaptador de impresión no-op y un directorio temporal; evita depender de una impresora física o abrir una aplicación externa, pero conserva la ejecución real del cálculo, la generación del PDF y la escritura de auditoría. Verificar en BD el cierre de tipo Z, la cantidad de pedidos pagados, importes fiscales y conciliación de desglose por método y sala con sus totales de cabecera.

Las aserciones consultan BD después de cada transición relevante y leen la interfaz para confirmar el estado que observa el usuario. Los casos monetarios deben elegir importes deterministas que permitan expresar el resultado esperado en centavos y detectar doble redondeo, aplicación de IVA a líneas exentas o IVA duplicado en precios inclusivos.

## Aislamiento y limpieza

- El recorrido se ejecuta solo en MySQL creado por `e2e_dia_completo_virtual.sh`; no admite la BD del `.env`, producción ni un esquema con otro nombre.
- La base fresca del proyecto Compose es la unidad de limpieza. El lanzador la elimina al terminar; no se borran tablas o datos de otros proyectos.
- La ventana Swing se cierra en `finally`; el lanzador existente elimina Xvfb `:99` al salir.
- El código de la prueba no depende de impresoras reales, visores PDF externos ni datos de mesas reales.
- No se cambia ni se restaura la configuración de la BD habitual: el ámbito efímero contiene tasa, IVA, pedidos, pagos, auditoría y cierre.

## Criterios de aceptación

1. El comando dedicado arranca una BD limpia e inicia la prueba en Xvfb `:99`, con las credenciales de integración fijas.
2. La clase rechaza antes de operar cualquier URL distinta de `127.0.0.1:3307/restaurante_test` o credenciales ausentes.
3. Los seis perfiles de la matriz aparecen en pedidos persistidos con `moneda_base`, `aplica_iva`, `precio_incluye_iva` y porcentaje fiscal correctos.
4. La UI permite completar pedidos de un producto y combinados, incremento de ronda, pagos en VES/USD/mixto, cambio de configuración, cancelación pendiente y cierre Z mediante acciones de `Robot`.
5. Los totales, subtotales, IVA, pagos y desglose Z concuerdan entre pantalla y BD a dos decimales; los montos de pago descuadrados se bloquean; las facturas ya pagadas conservan sus snapshots ante cambios posteriores.
6. El éxito y el fallo terminan cerrando la aplicación, el Xvfb y el proyecto Compose aislado, sin operar contra el `.env`.

## Fuera de alcance

- Reemplazar o duplicar la simulación de 52 pedidos de `CenaLifecycleE2E`.
- Cubrir por UI el CRUD completo de platos, usuarios, categorías y salas; los fixtures preparan solo los datos requeridos por la jornada.
- Usar impresora física, ejecutar el login visual o probar anulaciones de facturas ya cobradas. La ventana se construye con una sesión administradora, siguiendo el patrón de los E2E Swing existentes.
- Ejecutar esta prueba contra la base productiva o contra el escritorio visible del operador.
