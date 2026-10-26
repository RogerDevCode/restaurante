# Concatenador Inteligente de PDFs — Sermig

Este módulo permite unir automáticamente múltiples archivos PDF en un único documento de salida llamado **`sermig.pdf`**.

## Características Principales

1. **Ordenación Numérica Natural**:
   - Ordena los archivos según el número con el que inician en el nombre:
     - `1-carta.pdf`
     - `2-cedula.pdf`
     - `3-planilla.pdf`
     - `...`
     - `10-anexo.pdf`
   - Los números de dos o más dígitos (como el `10`, `11`) se ordenan después del `9` y no después del `1`.

2. **Ajuste Inteligente de Tamaño de Hoja (Heterogeneidad)**:
   - Los documentos pueden tener páginas de diferentes tamaños (recibos pequeños, facturas, A4, oficio, planos, cartas).
   - El script escala proporcionalmente cada hoja para que encaje de forma óptima en el tamaño estándar **Carta** (Letter), sin recortar márgenes ni distorsionar tipografías.
   - Si una página es horizontal (Landscape), el script la mantiene en horizontal ajustada a la proporción Carta Horizontal.

3. **Protección y Limpieza**:
   - Omite automáticamente el archivo resultante `sermig.pdf` para no duplicarlo si se vuelve a ejecutar.
   - Ignora archivos temporales (`~*`, `.*`).

---

## Modo de Uso

### 1. Uso Básico (Por defecto)
Simplemente coloca tus archivos PDF dentro de la carpeta `sermig/` y ejecuta:

```bash
python3 concatenar_sermig.py
```
*(O también `./sermig.py`)*

El archivo resultante se generará en la misma carpeta como **`sermig.pdf`**.

---

### 2. Opciones Avanzadas (Parámetros)

| Parámetro | Descripción | Valor por Defecto |
|---|---|---|
| `--formato` / `-f` | Tamaño de hoja (`carta`, `a4`, `oficio`, `folio`) | `carta` |
| `--margen` / `-m` | Margen de seguridad en milímetros | `5.0` mm |
| `--salida` / `-s` | Nombre del PDF resultante | `sermig.pdf` |
| `--dir` / `-d` | Directorio donde buscar los PDFs | Carpeta actual (`.`) |
| `--forzar-vertical` | Rota páginas horizontales a vertical | Desactivado |
| `--sin-escalar` | Concatena sin cambiar el tamaño original de las hojas | Desactivado |

#### Ejemplos:
- Generar en formato A4:
  ```bash
  python3 concatenar_sermig.py --formato a4
  ```
- Generar con margen de 10 mm:
  ```bash
  python3 concatenar_sermig.py --margen 10
  ```
- Unir sin escalar hojas (mantener tamaños heterogéneos originales):
  ```bash
  python3 concatenar_sermig.py --sin-escalar
  ```
