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
 ('Entradas'),('Platos Fuertes'),('Parrilla'),('Bebidas'),('Jugos Naturales'),('Postres'),
 ('Pizzas'),('Pastas'),('Hamburguesas'),('Pescados y Mariscos'),('Sopas y Cremas'),('Ensaladas'),('Desayunos'),('Panaderia y Reposteria'),('Cafeteria'),('Cocteles y Mocktails'),('Vinos y Licores'),('Comida Vegana'),('Menu Infantil'),('Sushi y Rolls');
INSERT INTO platos (nombre,precio,fecha,activo,aplica_iva,moneda_base,precio_usd,precio_ves,precio_clasificado,precio_incluye_iva) VALUES
 ('POLLO ASADO',45.00,CURDATE(),1,1,'USD',45.00,1642.50,1,0),('LOMO SALTADO',32.50,CURDATE(),1,1,'USD',32.50,1186.25,1,0),('PARRILLA MIXTA',55.00,CURDATE(),1,1,'USD',55.00,2007.50,1,0),
 ('ARROZ CON POLLO',10.00,CURDATE(),1,1,'USD',10.00,365.00,1,0),('CHAUFA',20.00,CURDATE(),1,1,'USD',20.00,730.00,1,0),('PABELLON CRIOLLO',16.00,CURDATE(),1,1,'USD',16.00,584.00,1,0),
 ('AREPA REINA PEPIADA',12.00,CURDATE(),1,1,'USD',12.00,438.00,1,0),('CACHAPA CON QUESO',15.00,CURDATE(),1,1,'USD',15.00,547.50,1,0),('EMPANADAS (3 UND)',9.00,CURDATE(),1,1,'USD',9.00,328.50,1,0),
 ('ENSALADA CESAR',14.00,CURDATE(),1,0,'USD',14.00,511.00,1,0),('SOPA DEL DIA',8.00,CURDATE(),1,0,'USD',8.00,292.00,1,0),
 ('GASEOSA COCA COLA 1.5 LITROS',8.00,CURDATE(),1,1,'USD',8.00,292.00,1,0),('CERVEZA',8.00,CURDATE(),1,1,'USD',8.00,292.00,1,0),
 ('CAFE NEGRO',3.00,CURDATE(),1,0,'USD',3.00,109.50,1,0),('CAFE CON LECHE',5.00,CURDATE(),1,0,'USD',5.00,182.50,1,0),
 ('JUGO DE NARANJA',6.00,CURDATE(),1,0,'USD',6.00,219.00,1,0),('JUGO DE MARACUYA',8.00,CURDATE(),1,0,'USD',8.00,292.00,1,0),('PAPELON CON LIMON',7.00,CURDATE(),1,0,'USD',7.00,255.50,1,0),
 ('TORTA DE CHOCOLATE',10.00,CURDATE(),1,1,'USD',10.00,365.00,1,0),('QUESILLO',8.00,CURDATE(),1,0,'USD',8.00,292.00,1,0),
 ('PIZZA MARGARITA',3.50,CURDATE(),1,1,'USD',3.50,127.75,1,0),
 ('PIZZA PEPPERONI',4.99,CURDATE(),1,1,'USD',4.99,182.14,1,1),
 ('PIZZA CUATRO QUESOS',246.38,CURDATE(),1,1,'VES',6.75,246.38,1,0),
 ('PIZZA VEGETARIANA',5.25,CURDATE(),1,1,'USD',5.25,191.62,0,NULL),
 ('PIZZA PROSCIUTTO E FUNGHI',273.75,CURDATE(),1,1,'VES',7.50,273.75,1,1),
 ('ESPAGUETI A LA BOLONESA',6.00,CURDATE(),1,0,'USD',6.00,219.00,1,0),
 ('FETTUCCINE ALFREDO',237.25,CURDATE(),1,0,'VES',6.50,237.25,1,0),
 ('LASAGNA DE CARNE',8.25,CURDATE(),1,1,'USD',8.25,301.12,1,0),
 ('PENNE ARRABBIATA',5.75,CURDATE(),1,1,'USD',5.75,209.88,1,1),
 ('RAVIOLES DE RICOTTA',255.50,CURDATE(),1,1,'VES',7.00,255.50,1,0),
 ('HAMBURGUESA CLASICA',200.75,CURDATE(),1,1,'VES',5.50,200.75,1,1),
 ('HAMBURGUESA DOBLE QUESO',7.25,CURDATE(),1,0,'USD',7.25,264.62,1,0),
 ('HAMBURGUESA BBQ BACON',292.00,CURDATE(),1,0,'VES',8.00,292.00,1,0),
 ('HAMBURGUESA DE POLLO CRISPY',6.25,CURDATE(),1,1,'USD',6.25,228.12,1,0),
 ('HAMBURGUESA VEGANA DE LENTEJAS',6.75,CURDATE(),1,1,'USD',6.75,246.38,1,1),
 ('FILETE DE SALMON A LA PLANCHA',456.25,CURDATE(),1,1,'VES',12.50,456.25,0,NULL),
 ('CAMARONES AL AJILLO',401.50,CURDATE(),1,1,'VES',11.00,401.50,1,0),
 ('PARGO FRITO ENTERO',511.00,CURDATE(),1,1,'VES',14.00,511.00,1,1),
 ('CEVICHE DE CAMARON',9.50,CURDATE(),1,0,'USD',9.50,346.75,1,0),
 ('PULPO A LA GALLEGA',501.88,CURDATE(),1,0,'VES',13.75,501.88,1,0),
 ('CREMA DE AUYAMA',4.25,CURDATE(),1,1,'USD',4.25,155.12,1,0),
 ('SOPA DE POLLO CON FIDEOS',4.00,CURDATE(),1,1,'USD',4.00,146.00,1,1),
 ('CREMA DE CHAMPINONES',173.38,CURDATE(),1,1,'VES',4.75,173.38,1,0),
 ('SOPA DE LENTEJAS',136.88,CURDATE(),1,1,'VES',3.75,136.88,1,1),
 ('CALDO DE RES',5.00,CURDATE(),1,0,'USD',5.00,182.50,1,0),
 ('ENSALADA CESAR CON POLLO',237.25,CURDATE(),1,0,'VES',6.50,237.25,1,0),
 ('ENSALADA CAPRESE',5.75,CURDATE(),1,0,'USD',5.75,209.88,0,NULL),
 ('ENSALADA DE QUINOA',6.25,CURDATE(),1,1,'USD',6.25,228.12,1,0),
 ('ENSALADA GRIEGA',6.00,CURDATE(),1,1,'USD',6.00,219.00,1,1),
 ('ENSALADA DE ATUN',200.75,CURDATE(),1,1,'VES',5.50,200.75,1,0),
 ('DESAYUNO CRIOLLO',191.62,CURDATE(),1,1,'VES',5.25,191.62,1,1),
 ('HUEVOS RANCHEROS',4.75,CURDATE(),1,0,'USD',4.75,173.38,1,0),
 ('PANCAKES CON MIEL',164.25,CURDATE(),1,0,'VES',4.50,164.25,1,0),
 ('AREPA CON PERICO',3.50,CURDATE(),1,1,'USD',3.50,127.75,1,0),
 ('TOSTADAS FRANCESAS',4.25,CURDATE(),1,1,'USD',4.25,155.12,1,1),
 ('CROISSANT DE MANTEQUILLA',100.38,CURDATE(),1,1,'VES',2.75,100.38,1,0),
 ('TORTA TRES LECHES',164.25,CURDATE(),1,1,'VES',4.50,164.25,1,1),
 ('CHEESECAKE DE FRUTOS ROJOS',4.75,CURDATE(),1,0,'USD',4.75,173.38,1,0),
 ('BROWNIE CON HELADO',146.00,CURDATE(),1,0,'VES',4.00,146.00,1,0),
 ('MILHOJAS DE CREMA',3.75,CURDATE(),1,1,'USD',3.75,136.88,1,0),
 ('CAPUCHINO',91.25,CURDATE(),1,0,'VES',2.50,91.25,0,NULL),
 ('LATTE VAINILLA',3.00,CURDATE(),1,1,'USD',3.00,109.50,1,1),
 ('ESPRESSO DOBLE',82.12,CURDATE(),1,1,'VES',2.25,82.12,1,0),
 ('MOCHA FRAPPE',136.88,CURDATE(),1,1,'VES',3.75,136.88,1,1),
 ('TE CHAI LATTE',3.25,CURDATE(),1,0,'USD',3.25,118.62,1,0),
 ('MOJITO SIN ALCOHOL',127.75,CURDATE(),1,0,'VES',3.50,127.75,1,0),
 ('PINA COLADA',4.25,CURDATE(),1,1,'USD',4.25,155.12,1,0),
 ('DAIQUIRI DE FRESA',4.00,CURDATE(),1,1,'USD',4.00,146.00,1,1),
 ('LIMONADA FROZEN',109.50,CURDATE(),1,1,'VES',3.00,109.50,1,0),
 ('SANGRIA SIN ALCOHOL',136.88,CURDATE(),1,1,'VES',3.75,136.88,1,1),
 ('COPA DE VINO TINTO',5.00,CURDATE(),1,0,'USD',5.00,182.50,1,0),
 ('COPA DE VINO BLANCO',182.50,CURDATE(),1,0,'VES',5.00,182.50,1,0),
 ('CERVEZA ARTESANAL IPA',4.50,CURDATE(),1,1,'USD',4.50,164.25,1,0),
 ('WHISKY ETIQUETA NEGRA (SHOT)',6.50,CURDATE(),1,1,'USD',6.50,237.25,1,1),
 ('RON ANIEJO (SHOT)',200.75,CURDATE(),1,1,'VES',5.50,200.75,1,0),
 ('BOWL VEGANO DE QUINOA',7.50,CURDATE(),1,1,'USD',7.50,273.75,0,NULL),
 ('CURRY DE GARBANZOS',246.38,CURDATE(),1,1,'VES',6.75,246.38,1,1),
 ('TACOS DE JACKFRUIT',6.25,CURDATE(),1,0,'USD',6.25,228.12,1,0),
 ('LASAGNA VEGANA',292.00,CURDATE(),1,0,'VES',8.00,292.00,1,0),
 ('SALTEADO DE TOFU Y VERDURAS',7.25,CURDATE(),1,1,'USD',7.25,264.62,1,0),
 ('MINI HAMBURGUESA CON PAPAS',4.50,CURDATE(),1,1,'USD',4.50,164.25,1,1),
 ('NUGGETS DE POLLO',146.00,CURDATE(),1,1,'VES',4.00,146.00,1,0),
 ('DEDOS DE QUESO',136.88,CURDATE(),1,1,'VES',3.75,136.88,1,1),
 ('HELADO DE CHOCOLATE (INFANTIL)',2.50,CURDATE(),1,0,'USD',2.50,91.25,1,0),
 ('ROLL CALIFORNIA',237.25,CURDATE(),1,0,'VES',6.50,237.25,1,0),
 ('ROLL FILADELFIA',7.00,CURDATE(),1,1,'USD',7.00,255.50,1,0),
 ('NIGIRI DE SALMON',5.50,CURDATE(),1,1,'USD',5.50,200.75,1,1),
 ('SASHIMI DE ATUN',310.25,CURDATE(),1,1,'VES',8.50,310.25,1,0),
 ('ROLL VEGETARIANO',209.88,CURDATE(),1,0,'VES',5.75,209.88,0,NULL),
 ('GYOZA DE CERDO',191.62,CURDATE(),1,1,'VES',5.25,191.62,1,1);
