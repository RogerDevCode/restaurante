# Plan de Implementación: Sistema Multi-Híbrido de Selección de Cantidad

## 1. Reestructuración de la Interfaz Gráfica (UI)
Se modificará el panel de cantidades en `Sistema.java` (actualmente `pnlBotonesCantidad` que solo tiene `+` y `-`):
*   **Nuevo Panel Híbrido:** Reemplazará al actual y se ubicará debajo o al lado de la tabla del carrito (`tableMenu`).
*   **Grilla Rápida (1 al 10):** Un `JPanel` con `GridLayout(2, 5)` conteniendo botones del 1 al 10.
    *   **Acción:** Al hacer clic en un número (ej. `5`), la cantidad del plato actualmente seleccionado en el carrito pasará a ser exactamente 5, recalculando el subtotal instantáneamente.
*   **Control de Precisión (+ / input / -):** Un `JPanel` horizontal que mantenga los botones `-` y `+` pero con un `JTextField` (`txtCantidadManual`) en el medio.
    *   **Acción:** El input permitirá escribir cantidades superiores a 10 (ej. `15`) presionando *Enter* para aplicar. Los botones `+` y `-` sumarán o restarán 1 a la cantidad actual, sincronizando siempre el valor reflejado en el `txtCantidadManual`.

## 2. Incremento Rápido por Clic (Tap-to-Increment)
Para maximizar la velocidad del operador (estilo Touch/POS moderno):
*   **MouseListener en la Tabla de Menú (`tblTemPlatos`):** Se añadirá un evento de clic sobre la lista de productos disponibles.
*   **Flujo:** Al hacer clic en un producto:
    1. Si no está en el carrito, se añade con cantidad `1`.
    2. Si ya está en el carrito, se selecciona automáticamente en el carrito y **su cantidad se incrementa en `1`** (sin tener que pulsar el botón `+` explícitamente).

## 3. Modificaciones Lógicas Internas
*   Creación de un método centralizado `setCantidadSeleccionada(int nuevaCantidad)` en `Sistema.java` que:
    *   Valide que la cantidad sea `> 0`.
    *   Actualice la fila en `tableMenu`.
    *   Actualice el texto del `txtCantidadManual`.
    *   Ejecute `TotalPagar()` para actualizar los montos de la venta.
*   Protección del campo de texto numérico para evitar excepciones (`NumberFormatException`) si el cajero teclea letras.

## 4. Pruebas Unitarias y Flujo
*   Se desarrollará un `TestCase` simulando la inserción de platos, clics en la grilla y escritura en el `JTextField` para validar que el importe subtotal coincida con `precio * cantidad`.
