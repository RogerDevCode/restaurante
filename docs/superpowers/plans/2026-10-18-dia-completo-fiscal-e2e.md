# E2E virtual de jornada completa y combinatoria fiscal — Plan

**Objetivo:** crear una prueba E2E incisiva del día operativo de restaurante, con la aplicación Swing real, teclado/mouse `Robot`, matriz fiscal USD/VES e IVA, pedidos individuales/mixtos, cobros, fuga y Cierre Z.

**Regla de arquitectura:** preparación, acciones y verificaciones deben ocurrir exclusivamente por la interfaz. No se permite que los fuentes del E2E llamen DAO, repositorio, servicio, controlador o modelo, ni que consulten/modifiquen la BD. La BD efímera solo aísla la ejecución normal de la aplicación.

## Cambios

- `e2e_virtual.sh`: reserva Xvfb `:99` con `flock`, exige que no haya display activo, recuerda su PID y apaga solo su propio proceso. Resuelve el classpath antes del directorio temporal.
- `e2e_dia_completo_virtual.sh`: inicia MySQL con un proyecto Compose único, exige puerto 3307 libre, exporta credenciales explícitas, separa PDFs/respaldos y simula `lp`; su `trap` elimina solo el proyecto y volumen propios.
- `test/Vista/DiaE2EFixture.java`: define valores de prueba y nombres únicos; no accede al dominio ni a BD.
- `test/Vista/DiaE2ERobot.java`: simula operaciones humanas de teclado y mouse, espera cambios visuales y lee controles para aserciones.
- `test/Vista/DiaCompletoFiscalE2EVirtual.java`: inicia login real y conduce la jornada por configuración, sala, platos, cuentas, pagos, cancelación y Cierre Z.
- `AGENTS.md`: documenta el comando E2E y establece ejecución virtual/UI-only.

## Cobertura funcional

1. Crear mediante formularios una sala de ocho mesas y seis platos: USD/VES × exento/IVA agregado/IVA incluido.
2. Validar desglose de ficha de plato y persistencia que la UI presenta al volver a seleccionar el plato.
3. Generar pedido combinado de seis líneas; agregar una segunda ronda con cantidades incrementales; verificar importes, condición fiscal y líneas visibles.
4. Crear una cuenta individual por perfil y cobrar en VES, USD o dos monedas.
5. Probar pago corto y excedido antes de aceptar un pago exacto.
6. Cambiar tasa e IVA desde Configuración; verificar cuentas abiertas recalculadas, facturas pagadas congeladas y precios nuevos con IVA actualizado.
7. Anular una cuenta pendiente como fuga, comprobar la mesa libre, conciliar el historial de hoy y solicitar el cierre Z.

Los importes esperados y las reglas de aislamiento están descritos en [la especificación](../specs/2026-10-18-dia-completo-fiscal-e2e-design.md).

## Verificación

- `bash -n e2e_virtual.sh e2e_dia_completo_virtual.sh`
- `git diff --check`
- `ant -q compile-test` (solo compilación; no se ejecuta `ant test`, `ant integration-test` ni el E2E)
- Inspección estática de los tres fuentes nuevos para confirmar que no invoquen negocio/persistencia directamente.

## Estado

- [x] Implementación UI-only del escenario y su driver Robot.
- [x] Lanzador aislado de BD, Xvfb y salida temporal de impresión.
- [x] Verificaciones estáticas finales y compilación tras los últimos cambios (`bash -n`, `git diff --check`, `ant -q compile-test`).
- [ ] Ejecución E2E virtual: queda para una solicitud explícita posterior.
