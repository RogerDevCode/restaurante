# Adversarial QA Report

## 1. Test Matrix & Scenarios Covered
- **Model Tests (`AdversarialModelTest.java`)**:
  - `Config`: Bound checks on `tasaDolar` for negative, zero, and extreme values (`-36.50`, `0`, `999999999.9999`).
  - `Pedidos`: Calculation constraints and handling extreme values.
  - String Boundaries: Exceeding varchar constraints in memory for `Usuario` and `Config` models.
- **Service Validation Tests (`AdversarialValidationTest.java`)**:
  - Roles & RBAC: Attempting to bypass role permissions with invalid roles, and testing missing rights for "Asistente".
  - Order Edge Cases: Negative total, zero total, order with missing items list, order item amount mismatch vs item list sum.
  - Table Edge Cases: Zero/Negative table number and ID.
  - Menu Items (Platos): Zero price, negative price, extremely large price beyond the allowed DB `DECIMAL(10,2)` bound.
- **Integration Tests (`AdversarialIntegrationIT.java`)**:
  - SQL Injection in Login: `' OR '1'='1` bypass attempt.
  - SQL Injection in User Registration: `'; DROP TABLE pedidos; --` in email field.
  - String bounds against the Database Layer: 10,000-character payload tests.

## 2. Discovered Vulnerabilities & Areas for Strengthening
- **In-Memory String Truncation**: Strings like names and emails are strictly bounded by database structure (causing `DataAccessException` upon save attempt) but lack explicit string length constraint validations directly in the Domain Service Models. Consider adding explicit length validations (e.g. `maxLength = 50`) in Services before calling Repositories.
- **Role Validations during Bootstrapping**: Trying to initialize `PoliticaAcceso` with completely unrecognizable roles throws `ErrorAplicacionException` appropriately, ensuring unknown roles can't proceed.
- **Tasa de Cambio Config Defaulting**: When an extreme/negative dollar rate is passed, `Config.setTasaDolar()` automatically defaults back to `"36.5000"`. While safe, ideally it should throw an explicit validation error notifying the admin of bad input instead of silently succeeding with a fallback value.
- **SQL Injections Resiliency**: The DAO layer uses `PreparedStatement` uniformly, properly nullifying SQL Injection payloads within user text input fields. 

## 3. Verification of Test Executions
The test suites have been successfully compiled and executed against both the mock repositories (for Model and Service validation test runs) and against the integration MySQL instance.

- `ant test -Dtest.includes="**/Adversarial*.java"`: Executed all permutations of negative values, mismatched totals, invalid RBAC attempts successfully in Model & Service layers.
- Integration DB environment resisted `'; DROP TABLE pedidos; --` correctly.
