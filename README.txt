================================================================================
          SISTEMA DE GESTION DE RESTAURANTE 2026 - GUIA DE INSTALACION
                             WINDOWS 10 / WINDOWS 11
================================================================================

Este documento contiene las instrucciones detalladas paso a paso para instalar,
configurar y ejecutar el Sistema de Restaurante en sistemas operativos Windows 10
y Windows 11 (64 bits).

--------------------------------------------------------------------------------
1. ESPECIFICACIONES Y VERSIONES DE COMPONENTES
--------------------------------------------------------------------------------
- Sistema Operativo: Windows 10 / Windows 11 (64-bit)
- Lenguaje / Runtime: Java OpenJDK 21 LTS (o Java 17 LTS)
  * Recomendado: Eclipse Adoptium Temurin 21 (x64)
- Motor de Base de Datos: MySQL 8.4 LTS (o MySQL 8.0+)
  * Opcion A: Docker Desktop con contenedor MySQL 8.4 aislado
  * Opcion B: Servidor MySQL local nativo (MySQL Server / XAMPP / WampServer)
- Interfaz de Usuario: Java Swing Desktop con Look & Feel moderno
- Motor de Facturacion: Generacion de Tickets PDF bimonetarios con iText
- Modelo Monetario: Bimoneda Oficial (Bolivares Bs. / Dolares $ USD) con
  tasa de cambio editable dinamicamente desde el menu de Configuracion.

--------------------------------------------------------------------------------
2. PASO 1: INSTALACION DE JAVA 21 EN WINDOWS 10 / 11
--------------------------------------------------------------------------------
Si tu sistema no tiene Java 21 instalado, sigue uno de estos metodos:

METODO A (Recomendado - Instalador Grafico MSI):
1. Ingresa a la pagina oficial de Eclipse Adoptium:
   https://adoptium.net/temurin/releases/?version=21
2. En la seccion "Operating System", selecciona "Windows" y arquitectura "x64".
3. Descarga el archivo instalador con extension .msi
   (ejemplo: OpenJDK21U-jdk_x64_windows_hotspot_21.0.x.msi).
4. Ejecuta el archivo .msi descargado.
5. IMPORTANTE durante la instalacion:
   - En la pantalla de caracteristicas personalizadas (Custom Setup):
     * Haz clic en "Set JAVA_HOME variable" y selecciona "Will be installed on local hard drive".
     * Haz clic en "Add to PATH" y selecciona "Will be installed on local hard drive".
6. Haz clic en "Siguiente" e "Instalar".

METODO B (Instalacion rapida via Terminal / Winget):
1. Abre PowerShell o Simbolo del Sistema (CMD) como Administrador.
2. Ejecuta:
   winget install EclipseAdoptium.Temurin.21.jdk
3. Cierra y vuelve a abrir la terminal.

VERIFICACION DE JAVA:
Abre una nueva ventana de Simbolo del Sistema (CMD) y ejecuta:
   java -version
   javac -version

Debe mostrar "openjdk version 21.x.x" (o 17.x.x).

--------------------------------------------------------------------------------
3. PASO 2: CONFIGURACION DE BASE DE DATOS (SELECCIONA OPCION A u OPCION B)
--------------------------------------------------------------------------------

================================================================================
OPCION A: USO CON DOCKER DESKTOP (RECOMENDADO Y AUTOMATIZADO)
================================================================================
Si deseas levantar la base de datos sin instalar MySQL localmente:

1. Descarga e instala Docker Desktop para Windows:
   https://www.docker.com/products/docker-desktop/
2. Reinicia Windows si el instalador lo solicita para habilitar WSL 2.
3. Inicia la aplicacion Docker Desktop en Windows y espera a que el icono
   en la barra de tareas indique que el motor esta corriendo (icono verde).
4. Abre la carpeta del proyecto en el Explorador de Archivos o CMD.
5. Copia el archivo .env.example y renombralo como .env:
   En CMD:
   copy .env.example .env
6. Abre el archivo .env con el Bloc de Notas y define tus credenciales:
   ------------------------------------------------------------
   MYSQL_DATABASE=restaurante
   MYSQL_USER=restaurante_app
   MYSQL_PASSWORD=mi_clave_secreta_app
   MYSQL_ROOT_PASSWORD=mi_clave_secreta_root
   MYSQL_PORT=3306
   ------------------------------------------------------------
