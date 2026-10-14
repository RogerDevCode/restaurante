# Fiscal Line Details and Unique User Names Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist each order line's VAT applicability, validate tax from those snapshots, enforce valid unique user names, and correct the access instructions.

**Architecture:** Add a nullable tax snapshot to order details and backfill historical rows from the current dish catalog during schema version 8 (unmatched dishes default to taxable). Recompute existing order totals and currency amounts during that migration. New order writes require explicit snapshots and derive taxable base and VAT from those lines. Add normalized unique username indexing through the JDBC schema synchronizer and SQL migration, while application validation enforces the username format.

**Tech Stack:** Java 21, Swing, JDBC, MySQL 8.4, Ant, JUnit 4.

## Global Constraints

- Follow `AGENTS.md`; never run E2E tests on the operator's desktop.
- Do not run the broad `ant test` suite because it may access the real database.
- Keep `actualizar_bd.sql` and `db/migrations/actualizar_bd.sql` byte-identical.
- Recalculate historical details from the current catalog; unmatched dishes default to taxable.

---

### Task 1: Carry fiscal applicability in order details

**Files:**
- Modify `src/Modelo/DetallePedido.java`
- Modify `src/Modelo/CalculoFiscalRecord.java`
- Modify `src/Modelo/PedidosDao.java`
- Modify `src/Servicio/PedidoServicio.java`
- Modify `src/Servicio/GeneradorPdfPedido.java`
- Modify `src/Vista/Sistema.java`
- Modify `BD.sql`
- Modify `src/infraestructura/MigradorEsquemaJdbc.java`
- Modify both idempotent schema migration scripts
- Modify fiscal service and DAO unit tests

- [x] Add nullable `Boolean aplicaIva` to `DetallePedido`; null means legacy/unknown.
- [x] Add fiscal calculation from line price, quantity, and applicability; reject null flags on new writes.
- [x] Persist and read the snapshot on create/update; validate the exact line-derived VAT in service, DAO, and PDF generation when all flags are present.
- [x] Preserve legacy PDF validation for old rows with null snapshots.
- [x] Populate the cart snapshot when a dish is added and preserve it when loading a pending order for editing.
- [x] Add the SQL column, backfill legacy rows from the current catalog, then enforce `NOT NULL`; bump JDBC schema version to 8 and recalculate stored order totals. Update fresh schema and both identical SQL migrations.
- [x] Add tests for taxable/exempt mixtures, tampered totals, persistence mapping, and legacy rows.

### Task 2: Validate and uniquely index usernames

**Files:**
- Create `src/Modelo/NombreUsuario.java`
- Modify `src/Modelo/LoginDao.java`
- Modify `src/infraestructura/MigradorEsquemaJdbc.java`
- Modify `BD.sql` and both identical schema migration scripts
- Add tests for accepted/rejected names and duplicate-normalized names

- [x] Validate names at registration: 3–60 Unicode letters/marks/digits, spaces, periods, underscores, or hyphens; no leading/trailing spaces.
- [x] Normalize and store trimmed names.
- [x] Validate existing names before migration and add a unique generated normalized-name column/index; fail clearly on invalid or duplicate legacy names.
- [x] Add the same idempotent schema change to both SQL migration files.

### Task 3: Correct access instructions and validate the change

**Files:**
- Modify `Restaurante_Ejecutable/LEEME_INSTRUCCIONES.txt`
- Add/update targeted test coverage

- [x] Document the two administrator accounts that are actually seeded and their current shared demo password; state that no assistant account is seeded.
- [x] Run compilation and targeted tests; the adversarial reviewer also ran `ant integration-test` against its isolated fixed `restaurante_test` Compose database. No production database or E2E was used.
- [x] Request adversarial review of the diff and run the reviewer's safe checks; it found the empty-order header case and suggested enforcing `NOT NULL`, both fixed and revalidated.
- [x] Recheck SQL migration file byte identity and inspect final diff.
