# Cross-Instance Administrator Mutation Lock Implementation Plan

> **For agentic workers:** Execute this plan task by task in the current session. Keep each change focused and validate it with the disposable MySQL integration environment.

**Goal:** Serialize administrator deactivation/deletion across all Restaurante processes connected to the same MySQL database so concurrent mutations cannot remove the final active Administrator.

**Architecture:** Keep `LoginDao`'s existing in-process monitor and transaction. Add a MySQL advisory lock on the same connection before reading administrator state, fail closed when the lock cannot be acquired, and release it in `finally`. Prove the cross-connection behavior with an integration test that holds the named lock from a separate connection.

**Tech Stack:** Java 21, JDBC, MySQL 8.4, Apache Ant, JUnit 4.

## Global Constraints

- Use only the disposable `restaurante_test` database on `127.0.0.1:3307` for integration tests.
- Preserve the active production application on port 49200; do not restart or deploy it.
- Keep administrator validation and mutation in one transaction.
- Release the MySQL advisory lock before returning its JDBC connection to the pool.
- Record evidence as `command → output file → PASS/FLAKY/FAIL` in `bitacora-contexto-produccion.md`.

---

### Task 1: Add a regression test for cross-connection lock contention

**Files:**
- Modify: `test/integracion/MySqlIntegrationIT.java`

**Interfaces:**
- Consumes: `LoginDao.desactivarUsuario(int)` and the integration helper `conexion()`.
- Produces: `mutacionAdministradorEsperaLockCompartidoDeOtraConexion()` proving the DAO waits while an independent JDBC connection owns the advisory lock.

- [x] **Step 1: Add the failing integration test**

Add this test method to `MySqlIntegrationIT`:

```java
@Test
public void mutacionAdministradorEsperaLockCompartidoDeOtraConexion() throws Exception {
    String correo = "it-admin-lock-" + UUID.randomUUID() + "@restaurante.test";
    int idAdmin = -1;
    String lock = "restaurante_admin_mutation_lock";
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch inicioMutacion = new CountDownLatch(1);
    boolean lockAdquirido = false;
    try (Connection conexionLock = conexion()) {
        try (Connection conexionInsert = conexion();
             PreparedStatement insertar = conexionInsert.prepareStatement(
                     "INSERT INTO usuarios (nombre, correo, pass, rol, activo) VALUES (?, ?, ?, 'Administrador', 1)",
                     Statement.RETURN_GENERATED_KEYS)) {
            insertar.setString(1, "Administrador Lock Integración");
            insertar.setString(2, correo);
            insertar.setString(3, new PasswordHasher().hash("ClaveIntegracion2026".toCharArray()));
            assertEquals(1, insertar.executeUpdate());
            try (ResultSet claves = insertar.getGeneratedKeys()) {
                assertTrue(claves.next());
                idAdmin = claves.getInt(1);
            }
        }

        try (PreparedStatement adquirir = conexionLock.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            adquirir.setString(1, lock);
            try (ResultSet resultado = adquirir.executeQuery()) {
                assertTrue(resultado.next());
                assertEquals(1, resultado.getInt(1));
                lockAdquirido = true;
            }
        }

        final int idObjetivo = idAdmin;
        Future<Boolean> mutacion = executor.submit(() -> {
            inicioMutacion.countDown();
            return new LoginDao().desactivarUsuario(idObjetivo);
        });
        assertTrue("La tarea de mutación debe iniciar", inicioMutacion.await(2, java.util.concurrent.TimeUnit.SECONDS));
        Thread.sleep(400);
        assertFalse("La mutación debe esperar el lock de otra conexión", mutacion.isDone());

        try (PreparedStatement liberar = conexionLock.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            liberar.setString(1, lock);
            liberar.executeQuery().close();
        }
        assertTrue("La mutación debe continuar al liberar el lock", mutacion.get(10, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
        executor.shutdownNow();
        if (lockAdquirido) {
            try (Connection liberarConexion = conexion();
                 PreparedStatement liberar = liberarConexion.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                liberar.setString(1, "restaurante_admin_mutation_lock");
                liberar.executeQuery().close();
            }
        }
        try (Connection limpieza = conexion(); PreparedStatement borrar = limpieza.prepareStatement(
                "DELETE FROM usuarios WHERE correo=?")) {
            borrar.setString(1, correo);
            borrar.executeUpdate();
        }
    }
}
```

- [x] **Step 2: Run the integration test before the fix**

Run: `ant -q integration-test`
Expected: **FAIL** because the current DAO does not wait for a named lock held by another JDBC connection. The `finally` block removes the fixture.

### Task 2: Acquire and release the shared MySQL lock in the DAO

**Files:**
- Modify: `src/Modelo/LoginDao.java`
- Test: `test/integracion/MySqlIntegrationIT.java`

**Interfaces:**
- Consumes: the integration regression test from Task 1.
- Produces: a fail-closed MySQL advisory-lock boundary around `ejecutarMutacionUsuarioProtegida(int, String)`.

- [x] **Step 1: Add the lock constant and JDBC helpers**

Add `private static final String LOCK_MUTACION_ADMIN_BD = "restaurante_admin_mutation_lock";`. Add a helper that executes `SELECT GET_LOCK(?, 10)` and throws `SQLException` unless the result is `1`. Add a release helper that executes `SELECT RELEASE_LOCK(?)` and logs a warning if explicit release fails.

- [x] **Step 2: Wrap the existing protected mutation with the database lock**

Inside the existing `synchronized (LOCK_MUTACION_ADMIN)` block, acquire the MySQL lock on the same `Connection` before `setAutoCommit(false)`. Keep the existing target-row lock, primario check, count of other active Administrators, mutation, and commit/rollback inside the existing transaction. Release the MySQL lock in a `finally` block before the try-with-resources closes the connection. If acquisition times out or errors, propagate a `DataAccessException` without executing the mutation.

- [x] **Step 3: Run integration and lifecycle tests**

Run: `ant -q integration-test`
Expected: **PASS**, including the new cross-connection contention case and the existing last-Administrator protection test.

Run: `ant -q test-single -Djavac.includes=Modelo/LoginDaoCicloVidaTest.java -Dtest.includes=Modelo/LoginDaoCicloVidaTest.java`
Expected: **PASS**, all `LoginDaoCicloVidaTest` cases.

### Task 3: Record the production-plan evidence

**Files:**
- Modify: `plan-produccion.md`
- Modify: `bitacora-contexto-produccion.md`
- Create/update: `evidencias/produccion/2026-10-19/admin-lock-integration.log`

**Interfaces:**
- Consumes: the passing integration and lifecycle results from Task 2.
- Produces: explicit evidence and an accurate remaining gate for administrator mutation safety.

- [x] **Step 1: Capture the validation command and output**

Run: `set -o pipefail && ant -q integration-test 2>&1 | tee evidencias/produccion/2026-10-19/admin-lock-integration.log`
Expected: exit code `0`, `BUILD SUCCESSFUL`, and JUnit XML reports with zero failures and errors.

- [x] **Step 2: Update the plan without overstating scope**

Mark the cross-instance last-Administrator mutation requirement verified only after the integration result passes. Leave hot session revocation and full authorization coverage open until separately audited. Add a bitacora row in the format `command → output path → PASS` with the test counts and database isolation.