7. Levanta el contenedor de base de datos ejecutando en CMD:
   docker compose up -d
8. Docker descargara la imagen oficial de MySQL 8.4 y cargara automaticamente
   el esquema completo, datos iniciales y migraciones desde el archivo BD.sql.
9. Puedes verificar que este corriendo con:
   docker ps

================================================================================
OPCION B: USO CON SERVIDOR MYSQL LOCAL (SIN DOCKER / XAMPP / MYSQL WORKBENCH)
================================================================================
Si prefieres usar un servidor MySQL ya instalado en tu maquina Windows:

1. Asegurate de que el servicio MySQL este iniciado (por ejemplo en el
   Panel de Control de XAMPP o en los Servicios de Windows).
2. Abre tu cliente MySQL favorito (MySQL Workbench, HeidiSQL, phpMyAdmin o CMD).
3. Crea la base de datos si no existe e importa el archivo BD.sql incluido
   en la raiz de este proyecto:

   - Opcion por Linea de Comandos (CMD):
     mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS restaurante CHARACTER SET utf8 COLLATE utf8_spanish_ci;"
     mysql -u root -p restaurante < BD.sql

   - Opcion grafica (phpMyAdmin o MySQL Workbench):
     1. Crea una base de datos llamada `restaurante`.
     2. Ve a la pestaña "Importar" (Import).
     3. Selecciona el archivo `BD.sql` ubicado en la raiz de este proyecto.
     4. Haz clic en "Continuar" / "Ejecutar".

4. Configura el archivo .env en la raiz del proyecto para que coincida con tu MySQL local:
   ------------------------------------------------------------
   MYSQL_DATABASE=restaurante
   MYSQL_USER=root
   MYSQL_PASSWORD=tu_password_local_aqui
   MYSQL_PORT=3306
   ------------------------------------------------------------
   (Nota: Si tu usuario root no tiene contraseña en XAMPP, deja MYSQL_PASSWORD= en blanco).

--------------------------------------------------------------------------------
4. PASO 3: EJECUCION DEL SISTEMA DE RESTAURANTE
--------------------------------------------------------------------------------

METODO 1: EJECUCION CON 1 CLIC (SCRIPT BATCH)
- Haz doble clic en el archivo `iniciar_restaurante.bat` incluido en la raiz.
  El script compilara el proyecto si es necesario y abrira la aplicacion.

METODO 2: CONSOLA USANDO APACHE ANT
Abre CMD en la carpeta del proyecto y ejecuta:
   ant run

METODO 3: GENERAR ARCHIVO JAR DISTRIBUIBLE
1. Compila y empaqueta:
   ant jar
2. Ejecuta directamente el JAR:
   java -jar dist/Restaurante.jar

METODO 4: ABRIR DESDE NETBEANS / INTELLIJ / ECLIPSE
1. Abre NetBeans IDE (17 o superior).
2. Archivo -> Abrir Proyecto -> Seleccionar la carpeta del Restaurante.
3. Presiona el boton "Ejecutar Proyecto" (F6).

--------------------------------------------------------------------------------
5. CREDENCIALES DE ACCESO POR DEFECTO
--------------------------------------------------------------------------------
El sistema incluye una cuenta semilla con permisos de Administrador:

- Correo / Usuario: info@angelsifuentes.com
- Contrasena: admin
- Rol: Administrador

* Nota de Seguridad: Al iniciar sesion por primera vez, el sistema migra
  automaticamente la contrasena legacy al algoritmo criptografico seguro
  PBKDF2 con HMAC-SHA256 y salt aleatorio de 16 bytes.

Desde el panel "Usuarios", el Administrador puede registrar nuevos usuarios con:
- Rol Administrador: Acceso total a gestion de platos, salas, ventas, configuracion y usuarios.
- Rol Asistente (Mesero/Cajero): Acceso restringido para consultar platos, mesas y registrar pedidos.

--------------------------------------------------------------------------------
6. GUIA OPERATIVA DEL MODULO BIMONETARIO (BOLIVARES / DOLARES)
--------------------------------------------------------------------------------
1. CONFIGURAR LA TASA DE CAMBIO:
   - Inicia sesion como Administrador y haz clic en la opcion "Config".
   - En el campo "Tasa de Cambio ($ a Bs.)", escribe el valor del dia (ej. 36.5000 o 42.0000).
   - Haz clic en "Actualizar". A partir de ese momento, todas las ordenes nuevas
     calcularan automaticamente los precios en base a esta tasa.

