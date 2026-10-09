#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "================================================================================"
echo "  REGENERADOR Y EMPAQUETADOR DE RESTAURANTE_EJECUTABLE (LINUX / UNIX)          "
echo "================================================================================"

OUTPUT_DIR="$DIR/Restaurante_Ejecutable"
ZIP_FILE="$DIR/Restaurante_Ejecutable.zip"
DIST_JAR="$DIR/dist/Restaurante.jar"
LIB_DIR="$DIR/librerias"

echo "[1/5] Compilando proyecto con Ant..."
ant jar

echo "[2/5] Limpiando artefactos previos..."
rm -rf "$OUTPUT_DIR"
rm -f "$ZIP_FILE"

echo "[3/5] Creando directorios limpios..."
mkdir -p "$OUTPUT_DIR/lib"
mkdir -p "$OUTPUT_DIR/logs"
mkdir -p "$OUTPUT_DIR/pdf"
mkdir -p "$OUTPUT_DIR/facturas"

echo "[4/5] Copiando archivos y dependencias..."
cp -f "$DIST_JAR" "$OUTPUT_DIR/Restaurante.jar"
cp -f "$LIB_DIR/AbsoluteLayout.jar" "$OUTPUT_DIR/lib/"
cp -f "$LIB_DIR/itextpdf-5.5.1.jar" "$OUTPUT_DIR/lib/"
cp -f "$LIB_DIR/mysql-connector-j-8.0.31.jar" "$OUTPUT_DIR/lib/"
[ -f "$LIB_DIR/pdfbox-2.0.31.jar" ] && cp -f "$LIB_DIR/pdfbox-2.0.31.jar" "$OUTPUT_DIR/lib/"
[ -f "$LIB_DIR/fontbox-2.0.31.jar" ] && cp -f "$LIB_DIR/fontbox-2.0.31.jar" "$OUTPUT_DIR/lib/"
[ -f "$LIB_DIR/commons-logging-1.2.jar" ] && cp -f "$LIB_DIR/commons-logging-1.2.jar" "$OUTPUT_DIR/lib/"

cp -f "$DIR/BD.sql" "$OUTPUT_DIR/BD.sql"
cp -f "$DIR/actualizar_bd.sql" "$OUTPUT_DIR/actualizar_bd.sql"
[ -f "$DIR/iniciar_restaurante.bat" ] && cp -f "$DIR/iniciar_restaurante.bat" "$OUTPUT_DIR/"
[ -f "$DIR/iniciar_restaurante.sh" ] && cp -f "$DIR/iniciar_restaurante.sh" "$OUTPUT_DIR/" && chmod +x "$OUTPUT_DIR/iniciar_restaurante.sh"
[ -f "$DIR/actualizar_bd.bat" ] && cp -f "$DIR/actualizar_bd.bat" "$OUTPUT_DIR/"
[ -f "$DIR/instalar_mysql_y_bd.bat" ] && cp -f "$DIR/instalar_mysql_y_bd.bat" "$OUTPUT_DIR/"
[ -f "$DIR/configurar_mysql.ps1" ] && cp -f "$DIR/configurar_mysql.ps1" "$OUTPUT_DIR/"
[ -f "$DIR/instalar_acceso_directo.bat" ] && cp -f "$DIR/instalar_acceso_directo.bat" "$OUTPUT_DIR/"
[ -f "$DIR/instalar_acceso_directo.ps1" ] && cp -f "$DIR/instalar_acceso_directo.ps1" "$OUTPUT_DIR/"
[ -f "$DIR/instalacion_completa_un_clic.bat" ] && cp -f "$DIR/instalacion_completa_un_clic.bat" "$OUTPUT_DIR/"
[ -f "$DIR/iniciar_restaurante_silencioso.vbs" ] && cp -f "$DIR/iniciar_restaurante_silencioso.vbs" "$OUTPUT_DIR/"
[ -f "$DIR/icono_restaurante.ico" ] && cp -f "$DIR/icono_restaurante.ico" "$OUTPUT_DIR/"
[ -f "$DIR/icono_restaurante.png" ] && cp -f "$DIR/icono_restaurante.png" "$OUTPUT_DIR/"
[ -f "$DIR/listar_impresoras.bat" ] && cp -f "$DIR/listar_impresoras.bat" "$OUTPUT_DIR/"
[ -f "$DIR/LEEME_INSTRUCCIONES.txt" ] && cp -f "$DIR/LEEME_INSTRUCCIONES.txt" "$OUTPUT_DIR/"
[ -f "$DIR/RESUMEN_CAMBIOS_Y_USO.txt" ] && cp -f "$DIR/RESUMEN_CAMBIOS_Y_USO.txt" "$OUTPUT_DIR/"

if [ -f "$DIR/.env" ]; then
    cp -f "$DIR/.env" "$OUTPUT_DIR/.env"
fi

echo "[5/5] Comprimiendo archivo ZIP..."
cd "$DIR"
zip -r "$ZIP_FILE" "Restaurante_Ejecutable" > /dev/null

echo "================================================================================"
echo "  [OK] Restaurante_Ejecutable y Restaurante_Ejecutable.zip generados con éxito."
echo "================================================================================"
