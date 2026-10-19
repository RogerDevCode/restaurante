# Comandas Incrementales Implementation Plan

> **For agentic workers:** Implement inline in this session using the task checkboxes below; validate each persistence and UI boundary before continuing.

**Goal:** A kitchen dispatch prints only the quantities added since the previous dispatch, while removals and edits to already dispatched items produce a separate adjustment slip and dispatches remain auditable and reprintable.

**Architecture:** Persist immutable dispatch headers and line snapshots in the same JDBC transaction as order creation/update. During an update, lock the pending order, compare prior details with submitted details by product identity and preparation comment, and record positive differences as `PREPARAR` and negative differences as `RETIRAR`; a comment change is a removal plus a new preparation. Legacy pending orders receive a non-printable baseline during migration so their existing items are not emitted again. Views receive a typed dispatch result and send only its immutable snapshot to the kitchen printer.

**Tech Stack:** Java 21, Swing, JDBC, MySQL 8.4, Ant, JUnit 4.

## Global Constraints

- All runtime SQL uses prepared statements and migrations remain idempotent.
- New or modified dependencies use constructor injection; no DI framework is introduced.
- E2E tests use only the UI and run through `./e2e_dia_completo_virtual.sh` on Xvfb `:99` with disposable MySQL.
- Integration tests use only the Compose database on port 3307; never use the production database.
- Do not deploy or restart the active application as part of this work.

---

### Task 1: Define immutable dispatch result and difference rules

**Files:**
- Create: `src/Modelo/ComandaMarcha.java`
- Create: `src/Modelo/MovimientoComanda.java`
- Test: `test/Modelo/ComandaMarchaTest.java`

**Interfaces:**
- `MovimientoComanda` contains action (`PREPARAR` or `RETIRAR`) and a defensive copy of one `DetallePedido` with a positive quantity.
- `ComandaMarcha` contains order ID, march ID, sequence, type (`INICIAL`, `INCREMENTO`, `AJUSTE`, `BASE_LEGADA`) and immutable movements.
- Matching key: `id_plato` when present, otherwise normalized product name, plus trimmed case-folded comment. Price and tax changes do not create another kitchen preparation.

- [x] Test same product on later dispatch increments quantity, different comment creates distinct preparation, quantity reduction creates `RETIRAR`, and no change creates no dispatch.
- [ ] Run the focused test and verify it fails before the model exists.
- [x] Implement immutable value types and pure difference calculation.
- [x] Re-run focused tests: 4/4 passed.

### Task 2: Add dispatch journal and legacy baseline migration

**Files:**
- Modify: `src/infraestructura/MigradorEsquemaJdbc.java`
- Modify: `BD.sql`
- Modify: `actualizar_bd.sql`
- Modify: `db/migrations/actualizar_bd.sql`
- Test: `test/infraestructura/MigradorEsquemaJdbcTest.java`

**Interfaces:**
- `comandas_marchas`: immutable header, unique `(id_pedido, secuencia)`, foreign key to `pedidos` with `ON DELETE RESTRICT`.
- `comandas_marchas_detalle`: immutable action and product snapshot, positive quantity, foreign key to march header with `ON DELETE RESTRICT`.
- Migration backfills each existing pending order with one `BASE_LEGADA` header and copies all its current details. Unique constraints make reruns safe.

- [x] Add migration-v15 schema creation and legacy baseline insert guarded by absence of an existing march for the order.
- [x] Mirror fresh-schema DDL and idempotent SQL migration in both byte-identical SQL files.
- [ ] Validate migration twice in an integration database and prove one baseline per existing pending order.

### Task 3: Persist creation/update plus dispatch atomically

**Files:**
- Modify: `src/Modelo/PedidosRepositorio.java`
- Modify: `src/Modelo/PedidosDao.java`
- Modify: `src/Servicio/PedidoServicio.java`
- Modify: `src/Controlador/PedidosControlador.java`
- Test: `test/integracion/ComandasIncrementalesIT.java`

**Interfaces:**
- Add repository/controller methods returning `ComandaMarcha` for create and update; retain existing boolean/int methods as compatibility wrappers.
- Creation stores the initial full dispatch. Update uses `SELECT ... FOR UPDATE`, compares old and submitted quantities, rewrites order details and stores any `INCREMENTO`/`AJUSTE` journal rows in one transaction.
- Add exact march lookup for reprint; no-change updates return an empty result and cannot re-emit a prior march.

- [x] Add disposable-MySQL cases for initial dispatch, repeated same product, quantity increase/decrease, comment change, no-op update, skipped printing with later accumulation, and concurrent same-quantity updates.
- [x] Implement JDBC writes and reads using prepared statements and explicit commit/rollback.
- [x] Run `ant -q integration-test`: 61 tests, 0 failures/errors, 1 skipped; 5/5 new integration cases passed.
- [ ] Add an explicit rollback-failure injection case.

### Task 4: Print only the returned march snapshot

**Files:**
- Modify: `src/Servicio/ServicioComandaCocina.java`
- Modify: `src/Vista/PanelAtencionMesasSplit.java`
- Modify: `src/Vista/Sistema.java`
- Test: `test/Servicio/ServicioComandaCocinaTest.java`

**Interfaces:**
- The printer accepts `ComandaMarcha`, includes order/march sequence and labels each movement `PREPARAR` or `RETIRAR`, and does not query or rebuild accumulated order details.
- Both classic and Split order flows pass the result returned by transactional persistence to the asynchronous printer.
- Reprint reads one exact march ID and uses the same renderer.

- [x] Add PDF tests proving the second dispatch contains only the incremental quantity and adjustment slips label removed items.
- [x] Replace full-detail print calls in both UI paths with typed results.
- [x] Run focused print tests (5/5), `ant -q compile-test`, and UI-only E2E on the isolated wrapper (`Vista.E2EVirtual`: `E2E_OK`).
- [x] Add a history action to list persisted marches and reprint one exact snapshot; reprints use the shared renderer and are audited.

### Task 5: Document and close the production gate

**Files:**
- Modify: `plan-produccion.md`
- Modify: `CONTEXT.md`

- [x] Record migration, integration, PDF, and E2E evidence and external printer gates in `plan-produccion.md` and `CONTEXT.md`.
- [x] Run `git diff --check` and verify both SQL migration files are byte-identical.
- [ ] Commit and push the reviewed change to the feature branch; do not deploy or restart the active instance.
