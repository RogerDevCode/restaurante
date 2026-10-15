# AGENTS.md — Scripts del proyecto Restaurante 2026

> Para contexto del sistema (arquitectura, BD, build): ver `CONTEXT.md`.
> Convenciones: Java 21, Ant (`ant -q compile jar`), MySQL vía Docker (`restaurante-db-1`, `admin/admin`), display `DISPLAY=:0.0`. No commitear scratch (`Test*.java`, `test_tab*`, `debug.log`, `target.txt`).

## Build / Deploy (Linux, repo root)

| Script / comando | Uso |
|---|---|
| `ant -q compile jar && cp -f dist/Restaurante.jar Restaurante_Ejecutable/Restaurante.jar` | Compilar + desplegar jar ejecutable (flujo estándar) |
| `./generar_restaurante_ejecutable.sh` | Regenera `Restaurante_Ejecutable/` (compila con Ant, copia jar+`librerias`, zip). Requiere JDK 21 + Ant |
| `./empaquetar_produccion.sh` | Genera `../Restaurante_2026_Produccion.zip` para distribuir |
| `ant test` | Suite unitaria (~398 tests, necesita display) |
| `ant integration-test` | Suite contra MySQL Docker 3307 (`restaurante_test`); levanta/baja compose solo |
| `./e2e_virtual.sh [clase]` | E2E en Xvfb `:99` (1366x711) con mouse/teclado independientes de `:0`; no toca el escritorio del operador. Defecto: `Vista.E2EVirtual` (flujo mesa 1: 1+2 pollos, crea y borra 1 pedido real; exige `-De2e.permitir=SI`, incluido) |
| `ant integration-test` | Suite contra MySQL Docker 3307 (`restaurante_test`); levanta/baja compose solo |

## Arranque (producción)

| Script / comando | Uso |
|---|---|
| `cd Restaurante_Ejecutable && DISPLAY=:0.0 java -jar Restaurante.jar` | Arranque manual |
| `./iniciar_restaurante.sh` (root o `Restaurante_Ejecutable/`) | Lanza `Restaurante.jar` del dir actual; exige Java 21 |
| `java -jar Restaurante.jar --migrate-db` | Solo valida migración de esquema y sale |
| Login demo: `admin` / `admin`. Instancia única por puerto 49200 |

## Base de datos

| Script / comando | Uso |
|---|---|
| `./inicio_fresco.sh [--sin-semana] [--forzar]` | **Destructivo**: backup → vacía 16 tablas (preserva `schema_migrations`) → siembra maestra (4 salas, 6 categorías, 20 platos, 6 mesoneros, clientes, config, admin) → semana demo 7 días + cierres → verifica. Vars: `CONTENEDOR/MYSQL_USER/MYSQL_PASS/MYSQL_DB`. Pide `SI` salvo `--forzar` |
| `MYSQL_DB=restaurante_prueba ./inicio_fresco.sh --forzar` | Probar el script en BD scratch (no toca producción) |
| `actualizar_bd.bat` | Migración segura Windows (JDBC, con postcondiciones) |
| `BD.sql` + `actualizar_bd.sql` + `db/migrations/actualizar_bd.sql` | Schema fresco + migraciones idempotentes (los 2 últimos **deben ser byte-idénticos**: lo exige `InstaladoresSeguridadTest`) |

## Instalación Windows (un clic, requieren Admin)

| Script | Uso |
|---|---|
| `instalacion_completa_un_clic.bat` | Todo en uno: MySQL + BD + ejecutable |
| `instalar_mysql_y_bd.bat` / `.ps1` | Solo MySQL + base nueva |
| `generar_restaurante_ejecutable.bat` / `.ps1` / `.sh` | Recompila y reempaqueta `Restaurante_Ejecutable/` por plataforma |
| `instalar_acceso_directo.bat` / `.ps1` | Crea acceso directo del sistema |
| `iniciar_restaurante.bat` | Arranque en Windows |
| `listar_impresoras.bat` | Lista impresoras (nombre, default, puerto, driver) para configurar tickera |
| `configurar_mysql.ps1` | Configura/eleva MySQL en Windows |

## Notas para agentes
- DB prod: `docker exec restaurante-db-1 mysql -uadmin -padmin restaurante -e "..."`. No hay `mysql` CLI en el host.
- **Tests E2E**: SIEMPRE deben ejecutarse en una ventana virtual aislada (mediante `./e2e_virtual.sh [clase]` sobre Xvfb `:99`), con teclado y mouse virtuales propios, sin interferir jamás con el escritorio ni el cursor del operador.
- No reiniciar la app en horario de servicio sin pedirlo; el jar desplegado se activa al próximo arranque.
- Los tests unitarios tocan la BD real (vía `.env`); no correr `ant test` tras sembrar datos limpios salvo necesario.
- `pkill -f "[R]estaurante.jar"` (con corchetes; el patrón plano se auto-matchea).
- **Esquema de BD vs Clave/Valor**: Antes de agregar una columna a la base de datos (especialmente en `config`), evaluar primero si se puede resolver la necesidad con el modelo clave/valor de configuración (`configuracion_sistema` vía `ConfigClaves` y `ConfiguracionRepositorio`), evitando alteraciones de esquema (DDL) innecesarias.
