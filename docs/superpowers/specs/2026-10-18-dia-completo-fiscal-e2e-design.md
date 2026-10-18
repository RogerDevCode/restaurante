# E2E virtual de jornada completa y combinatoria fiscal

## Objetivo

Agregar una prueba E2E de aceptación que conduzca la aplicación Swing real durante una jornada simulada. La prueba debe descubrir fallas en la experiencia completa del operador: configuración, alta de sala y carta, comandas/cuentas, rondas, cobros, cancelación de una cuenta impaga y cierre Z.

Toda interacción funcional y toda preparación de datos se hace desde la interfaz con `java.awt.Robot`. Las comprobaciones se limitan a información que la aplicación presenta en pantalla. El test no llama DAOs, repositorios, servicios, controladores ni modelos, y no consulta ni modifica la base de datos directamente.

## Enfoque

- Arrancar la aplicación por su login normal y autenticarse en la ventana con mouse y teclado virtuales.
- Ejecutar exclusivamente bajo `./e2e_virtual.sh` en Xvfb `:99`, 1366×711; el display y el cursor del operador permanecen aislados.
- Arrancar un MySQL de integración desechable mediante `e2e_dia_completo_virtual.sh`, con proyecto y volumen Compose propios. Rechazar un puerto 3307 ya ocupado y retirar solo el proyecto propio.
- Exigir propiedades JDBC explícitas con las credenciales fijas de integración para evitar que la aplicación caiga al `.env`.
- Crear sala y platos, ajustar tasa/IVA, abrir mesas y generar pedidos usando únicamente los controles visibles. Leer tablas, etiquetas y alertas Swing como los datos disponibles para el operador.
- Simular impresora térmica con un ejecutable `lp` temporal que retorna éxito; guardar PDFs y respaldos debajo del directorio temporal del E2E para no depender de impresoras, visores ni carpetas del proyecto.

## Matriz de platos

| Moneda principal | Exento | IVA agregado al precio | IVA incluido en el precio |
|---|---|---|---|
| USD | USD 3.00 | USD 10.00 | USD 11.60 |
| VES (bolívares) | VES 120.00 | VES 400.00 | VES 464.00 |

La tasa inicial será 40.0000 y el IVA 16%. La matriz verifica precios/ficha fiscal, combinaciones y productos individuales. A mitad de jornada se cambia desde Configuración a tasa 42.0000 e IVA 10%; las cuentas ya abiertas deben conservar el IVA de sus líneas, y los platos/pedidos nuevos deben reflejar el porcentaje nuevo.

## Recorrido

1. Login real y carga de la ventana principal.
2. Configurar por UI la tasa inicial, IVA y salida térmica; desactivar por UI la impresión automática de comandas para que el entorno no invoque dispositivos.
3. Registrar una sala de ocho mesas y los seis productos con nombres únicos. Validar en la ficha el precio final, base, IVA, moneda y banderas.
4. Mesa 1: enviar primera ronda con los seis perfiles y verificar carrito, condición fiscal y total USD 52.40 / VES 2096.00.
5. Mesa 1: reabrir la cuenta, agregar una unidad extra de EX_USD y MAS_VES, verificar cantidades y total USD 67.00 / VES 2680.00.
6. Mesas 2–7: abrir un pedido individual por cada perfil. Cobrar EX_VES solo en VES y EX_USD solo en USD. En MAS_USD probar un pago insuficiente (11.59) y uno excedido (11.61), comprobar las alertas y después aceptar 11.60.
7. Cambiar la tasa a 42.0000 e IVA a 10.00 por Configuración. Comprobar que las tres facturas ya pagadas mantienen su total y método, mientras la cuenta de mesa 1 se recalcula a USD 65.20 / VES 2738.40 y conserva las líneas con IVA 16%.
8. Cobrar MAS_VES, INC_USD, INC_VES y la cuenta mixta de mesa 1. Para esta última usar 1369.20 VES + 32.60 USD.
9. En una mesa liberada, crear un nuevo pedido con MAS_USD e INC_VES; validar IVA 10%, total USD 22.04 / VES 926.00 y cobrar en VES.
10. Mesa 8: crear una cuenta pendiente y cancelarla mediante “No Pagó (Fuga)”. Comprobar estado anulado y que la mesa quede libre.
11. En el historial filtrado por hoy, conciliar desde las filas visibles 8 facturas, 1 cuenta anulada, 0 pendientes y VES 5783.60. Solicitar Cierre Z desde Caja y comprobar el resultado visible de la aplicación y el despacho al `lp` simulado.

## Implementación y límites

- `test/Vista/DiaE2EFixture.java` solo contiene valores de la matriz, nombres únicos y validación de las propiedades de conexión explícitas. No contiene SQL, JDBC, DAOs ni preparación de estado.
- `test/Vista/DiaE2ERobot.java` usa `Robot` para mouse/teclado. La reflexión solo localiza controles para poder clicar y leer su estado visible/modelo de tabla; no escribe componentes ni dispara listeners.
- `test/Vista/DiaCompletoFiscalE2EVirtual.java` divide el recorrido en fases y valida mensajes, totales, filas e importes que aparecen en la UI. No hace llamadas directas al dominio ni a persistencia.
- `e2e_dia_completo_virtual.sh` crea y elimina su proyecto Compose/volumen propio, usa un directorio temporal y prepara el stub de impresión. `e2e_virtual.sh` mantiene exclusivo el display `:99` y elimina solo el Xvfb que inició.
- No se ejecuta la prueba de aceptación durante la implementación. `ant -q compile-test` compila test sources pero no ejecuta tests.

## Criterios de aceptación

1. La clase exige `DB_URL`, `DB_USER` y `DB_PASSWORD` explícitos de la BD desechable antes de iniciar la aplicación.
2. Todo flujo funcional (preparación, operación y verificación) ocurre por la UI mediante `Robot`; no hay atajos DAO/JDBC ni SQL en el test.
3. La prueba cubre los seis cruces moneda × IVA, productos individuales y combinados, ronda adicional, cambio de tasa/IVA, tres modalidades de pago, rechazo de montos incorrectos, cuenta de fuga y cierre Z.
4. Las pantallas muestran los totales y subtotales esperados a dos decimales y las alertas de pago corto/excedido.
5. La ejecución queda aislada en Xvfb y un proyecto MySQL temporal; fallo o éxito cierra la aplicación y limpia solo los recursos propios.
