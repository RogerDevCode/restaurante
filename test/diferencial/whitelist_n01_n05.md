# LISTA BLANCA DE DIFERENCIAS AUTORIZADAS EN PRUEBA DIFERENCIAL (N-01 y N-05)

**Fecha:** 17 de Octubre de 2026  
**Auditor Red Team:** Agente Adversarial de Seguridad y Auditoría Contable-Fiscal  
**Línea Base Golden:** `test/diferencial/golden_corpus_calculo.tsv` (Commit `368d955`)  
**Regla de Oro:** Cualquier diferencia respecto al archivo golden que no esté explícitamente autorizada en esta lista blanca constituye una **REGRESIÓN CONTABLE/FISCAL CRÍTICA**.

---

## 1. Alcance y Corpus

- **Semilla fija:** `20261017L`
- **Tamaño:** 100 pedidos sintéticos con combinatoria de 1 a 20 líneas.
- **Tasas evaluadas:** 36.5000, 45.1500, 45.2500, 50.0000, 1014.0000.
- **Condiciones cubiertas:**
  - `aplica_iva`: 70% gravado, 30% exento.
  - `precio_incluye_iva`: 50% incluido, 50% no incluido.
  - `moneda_base`: 50% USD, 50% VES.

---

## 2. Diferencias Autorizadas por Hallazgo

### A. Hallazgo N-01 — Corrección de IVA en Bs para Líneas Exentas en USD
- **Condición de Activación:**
  El pedido contiene una o más líneas con `aplica_iva = false` y `moneda_base = 'USD'` (`has_exento_usd == true`).
- **Causa en Línea Base:**
  `CalculoMonetarioPedido.java:95` ejecutaba `ivaVes = totalVes.subtract(baseVes)`. Para líneas exentas donde `baseVes == 0`, `ivaVes` absorbió el 100% del monto exento en Bolívares.
- **Diferencia Autorizada en Salidas:**
  $$\Delta(\text{iva\_ves}) = -\sum \text{exento\_ves}_{\text{USD}}$$
  $$\Delta(\text{subtotal\_ves}) = 0.00$$
  $$\Delta(\text{total\_ves}) = 0.00$$
  $$\text{subtotal\_ves}_{\text{nuevo}} + \text{iva\_ves}_{\text{nuevo}} = \text{total\_ves}_{\text{nuevo}}$$
- **Invariantes Inviolables:**
  - Si un pedido no contiene líneas exentas en USD (`has_exento_usd == false`), $\Delta(\text{iva\_ves})$ debe ser estrictamente `0.00`.
  - El redondeo de las líneas gravadas en Bolívares y en USD debe permanecer byte-idéntico.

---

### B. Hallazgo N-05 — Aditividad de Importes USD en Platos VES (Decisión Humana D8)
- **Condición de Activación:**
  El pedido contiene platos clasificados en `moneda_base = 'VES'` (`has_ves == true`).
- **Causa en Línea Base:**
  `subtotalUsd()` e `ivaUsd()` derivaban de divisiones independientes con redondeo HALF_UP (`convertirADolaresReferencia`), provocando discrepancias de hasta $\pm 0.01\text{ USD}$ frente a `totalUsd()`.
- **Decisión D8 Ratificada:**
  *Se prioriza la suma aditiva de líneas ($\text{subtotal} + \text{iva} = \text{total}$ en USD) sobre la división individual.*
- **Diferencia Autorizada en Salidas:**
  $$\text{subtotal\_usd}_{\text{nuevo}} + \text{iva\_usd}_{\text{nuevo}} = \text{total\_usd}_{\text{nuevo}} \quad (\text{diferencia exacto } 0.00)$$
  $$\Delta(\text{total\_ves}) = 0.00 \quad (\text{el importe en moneda de curso legal es sagrado e inmutable})$$
- **Invariantes Inviolables:**
  - Para pedidos denominados exclusivamente en USD (`has_ves == false`), los subtotales y totales no pueden alterarse.
  - La sumatoria total en Bolívares nunca puede sufrir alteraciones por efectos de la política D8.

---

## 3. Matriz de Tolerancia de Campos

| Campo | `has_exento_usd = true` | `has_ves = true` | Resto del Corpus |
|---|:---:|:---:|:---:|
| `subtotal_usd` | Idéntico (`0.00`) | Ajuste máx $\pm 0.01$ (D8) | Idéntico (`0.00`) |
| `iva_usd` | Idéntico (`0.00`) | Ajuste máx $\pm 0.01$ (D8) | Idéntico (`0.00`) |
| `total_usd` | Idéntico (`0.00`) | Idéntico o ajuste D8 | Idéntico (`0.00`) |
| `subtotal_ves` | Idéntico (`0.00`) | Idéntico (`0.00`) | Idéntico (`0.00`) |
| `iva_ves` | Reducción exacta del exento | Idéntico (`0.00`) | Idéntico (`0.00`) |
| `total_ves` | Idéntico (`0.00`) | Idéntico (`0.00`) | Idéntico (`0.00`) |
