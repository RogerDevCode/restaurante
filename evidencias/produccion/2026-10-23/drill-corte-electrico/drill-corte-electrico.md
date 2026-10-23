# Drill corte eléctrico: integridad + auto-recuperación de la BD

Escenario del local: cortes de luz repetidos. Pregunta: ¿se pierden datos si la
app se cierra por la X o si cae la luz mientras la BD está escribiendo?

- **Cerrar por la X**: ya cubierto por el gate E2E (corridas 4c5d14f):
  `evidencias/produccion/2026-10-23/e2e-cierre-sesion-datos/` — con mesas
  abiertas, "¡ADVERTENCIA: Mesas abiertas!" → *Sí* → `System.exit(0)`; y en
  post-mortem /SQL la cuenta persiste `PENDIENTE` con su línea exacta.
- **Corte eléctrico de la BD**: este drill (`drill_corte_electrico.sh`,
  commitraftado en la raíz del repo) mata el contenedor MySQL con SIGKILL en
  plena escritura — corte eléctrico real (sin apagado, sin flush, sin tracker).

## Marco de durabilidad de la instalación (MySQL 8.4.11, valores por defecto)

| Variable | Valor | Significado ante corte de luz |
|---|---|---|
| `innodb_flush_log_at_trx_commit` | **1** | el redo log se vacía a disco en **cada COMMIT** ⇐ un COMMIT confirmado a la app ya está en disco |
| `sync_binlog` | **1** | binlog sincrónico por commit |
| `innodb_doublewrite` | **ON** | protege páginas partidas por corte de energía |
| `innodb_flush_method` | `O_DIRECT` | doble caché evitada |
| `docker-compose.yml` | sin overrides | producción usa estos mismos valores por defecto + volumen nombrado (`restaurante_mysql_data`) + `restart: unless-stopped` + healthcheck |

## Fases del drill (BD desechable `restaurante_test` 3307, compose `-p drillcorte`)

1. Esquema completo + datos base (`./inicializar_bd_test.sh --datos`).
2. **Corte #1**: escritor con acuses en host (cada INSERT autocommit que recibió
   OK del servidor queda anotado en un recibo en `/tmp/opencode`) + transacción
  BEGIN…SLEEP(12)…COMMIT a mitad de camino; `docker compose kill --signal=KILL db`.
3. `docker start` del MISMO contenedor (mismo volumen) → **XA crash recovery**
   aparece terminado en los logs sin intervención.
4. Conciliación: filas del escritor en BD == acuses (0 perdidas); la transacción
   sin COMMIT no dejó residuos (0) ⇒ durabilidad + atomicidad.
5. **Corte #2** repetido: 13/13 acuses de nuevo, filas del corte #1 intactas.
6. `CHECK TABLE pedidos/detalle_pedidos/usuarios/configuracion_sistema` OK.

## Resultado (corrida oficial log completa: `corridaoficial.log`)

| Comprobación | Dato | Resultado |
|---|---|---|
| Config de durabilidad | flush_log_at_trx_commit=1, sync_binlog=1, doublewrite=ON | PASS |
| Corte #1 (SIGKILL en escritura) | 13 acuses ↔ 13 filas del escritor 1 en BD; borrador sin COMMIT: 0 residuos | `CORTE_1_OK` |
| Recuperación InnoDB automática | `XA crash recovery finished` en logs tras cada restart | PASS |
| Corte #2 (repetido, mismo volumen) | 13/13 del escritor 2 + las 13 del corte #1 intactas | `CORTE_2_OK` |
| Integridad de esquema tras 2 cortes | `CHECK TABLE` OK ×4 | PASS |
| Marcador global | `DRILL_CORTE_ELECTRICO_OK`, EXIT=0 | PASS |

## Semántica operativa (traducción al restaurant)

- **Se pierde un COMMIT ya confirmado en pantalla: NUNCA** (flush por commit).
- Lo que sí se pierde, por diseño, es lo que estaba **en memoria de la app y
  nunca se envió** (borrador del carrito sin "Generar pedido"): al morir el
  proceso no llegó al servidor y por eso no puede estar en la BD — la cuenta
  realmente enviada a cocina sí persiste.
- Transacción in-flight al momento del corte: rollback limpio, sin filas
  huérfanas ni medias cuentas (comprobado por el `borradores sin COMMIT: 0`).
- Tras un corte: docker auto-reinicia (`restart: unless-stopped`) y MySQL se
  recupera solo (XA crash recovery); la app reconecta por su pool al reintento.