SET @e:=(SELECT id FROM categorias WHERE nombre='Entradas'),@f:=(SELECT id FROM categorias WHERE nombre='Platos Fuertes'),@p:=(SELECT id FROM categorias WHERE nombre='Parrilla'),@b:=(SELECT id FROM categorias WHERE nombre='Bebidas'),@j:=(SELECT id FROM categorias WHERE nombre='Jugos Naturales'),@d:=(SELECT id FROM categorias WHERE nombre='Postres'),
 @pz:=(SELECT id FROM categorias WHERE nombre='Pizzas'),
 @pa:=(SELECT id FROM categorias WHERE nombre='Pastas'),
 @hb:=(SELECT id FROM categorias WHERE nombre='Hamburguesas'),
 @pm:=(SELECT id FROM categorias WHERE nombre='Pescados y Mariscos'),
 @sc:=(SELECT id FROM categorias WHERE nombre='Sopas y Cremas'),
 @en:=(SELECT id FROM categorias WHERE nombre='Ensaladas'),
 @ds:=(SELECT id FROM categorias WHERE nombre='Desayunos'),
 @pr:=(SELECT id FROM categorias WHERE nombre='Panaderia y Reposteria'),
 @cf:=(SELECT id FROM categorias WHERE nombre='Cafeteria'),
 @ck:=(SELECT id FROM categorias WHERE nombre='Cocteles y Mocktails'),
 @vl:=(SELECT id FROM categorias WHERE nombre='Vinos y Licores'),
 @vg:=(SELECT id FROM categorias WHERE nombre='Comida Vegana'),
 @ni:=(SELECT id FROM categorias WHERE nombre='Menu Infantil'),
 @su:=(SELECT id FROM categorias WHERE nombre='Sushi y Rolls');
