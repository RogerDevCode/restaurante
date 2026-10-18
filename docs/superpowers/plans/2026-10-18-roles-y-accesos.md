# Roles y accesos configurables Implementation Plan

> **For agentic workers:** Implement inline from this plan. Each step is a separately reviewable change.

**Goal:** Add five restaurant roles, an administrator-managed permission matrix, and secure enforcement at service and UI boundaries.

**Architecture:** Keep the permission catalog in application code and persist role/permission overrides in the existing `configuracion_sistema` key/value store. Load an immutable permission snapshot for each login session, fail closed on storage errors and unknown identifiers, and keep Administrator as an unconditional all-permissions bypass. Enforce permissions in services/controllers as well as disabling inaccessible tabs and controls.

**Tech Stack:** Java 21, Swing, JDBC, MySQL key/value configuration, Ant.

## Global Constraints

- Preserve the existing Asistente behavior and the Administrador full-access invariant.
- Do not add schema columns/tables; persist permission settings through `configuracion_sistema`.
- Do not overwrite pre-existing user changes in `BD.sql`, migration SQL, `MigradorEsquemaJdbc.java`, category classes, or the unrelated category changes in `Sistema.java`.
- Fail closed for inactive users, unknown roles/permissions, malformed values, and permission-store read failures.
- Do not add or run tests unless the user requests testing; keep changes suitable for manual UI review.

---

## File map

- Create `src/Servicio/RolesSistema.java`: canonical role identifiers, labels, and validation.
- Extend `src/Servicio/PoliticaAcceso.java`: stable permission identifiers and UI labels/groups.
- Create `src/Servicio/ConfiguracionAccesosServicio.java`: secure defaults, strict load/save, immutable session snapshots.
- Modify `src/Modelo/ConfiguracionSistemaDao.java`: add strict key/value loading for authorization data without changing existing tolerant callers.
- Modify `src/Modelo/LoginDao.java` and `src/Vista/Sistema.java`: accept/display the new roles while retaining Asistente.
- Modify `src/Servicio/PoliticaAcceso.java`: use the session permission snapshot and preserve the immutable Administrator bypass.
- Modify order services/controllers to separate open-order edits/cancellations, collection, history, and invoice reversal permissions.
- Modify `src/Vista/Sistema.java`: administrator-only matrix configuration and tab/control gating.

## Task 1: Canonical roles and permissions

1. Add canonical IDs for `Administrador`, `Asistente`, `Mesonero`, `Cocinero`, `Cajero`, `Maitre`, and `Host`; validate exact identifiers at user creation and login.
2. Add permission IDs for screen consultation and each protected operation. Keep invoice reversal, user administration, role matrix editing, and global configuration separate from routine order management.
3. Make unknown role or permission identifiers deny access; legacy service constructors without an authenticated policy use a deny-all policy.

## Task 2: Secure permission persistence and session policy

1. Add a strict configuration read method that throws on SQL failures rather than returning a default or partial map.
2. Define defaults: Asistente keeps current access; Mesonero consults rooms/menu and registers/updates open orders; Cocinero starts with no grants because the data model cannot distinguish sent rounds from the accumulated table account; Cajero reads pending accounts/history and collects; Maitre manages open service orders and pending account cancellation; Host reads table availability.
3. Persist role permissions under validated, stable keys with values restricted to `0` or `1`; save all changes transactionally with `guardarVarios`.
4. Reject attempts to save Administrator permissions, unknown permission IDs, or unknown roles. Administrator always receives every catalog permission in code.
5. Load one immutable snapshot for each authenticated session; if strict loading fails, block non-administrator access and surface an actionable startup error. Apply changes on next login.

## Task 3: Enforce permissions at operation boundaries

1. Split broad `GESTIONAR_PEDIDOS` checks into consult-history, consult-pending, edit-open-order, cancel-pending-account, collect/finalize, and reverse-paid-invoice actions.
2. Update `ConsultaPedidosServicio`, `PedidosControlador`, `PedidoServicio`, and any `CierreCajaServicio` entry points so each operation checks its own action before repository calls.
3. Do not expose a kitchen read until the database can distinguish newly sent rounds from accumulated table details; a kitchen view over cumulative details can duplicate production.
4. Preserve administrator-only invoice reversal, user management, app configuration, data purge, and role-matrix editing.

## Task 4: Role/access matrix UI

1. Add a `Roles y accesos` section under configuration with role selection and grouped permission checkboxes from the central catalog.
2. Show Administrator checked and disabled; prevent save if the selected role is Administrator or any identifier/value fails validation.
3. Save a complete role snapshot atomically; show a clear failure and retain unsaved form state if persistence fails.
4. Restrict the whole section and all its event handlers to an active Administrator session.
5. On login, use the persisted permission snapshot to enable tabs and controls; keep service-side authorization authoritative even if a hidden control is invoked indirectly.

## Task 5: Integration review (implementation complete; compile verified)

1. Search for every `GESTIONAR_PEDIDOS`, `GESTIONAR_SALAS`, `GESTIONAR_PLATOS`, and `EDITAR_CONFIGURACION` callsite and replace broad checks with the precise permission where applicable.
2. Confirm all new role names are accepted at account creation and authentication, and existing Asistente accounts preserve their current behavior.
3. Inspect the final diff to ensure all pre-existing user modifications remain intact and no permission path depends solely on button visibility.
4. Keep E2E and unit suites unrun unless the user explicitly requests testing.

## Initial permission defaults

| Role | Initial grants |
|---|---|
| Administrador | All catalog permissions; bypass is fixed in code and is not persisted/editable. |
| Asistente | Consult rooms, consult menu, register orders. |
| Mesonero | Consult rooms/menu, register and edit open orders. |
| Cocinero | No grants until a persistent, round-aware kitchen queue is designed. |
| Cajero | Read pending accounts/history and collect/finalize. |
| Maitre | Consult rooms/menu, register/edit open orders, assign server, cancel pending accounts. |
| Host | Consult rooms and table availability. |

Paid-invoice reversal, user and role administration, data purge, and application configuration are Administrator-only defaults.
