#!/usr/bin/env bash
#
# inicio_fresco.sh — Deja la BD lista para un próximo inicio cuando haga falta.
#
# Hace, en orden: respaldo de seguridad -> vaciado de datos -> estructura
# maestra (4 salas, 6 categorías, 20 platos, 6 mesoneros, clientes, config,
# admin) -> semana demo relativa (últimos 7 días cerrados + cierres) ->
# verificación de coherencia.
#
# Uso:
#   ./inicio_fresco.sh [--sin-semana] [--forzar] [MYSQL_DB=restaurante ...]
#   MYSQL_DB=restaurante_prueba ./inicio_fresco.sh --forzar   # prueba segura
#
# Variables: CONTENEDOR, MYSQL_USER, MYSQL_PASS, MYSQL_DB.
# Requiere: docker, mysql corriendo en el contenedor, python3 (solo con semana).
# NO reinicia la app: el jar nuevo se activa al próximo arranque.
#
set -euo pipefail

CONTENEDOR="${CONTENEDOR:-restaurante-db-1}"
MYSQL_USER="${MYSQL_USER:-admin}"
MYSQL_PASS="${MYSQL_PASS:-admin}"
MYSQL_DB="${MYSQL_DB:-restaurante}"
CON_SEMANA=1
FORZAR=0

for arg in "$@"; do
    case "$arg" in
        --sin-semana) CON_SEMANA=0 ;;
        --forzar) FORZAR=1 ;;
        -h|--help)
            echo "Uso: $0 [--sin-semana] [--forzar]"
            echo "Vars: CONTENEDOR MYSQL_USER MYSQL_PASS MYSQL_DB"
            exit 0 ;;
        *) echo "Opción desconocida: $arg" >&2; exit 2 ;;
    esac
done