INSERT INTO plato_categoria (nombre_clave,id_categoria) VALUES
 ('pollo asado',@p),('lomo saltado',@p),('parrilla mixta',@p),
 ('arroz con pollo',@f),('chaufa',@f),('pabellon criollo',@f),
 ('arepa reina pepiada',@e),('cachapa con queso',@e),('empanadas (3 und)',@e),('ensalada cesar',@e),('sopa del dia',@e),
 ('gaseosa coca cola 1.5 litros',@b),('cerveza',@b),('cafe negro',@b),('cafe con leche',@b),
 ('jugo de naranja',@j),('jugo de maracuya',@j),('papelon con limon',@j),
 ('torta de chocolate',@d),('quesillo',@d),
 ('pizza margarita',@pz),
('pizza pepperoni',@pz),
('pizza cuatro quesos',@pz),
('pizza vegetariana',@pz),
('pizza prosciutto e funghi',@pz),
('espagueti a la bolonesa',@pa),
('fettuccine alfredo',@pa),
('lasagna de carne',@pa),
('penne arrabbiata',@pa),
('ravioles de ricotta',@pa),
('hamburguesa clasica',@hb),
('hamburguesa doble queso',@hb),
('hamburguesa bbq bacon',@hb),
('hamburguesa de pollo crispy',@hb),
('hamburguesa vegana de lentejas',@hb),
('filete de salmon a la plancha',@pm),
('camarones al ajillo',@pm),
('pargo frito entero',@pm),
('ceviche de camaron',@pm),
('pulpo a la gallega',@pm),
('crema de auyama',@sc),
('sopa de pollo con fideos',@sc),
('crema de champinones',@sc),
('sopa de lentejas',@sc),
('caldo de res',@sc),
('ensalada cesar con pollo',@en),
('ensalada caprese',@en),
('ensalada de quinoa',@en),
('ensalada griega',@en),
('ensalada de atun',@en),
('desayuno criollo',@ds),
('huevos rancheros',@ds),
('pancakes con miel',@ds),
('arepa con perico',@ds),
('tostadas francesas',@ds),
('croissant de mantequilla',@pr),
('torta tres leches',@pr),
('cheesecake de frutos rojos',@pr),
('brownie con helado',@pr),
('milhojas de crema',@pr),
('capuchino',@cf),
('latte vainilla',@cf),
('espresso doble',@cf),
('mocha frappe',@cf),
('te chai latte',@cf),
('mojito sin alcohol',@ck),
('pina colada',@ck),
('daiquiri de fresa',@ck),
('limonada frozen',@ck),
('sangria sin alcohol',@ck),
('copa de vino tinto',@vl),
('copa de vino blanco',@vl),
('cerveza artesanal ipa',@vl),
('whisky etiqueta negra (shot)',@vl),
('ron aniejo (shot)',@vl),
('bowl vegano de quinoa',@vg),
('curry de garbanzos',@vg),
('tacos de jackfruit',@vg),
('lasagna vegana',@vg),
('salteado de tofu y verduras',@vg),
('mini hamburguesa con papas',@ni),
('nuggets de pollo',@ni),
('dedos de queso',@ni),
('helado de chocolate (infantil)',@ni),
('roll california',@su),
('roll filadelfia',@su),
('nigiri de salmon',@su),
('sashimi de atun',@su),
('roll vegetariano',@su),
('gyoza de cerdo',@su);
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