2. CATALOGO DE PLATOS:
   - En la seccion "Platos", registra los platillos con su precio base en Dolares ($ USD).

3. REGISTRO DE PEDIDOS:
   - Al seleccionar una mesa y agregar platos al pedido, la pantalla mostrara el total
     en Bolivares (Bs.) y su equivalente en Dolares en tiempo real:
     Ejemplo: `Total a Pagar: Bs. 657.00 ($ 18.00)`.
   - Al presionar "GENERAR PEDIDO", el pedido queda guardado de forma atomica en la BD.

4. COBRO Y GENERACION DE TICKET PDF:
   - Al seleccionar una mesa ocupada y presionar "Finalizar Pedido", se genera
     un recibo PDF en la carpeta `pdf/` o visor de PDF del sistema con los datos
     fiscales de la empresa, desglose de platos y montos en Bolivares y Dolares.

--------------------------------------------------------------------------------
7. RESOLUCION DE DUDAS Y PROBLEMAS FRECUENTES (TROUBLESHOOTING)
--------------------------------------------------------------------------------
1. PROBLEMA: "java no se reconoce como un comando interno o externo"
   SOLUCION: Asegurate de haber instalado Java 21 y marcado la opcion "Add to PATH"
   durante la instalacion. Cierra la consola CMD y vuelve a abrirla.

2. PROBLEMA: "Puerto 3306 ocupado / Port 3306 is already in use"
   SOLUCION: Si tienes MySQL instalado localmente y tambien levantaste Docker, ambos
   compiten por el puerto 3306. Puedes detener tu MySQL local o cambiar en `.env`
   el puerto a `MYSQL_PORT=3308`.

3. PROBLEMA: "Access denied for user ... No se pudo conectar a la base de datos"
   SOLUCION: Revisa tu archivo `.env`. Verifica que el usuario y la contrasena sean
   los mismos que configuraste en tu servidor MySQL o en Docker.

4. PROBLEMA: Donde se guardan los registros de errores y auditoria?
   SOLUCION: La aplicacion genera logs diarios automáticos en la carpeta `logs/`,
   por ejemplo `logs/restaurante-2026-10-05.log`.

--------------------------------------------------------------------------------
8. CHECKLIST DE PUESTA EN MARCHA PARA PRODUCCION
--------------------------------------------------------------------------------
Siga esta lista de verificacion paso a paso para el despliegue exitoso en el
local o restaurante:

[ ] 1. BASE DE DATOS Y CONECTIVIDAD:
    - Opcion Docker: Iniciar Docker Desktop y ejecutar `docker compose up -d`.
    - Opcion Local: Iniciar MySQL y verificar que la BD `restaurante` este
      importada desde `BD.sql`.
    - Verificar que el archivo `.env` contenga las credenciales correctas.

[ ] 2. PRIMER INICIO DE SESION:
    - Ejecutar la aplicacion con doble clic en `iniciar_restaurante.bat` o
      `java -jar dist/Restaurante.jar`.
    - Iniciar sesion con las credenciales por defecto:
      Usuario: `info@angelsifuentes.com` / Clave: `admin`.

[ ] 3. CONFIGURACION FISCAL Y DE LA EMPRESA:
    - Ir a la pestaña "Config".
    - Ingresar RUC / RIF, Nombre del Restaurante, Telefono, Direccion y
      Mensaje de agradecimiento para los tickets.
    - Establecer la TASA DE CAMBIO ($ a Bs.) oficial del dia (ej. 36.5000).
    - Presionar "Actualizar" y confirmar que los datos se guarden.

[ ] 4. GESTION DE SEGURIDAD Y USUARIOS:
    - Ir a la pestaña "Usuarios".
    - Cambiar la contrasena por defecto del usuario Administrador.
    - Registrar las cuentas de trabajo para el personal (Meseros / Cajeros)
      con Rol "Asistente".

[ ] 5. PARAMETRIZACION DE INFRAESTRUCTURA (SALAS Y MESAS):
    - Ir a la pestaña "Salas".
    - Registrar las salas o ambientes (ej. "Terraza", "Salon Principal", "Bar").
    - Asignar el numero de mesas correspondiente a cada sala.