q() { docker exec -i "$CONTENEDOR" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" 2>/dev/null; }
qs() { docker exec "$CONTENEDOR" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -e "$1" 2>/dev/null; }

echo "== [1/6] Prechequeos =="
docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR" || { echo "Contenedor $CONTENEDOR no corre" >&2; exit 1; }
qs "SELECT 1" >/dev/null || { echo "MySQL no responde" >&2; exit 1; }
command -v python3 >/dev/null || { echo "Falta python3" >&2; exit 1; }
echo "OK: $CONTENEDOR/$MYSQL_DB responde."

echo "== [2/6] Respaldo de seguridad =="
mkdir -p /tmp/opencode/respaldos-inicio
RESP="/tmp/opencode/respaldos-inicio/pre_inicio_$(date +%Y%m%d_%H%M%S).sql.gz"
docker exec "$CONTENEDOR" mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASS" --single-transaction --routines "$MYSQL_DB" 2>/dev/null | gzip > "$RESP"
echo "Respaldo: $RESP ($(du -h "$RESP" | cut -f1))"

if [ "$FORZAR" -ne 1 ]; then
    echo ""
    echo "Se VACIARAN los datos de '$MYSQL_DB' y se sembrará inicio fresco."
    read -r -p "Escribe SI para continuar: " r
    [ "$r" = "SI" ] || { echo "Cancelado."; exit 1; }
fi

echo "== [3/6] Vaciado (se preserva schema_migrations) =="
q <<'EOSQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE detalle_pedidos;
TRUNCATE TABLE auditoria_pedidos;
TRUNCATE TABLE pedido_pagos;
TRUNCATE TABLE pedidos;
TRUNCATE TABLE cierres_caja;
TRUNCATE TABLE clientes;
TRUNCATE TABLE mesoneros;
TRUNCATE TABLE plato_categoria;
TRUNCATE TABLE plato_favorito;
TRUNCATE TABLE categorias;
TRUNCATE TABLE platos;
TRUNCATE TABLE preferencias_tabla;
TRUNCATE TABLE salas;
TRUNCATE TABLE test_respaldo_tmp;
TRUNCATE TABLE usuarios;
TRUNCATE TABLE config;
TRUNCATE TABLE configuracion_sistema;
SET FOREIGN_KEY_CHECKS=1;
EOSQL
echo "Vaciado OK."

echo "== [4/6] Maestra: salas, categorías, platos, mesoneros, clientes, config, admin =="
q <<'EOSQL'
INSERT INTO salas (nombre, mesas, tipo) VALUES
 ('SALON PRINCIPAL',4,'SALON'),('TERRAZA',4,'SALON'),('SALON VIP',4,'SALON'),('BARRA',4,'BARRA');
INSERT INTO categorias (nombre) VALUES
 ('Entradas'),('Platos Fuertes'),('Parrilla'),('Bebidas'),('Jugos Naturales'),('Postres');
INSERT INTO platos (nombre,precio,fecha,activo,aplica_iva) VALUES
 ('POLLO ASADO',45.00,CURDATE(),1,1),('LOMO SALTADO',32.50,CURDATE(),1,1),('PARRILLA MIXTA',55.00,CURDATE(),1,1),
 ('ARROZ CON POLLO',10.00,CURDATE(),1,1),('CHAUFA',20.00,CURDATE(),1,1),('PABELLON CRIOLLO',16.00,CURDATE(),1,1),
 ('AREPA REINA PEPIADA',12.00,CURDATE(),1,1),('CACHAPA CON QUESO',15.00,CURDATE(),1,1),('EMPANADAS (3 UND)',9.00,CURDATE(),1,1),
 ('ENSALADA CESAR',14.00,CURDATE(),1,0),('SOPA DEL DIA',8.00,CURDATE(),1,0),
 ('GASEOSA COCA COLA 1.5 LITROS',8.00,CURDATE(),1,1),('CERVEZA',8.00,CURDATE(),1,1),
 ('CAFE NEGRO',3.00,CURDATE(),1,0),('CAFE CON LECHE',5.00,CURDATE(),1,0),
 ('JUGO DE NARANJA',6.00,CURDATE(),1,0),('JUGO DE MARACUYA',8.00,CURDATE(),1,0),('PAPELON CON LIMON',7.00,CURDATE(),1,0),
 ('TORTA DE CHOCOLATE',10.00,CURDATE(),1,1),('QUESILLO',8.00,CURDATE(),1,0);
SET @e:=(SELECT id FROM categorias WHERE nombre='Entradas'),@f:=(SELECT id FROM categorias WHERE nombre='Platos Fuertes'),@p:=(SELECT id FROM categorias WHERE nombre='Parrilla'),@b:=(SELECT id FROM categorias WHERE nombre='Bebidas'),@j:=(SELECT id FROM categorias WHERE nombre='Jugos Naturales'),@d:=(SELECT id FROM categorias WHERE nombre='Postres');
INSERT INTO plato_categoria (nombre_clave,id_categoria) VALUES
 ('pollo asado',@p),('lomo saltado',@p),('parrilla mixta',@p),
 ('arroz con pollo',@f),('chaufa',@f),('pabellon criollo',@f),
 ('arepa reina pepiada',@e),('cachapa con queso',@e),('empanadas (3 und)',@e),('ensalada cesar',@e),('sopa del dia',@e),
 ('gaseosa coca cola 1.5 litros',@b),('cerveza',@b),('cafe negro',@b),('cafe con leche',@b),
 ('jugo de naranja',@j),('jugo de maracuya',@j),('papelon con limon',@j),
 ('torta de chocolate',@d),('quesillo',@d);
INSERT INTO plato_favorito (nombre_clave) VALUES
 ('pollo asado'),('parrilla mixta'),('arepa reina pepiada'),('cafe con leche'),('torta de chocolate');
INSERT INTO mesoneros (nombre_completo,cedula,telefono,activo,eliminado) VALUES
 ('Juan Pérez','V-20111222','0414-1112233',1,0),('Luis García','V-21555666','0412-7778899',1,0),
 ('Pedro Sánchez','V-18777888','0424-5556677',1,0),('Ana Martínez','V-18987654','0424-4445566',1,0),
 ('Carmen Ruiz','V-17666777','0416-0001122',1,0),('Lucía Fernández','V-19888777','0414-3334455',1,0);
INSERT INTO clientes (documento,nombre,telefono,direccion) VALUES
 ('V-12345678','María González','0414-1112233','Caracas'),('V-87654321','Carlos Pérez','0424-4445566','Caracas'),
 ('J-314159265','Empresa Demo CA','0212-5556677','Caracas'),('V-14555666','Pedro Sánchez','0412-9998877','Caracas'),
 ('V-19888777','Lucía Fernández','0416-3334455','Caracas');
INSERT INTO config (id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path, cliente_predeterminado_nombre, cliente_predeterminado_documento, meses_retencion_pedidos, imprimir_logo_ticket, impresora_tickets, modo_salida_tickets)
VALUES (1, 'J-999999999', 'Restaurante RedTeam', '0414-1111111', 'Caracas', 'Gracias', 1014.0000, 16.00, NULL, 'Cliente Frecuente', 'V-12345678', 36, 0, 'Xprinter XP-80T', 'PDF24_CREATOR');
INSERT INTO configuracion_sistema (clave, valor) VALUES
 ('tasa_dolar', '1014.00'),('iva_porcentaje', '16.00'),('impresora_tickets', 'Xprinter XP-80T'),
 ('modo_salida_tickets', 'PDF24_CREATOR'),('imprimir_logo_ticket', '0'),
 ('cliente_predeterminado_nombre', 'Cliente Frecuente'),('cliente_predeterminado_documento', 'V-12345678'),
 ('meses_retencion_pedidos', '36');
INSERT INTO usuarios (id, nombre, correo, pass, rol) VALUES
 (2, 'Administrador', 'admin', 'pbkdf2-sha256$600000$4rRaMK7S7lSdpTKgN6YRiA$O4BlX32Y2+lu3IjnBmn08WaE3waaVtdvv7xiuszXxNM', 'Administrador');
EOSQL
echo "Maestra OK."

if [ "$CON_SEMANA" -eq 1 ]; then
echo "== [5/6] Semana demo (últimos 7 días cerrados) =="
TASA=$(qs "SELECT valor FROM configuracion_sistema WHERE clave='tasa_dolar'")
IVA=$(qs "SELECT valor FROM configuracion_sistema WHERE clave='iva_porcentaje'")
TASA=${TASA:-45.25}; IVA=${IVA:-16.00}
export SEM_TASA="$TASA" SEM_IVA="$IVA"
python3 - <<'PYEOF' | q
import os, random
from datetime import date, timedelta
random.seed()
TASA = float(os.environ.get("SEM_TASA", "45.25"))
IVA = float(os.environ.get("SEM_IVA", "16.0"))
L = []
menu = [("POLLO ASADO",45.00,1),("LOMO SALTADO",32.50,1),("PARRILLA MIXTA",55.00,1),
 ("ARROZ CON POLLO",10.00,1),("CHAUFA",20.00,1),("PABELLON CRIOLLO",16.00,1),
 ("AREPA REINA PEPIADA",12.00,1),("CACHAPA CON QUESO",15.00,1),("EMPANADAS (3 UND)",9.00,1),
 ("ENSALADA CESAR",14.00,0),("SOPA DEL DIA",8.00,0),
 ("GASEOSA COCA COLA 1.5 LITROS",8.00,1),("CERVEZA",8.00,1),
 ("CAFE NEGRO",3.00,0),("CAFE CON LECHE",5.00,0),
 ("JUGO DE NARANJA",6.00,0),("JUGO DE MARACUYA",8.00,0),("PAPELON CON LIMON",7.00,0),
 ("TORTA DE CHOCOLATE",10.00,1),("QUESILLO",8.00,1)]
mesoneros = [(1,"Juan Pérez"),(2,"Luis García"),(3,"Pedro Sánchez"),(4,"Ana Martínez"),(5,"Carmen Ruiz"),(6,"Lucía Fernández")]
clis = [('Consumidor Final','V-00000000')]*7 + [('María González','V-12345678'),('Carlos Pérez','V-87654321'),('Pedro Sánchez','V-14555666'),('Lucía Fernández','V-19888777')]
mets = ['EFECTIVO']*10 + ['EFECTIVO_BS']*4 + ['TRANSFERENCIA']*4 + ['TARJETA']*3 + ['PAGO_MOVIL']*2 + ['MIXTO']
hoy = date.today()
dusd={}; dbs={}; defu={}; defb={}
for off in range(7, 0, -1):
    dia = (hoy - timedelta(days=off)).isoformat()
    for k in range(random.randint(15, 22)):
        sid = random.choices([1,2,3,4],[40,25,20,15])[0]
        mesa = random.randint(1,4)
        h = random.choices(list(range(11,23)), weights=[3,5,8,8,4,3,3,4,6,8,8,3])[0]
        hora = f"{h:02d}:{random.choice(['05','20','35','50'])}:00"
        det = random.sample(menu, random.randint(1,4))
        cn,cd = random.choice(clis); met = random.choice(mets)
        mid = random.choice([1,2,3,4,5,6]) if random.random() < 0.9 else None
        base = 0.0; ex = 0.0; dl = []
        for pnn,pp,iv in det:
            qq = random.randint(1,3)
            if iv: base += pp*qq
            else: ex += pp*qq
            dl.append((pnn,pp,qq))
        base = round(base,2); ex = round(ex,2)
        iva = round(base*IVA/100.0,2); tot = round(base+ex+iva,2); bs = round(tot*TASA,2)
        mn = f'"{dict(mesoneros)[mid]}"' if mid else 'NULL'
        L.append(f"INSERT INTO pedidos (id_sala,num_mesa,fecha,total,estado,usuario,subtotal,iva_porcentaje,iva_monto,tasa_cambio,total_bs,subtotal_bs,iva_bs,cliente_nombre,cliente_documento,metodo_pago,id_mesonero,mesonero_nombre) VALUES ({sid},{mesa},'{dia} {hora}',{tot:.2f},'FINALIZADO','admin',{round(base+ex,2):.2f},{IVA:.2f},{iva:.2f},{TASA:.4f},{bs:.2f},{round((base+ex)*TASA,2):.2f},{round(iva*TASA,2):.2f},'{cn}','{cd}','{met}',{mid if mid else 'NULL'},{mn});")
        L.append("SET @pid = LAST_INSERT_ID();")
        for pnn,pp,qq in dl:
            safe = pnn.replace("'","''")
            L.append(f"INSERT INTO detalle_pedidos (nombre,precio,cantidad,comentario,id_pedido) VALUES ('{safe}',{pp:.2f},{qq},'',@pid);")
        dusd[dia]=dusd.get(dia,0)+tot; dbs[dia]=dbs.get(dia,0)+bs
        if met=='EFECTIVO': defu[dia]=defu.get(dia,0)+tot
        if met=='EFECTIVO_BS': defb[dia]=defb.get(dia,0)+bs
        if met=='MIXTO': defu[dia]=defu.get(dia,0)+round(tot/2,2); defb[dia]=defb.get(dia,0)+round(bs/2,2)
for off in range(7, 0, -1):
    dia = (hoy - timedelta(days=off)).isoformat()
    L.append(f"INSERT INTO cierres_caja (tipo,fecha_jornada,fecha_hora_emision,usuario_emisor,total_ventas_usd,total_ventas_bs,efectivo_esperado_bs,efectivo_esperado_usd,tasa_cambio,ruta_pdf) VALUES ('TOTAL','{dia}','{dia} 22:45:00','Administrador',{dusd.get(dia,0):.2f},{dbs.get(dia,0):.2f},{defb.get(dia,0):.2f},{defu.get(dia,0):.2f},{TASA:.4f},NULL);")
print('\n'.join(L))
PYEOF
echo "Semana OK."
# MySQL ROUND == Python round en bordes .5: normalizar Bs como hace la app
q <<'EOSQL'
UPDATE pedidos SET subtotal_bs = ROUND(subtotal*tasa_cambio,2), iva_bs = ROUND(iva_monto*tasa_cambio,2), total_bs = ROUND(total*tasa_cambio,2)
 WHERE DATE(fecha) >= CURDATE() - INTERVAL 7 DAY;
EOSQL
else
echo "== [5/6] Semana omitida (--sin-semana) =="
fi

echo "== [6/6] Verificación =="
qs "SELECT CONCAT('salas=',(SELECT COUNT(*) FROM salas),' platos=',(SELECT COUNT(*) FROM platos WHERE activo=1),' mesoneros=',(SELECT COUNT(*) FROM mesoneros),' pedidos=',(SELECT COUNT(*) FROM pedidos),' cierres=',(SELECT COUNT(*) FROM cierres_caja),' usuarios=',(SELECT COUNT(*) FROM usuarios));"
qs "SELECT CONCAT('sin_cat=',(SELECT COUNT(*) FROM platos p LEFT JOIN plato_categoria pc ON pc.nombre_clave=p.nombre_clave WHERE p.activo=1 AND pc.nombre_clave IS NULL),' fuera_rango=',(SELECT COUNT(*) FROM pedidos p JOIN salas s ON s.id=p.id_sala WHERE p.num_mesa<1 OR p.num_mesa>s.mesas),' tot_mal=',(SELECT COUNT(*) FROM pedidos WHERE ROUND(total,2)<>ROUND(subtotal+iva_monto,2)),' pend=',(SELECT COUNT(*) FROM pedidos WHERE estado='PENDIENTE'));"
echo "Listo. Reinicia la app para el próximo inicio (el jar se activa al arrancar)."
