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
Soporte y Contacto del Desarrollador:
Proyecto Restaurante 2026 - Codigo fuente bajo control de versiones Git.
================================================================================