[ ] 6. CARGA DEL CATALOGO DE PRODUCTOS (PLATOS):
    - Ir a la pestaña "Platos".
    - Registrar las categorias y platos con su precio base en Dolares ($ USD).
    - Verificar que en la tabla se visualice correctamente la conversion a
      Bolivares (Bs.).

[ ] 7. PRUEBA DE CICLO COMPLETO DE ATENCION Y FACTURACION:
    - Abrir una sala y seleccionar una mesa disponible.
    - Agregar platos al pedido y verificar el total bimonetario (Bs. y $).
    - Presionar "GENERAR PEDIDO" y verificar que la mesa cambie a ocupada.
    - Presionar "Finalizar Pedido" y verificar la generacion del Ticket PDF
      en la carpeta `pdf/`.

--------------------------------------------------------------------------------
Soporte y Contacto del Desarrollador:
Proyecto Restaurante 2026 - Codigo fuente bajo control de versiones Git.
================================================================================


================================================================================
9. INSTALACION DETALLADA DE MYSQL 8.4.11 EN WINDOWS 10 / 11 (64 BITS)
================================================================================
El proyecto fija MySQL Community Server 8.4.11 LTS en su configuracion Docker.
Esta seccion explica como instalar esa version como servidor local de Windows,
crear e importar la base de datos del proyecto y configurar la conexion JDBC.

1. DESCARGAR EL INSTALADOR OFICIAL
   - Abre la pagina oficial de MySQL Community Server 8.4.11:
     https://dev.mysql.com/downloads/mysql/8.4.html
   - Selecciona Windows de 64 bits y descarga el paquete MSI de MySQL Server.
     No descargues MySQL Installer 8.0: para MySQL 8.4 se utiliza el MSI del
     servidor, que incluye MySQL Configurator.
   - La pagina puede solicitar iniciar sesion o crear una cuenta Oracle; puedes
     continuar con la opcion para iniciar la descarga sin registrarte.

2. INSTALAR MYSQL SERVER
   - Ejecuta el archivo `.msi` descargado y acepta el aviso de Windows.
   - Sigue el asistente de instalacion. MySQL 8.4 necesita Microsoft Visual
     C++ 2019 Redistributable. Si el instalador indica que falta, instalalo
     desde el Centro de descargas oficial de Microsoft y vuelve a ejecutar el
     MSI:
     https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist
   - Al terminar, abre MySQL Configurator cuando el asistente lo ofrezca.
     Tambien puedes abrirlo luego desde el menu Inicio buscando "MySQL
     Configurator".

3. CONFIGURAR EL SERVIDOR CON MYSQL CONFIGURATOR
   - Server Configuration Type: selecciona `Development`.
   - Connectivity: deja marcada la conexion `TCP/IP` y el puerto `3306`.
     El proyecto conecta por defecto a `127.0.0.1:3306`.
   - Si solo utilizaras el sistema en este equipo, desmarca la opcion para
     abrir el puerto de MySQL en Windows Firewall ("Open Windows Firewall port
     for network access").
   - Authentication: conserva el metodo predeterminado.
   - Accounts and Roles: asigna una contrasena segura a la cuenta administrativa
     `root` y guardala en un lugar seguro. No hace falta configurar la
     aplicacion para conectarse como `root`.
   - Windows Service: conserva la opcion de instalar MySQL como servicio y
     marcar el inicio automatico con Windows.
   - Pulsa `Execute` y espera a que todos los pasos terminen correctamente.
   - La carpeta predeterminada del servidor instalado por MSI es:
     `C:\Program Files\MySQL\MySQL Server 8.4`

4. COMPROBAR QUE EL SERVIDOR ESTA ACTIVO
   - Desde Inicio, abre `Servicios` y confirma que el servicio MySQL (normalmente
     `MySQL84`) indique `En ejecucion`.
   - Abre CMD y ejecuta:

     ```bat
     "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" --version
     ```

   - Para probar la conexion al servidor como administrador:

     ```bat
     "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p -e "SELECT VERSION();"
     ```

   - Escribe la contrasena de `root` cuando se solicite. Si instalaste MySQL en
     otra carpeta, ajusta la ruta de `mysql.exe` en estos comandos.

5. CREAR LA BASE DE DATOS Y EL USUARIO DE LA APLICACION
   - Abre CMD en la carpeta raiz del proyecto. Puedes usar el Explorador de
     archivos para abrir esa carpeta y escribir `cmd` en la barra de direcciones.
   - Conecta como `root`:

     ```bat
     "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p
     ```

   - Ingresa la contrasena de `root`. Cuando aparezca el indicador `mysql>`,
     ejecuta estas sentencias. Sustituye `PON_AQUI_UNA_CLAVE_SEGURA` por una
     contrasena propia y conserva esa misma clave para el archivo `.env`.
     Para evitar problemas al leer `.env`, usa una clave larga formada por
     letras, numeros, guion o guion bajo; no uses espacios, `:` ni `=`:

     ```sql
     CREATE DATABASE IF NOT EXISTS restaurante CHARACTER SET utf8 COLLATE utf8_spanish_ci;
     CREATE USER 'restaurante_app'@'127.0.0.1' IDENTIFIED BY 'PON_AQUI_UNA_CLAVE_SEGURA';
     GRANT ALL PRIVILEGES ON restaurante.* TO 'restaurante_app'@'127.0.0.1';
     EXIT;
     ```

   - Si la base `restaurante` ya existe, no ejecutes a ciegas el `CREATE
     DATABASE`: revisa primero si contiene datos que debas conservar. El paso de
     importacion siguiente reemplaza tablas que tengan los mismos nombres.

6. IMPORTAR EL ESQUEMA Y LOS DATOS INICIALES
   - Verifica que CMD este ubicado en la raiz del proyecto, donde esta `BD.sql`.
     Puedes comprobarlo con `dir BD.sql`.
   - Ejecuta:

     ```bat
     "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -h 127.0.0.1 -u restaurante_app -p restaurante < BD.sql
     ```

   - Ingresa la contrasena creada para `restaurante_app` cuando se solicite.
     Si CMD indica que no encuentra `BD.sql`, cambia a la carpeta del proyecto
     usando `cd /d "C:\ruta\a\Restaurante"` y repite el comando.
   - `BD.sql` es un volcado heredado y contiene `DROP TABLE IF EXISTS`; importar
     de nuevo elimina y recrea las tablas indicadas en ese archivo. Hazlo solo
     en una base vacia o despues de respaldar cualquier informacion existente.

7. CONFIGURAR LA APLICACION
   - En la carpeta raiz del proyecto, copia `.env.example` como `.env`. Desde
     CMD puedes ejecutar `copy .env.example .env`.
   - Abre `.env` con el Bloc de notas y deja estos valores, con la contrasena
     que asignaste arriba:

     ```ini
     MYSQL_DATABASE=restaurante
     MYSQL_USER=restaurante_app
     MYSQL_PASSWORD=PON_AQUI_UNA_CLAVE_SEGURA
     MYSQL_PORT=3306
     ```

   - Guarda el archivo. No publiques ni compartas `.env`, porque contiene la
     contrasena de la base de datos.

8. VERIFICAR LA IMPORTACION Y ARRANCAR EL SISTEMA
   - Desde CMD, ubicado en la raiz del proyecto, comprueba que la base contiene
     tablas:

     ```bat
     "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -h 127.0.0.1 -u restaurante_app -p -D restaurante -e "SHOW TABLES;"
     ```

   - Si aparecen tablas, MySQL acepta la conexion y la importacion esta lista.
     Ejecuta despues `iniciar_restaurante.bat`.

PROBLEMAS FRECUENTES
   - Puerto 3306 ocupado: ejecuta `netstat -ano | findstr :3306` en CMD.
     Deten el otro servicio MySQL si no lo necesitas, o configura MySQL para usar
     otro puerto y actualiza `MYSQL_PORT` en `.env` con el mismo numero.
   - Acceso denegado: revisa que `MYSQL_USER`, `MYSQL_PASSWORD` y el host del
     usuario coincidan con la cuenta creada (`restaurante_app` en `127.0.0.1`).
   - El servidor no inicia: revisa en Servicios que MySQL84 este iniciado y
     comprueba el registro `.err` de la carpeta de datos de MySQL.

Documentacion oficial de instalacion y configuracion para Windows:
https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html
https://dev.mysql.com/doc/refman/8.4/en/mysql-configurator-workflow-server.html
================================================================================
