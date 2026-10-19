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
MANIFIESTO="ARTEFACTOS.sha256"
ZIP_STAGING=""
TEMP_ZIP=""
TEMP_ZIP_VERIF=""

BIBLIOTECAS=(
    "AbsoluteLayout.jar"
    "itextpdf-5.5.1.jar"
    "mysql-connector-j-8.0.31.jar"
    "pdfbox-2.0.31.jar"
    "fontbox-2.0.31.jar"
    "commons-logging-1.2.jar"
)
FILES=(
    "BD.sql" "actualizar_bd.sql" "BD RESTAURANTE 2026.sql"
    "limpiar_para_comenzar_hoy.sql" "limpiar_para_comenzar_hoy.bat" "limpiar_para_comenzar_hoy.sh"
    "actualizar_datos_cliente.bat" "actualizar_datos_cliente.sh"
    "iniciar_restaurante.bat" "iniciar_restaurante.sh" "actualizar_bd.bat"
    "instalar_mysql_y_bd.bat" "instalar_mysql_y_bd.ps1" "configurar_mysql.ps1"
    "instalar_acceso_directo.bat" "instalar_acceso_directo.ps1" "instalacion_completa_un_clic.bat"
    "iniciar_restaurante_silencioso.vbs" "icono_restaurante.ico" "icono_restaurante.png"
    "crear_acceso_directo.bat" "listar_impresoras.bat" "LEEME_INSTRUCCIONES.txt"
    "RESUMEN_CAMBIOS_Y_USO.txt"
)

cleanup_temporaries() {
    [ -z "$ZIP_STAGING" ] || rm -rf "$ZIP_STAGING"
    [ -z "$TEMP_ZIP" ] || rm -f "$TEMP_ZIP"
    [ -z "$TEMP_ZIP_VERIF" ] || rm -f "$TEMP_ZIP_VERIF"
}
trap cleanup_temporaries EXIT

# Guarda de árbol limpio: no se empaqueta código sin versionar.
if [ "${EMPAQUETAR_CON_ARBOL_SUCIO:-NO}" != "SI" ]; then
    if command -v git >/dev/null 2>&1 && git -C "$DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        CAMBIOS="$(git -C "$DIR" status --porcelain --untracked-files=no)"
        if [ -n "$CAMBIOS" ]; then
            echo "[ERROR] El árbol tiene cambios sin commitear; el artefacto de release debe nacer de un commit." >&2
            echo "$CAMBIOS" >&2
            echo "Commitee los cambios o exporte EMPAQUETAR_CON_ARBOL_SUCIO=SI (no recomendado para producción)." >&2
            exit 1
        fi
    fi
fi

if [ "${SKIP_COMPILE:-NO}" = "SI" ]; then
    [ -f "$DIST_JAR" ] || { echo "[ERROR] No existe dist/Restaurante.jar para empaquetar." >&2; exit 1; }
    echo "[1/6] Usando dist/Restaurante.jar compilado previamente..."
else
    echo "[1/6] Compilando proyecto con Ant..."
    ant jar
fi

echo "[2/6] Eliminando artefactos gestionados anteriores (evita archivos obsoletos)..."
mkdir -p "$OUTPUT_DIR/lib"
rm -f "$OUTPUT_DIR/Restaurante.jar"
for library in "${BIBLIOTECAS[@]}"; do
    rm -f "$OUTPUT_DIR/lib/$library"
done
for file in "${FILES[@]}"; do
    rm -f "$OUTPUT_DIR/$file"
done
rm -f "$OUTPUT_DIR/.env.example" "$OUTPUT_DIR/$MANIFIESTO"

echo "[3/6] Creando directorios limpios..."
mkdir -p "$OUTPUT_DIR/lib"
mkdir -p "$OUTPUT_DIR/logs"
mkdir -p "$OUTPUT_DIR/pdf"
mkdir -p "$OUTPUT_DIR/facturas"

echo "[4/6] Copiando archivos y dependencias..."
cp -f "$DIST_JAR" "$OUTPUT_DIR/Restaurante.jar"
for library in "${BIBLIOTECAS[@]}"; do
    [ ! -f "$LIB_DIR/$library" ] || cp -f "$LIB_DIR/$library" "$OUTPUT_DIR/lib/"
done
for file in "${FILES[@]}"; do
    [ ! -f "$DIR/$file" ] || cp -f "$DIR/$file" "$OUTPUT_DIR/$file"
done
[ ! -f "$DIR/.env.example" ] || cp -f "$DIR/.env.example" "$OUTPUT_DIR/.env.example"
chmod +x "$OUTPUT_DIR/limpiar_para_comenzar_hoy.sh" 2>/dev/null || true
chmod +x "$OUTPUT_DIR/actualizar_datos_cliente.sh" 2>/dev/null || true
chmod +x "$OUTPUT_DIR/iniciar_restaurante.sh" 2>/dev/null || true

echo "Normalizando terminaciones de linea Windows (CRLF) para scripts .bat, .cmd, .ps1, .vbs..."
for ext in bat cmd ps1 vbs; do
    find "$OUTPUT_DIR" -maxdepth 1 -name "*.$ext" -type f -exec sed -i 's/\r$//; s/$/\r/' {} +
done

echo "[5/6] Calculando inventario SHA-256 del artefacto..."
: > "$OUTPUT_DIR/$MANIFIESTO"
{
    echo "# Inventario SHA-256 de Restaurante_Ejecutable"
    echo "# Generado por generar_restaurante_ejecutable.sh"
    echo "Restaurante.jar  $(sha256sum "$OUTPUT_DIR/Restaurante.jar" | cut -d' ' -f1)"
    for library in "${BIBLIOTECAS[@]}"; do
        [ ! -f "$OUTPUT_DIR/lib/$library" ] || echo "lib/$library  $(sha256sum "$OUTPUT_DIR/lib/$library" | cut -d' ' -f1)"
    done
    for file in "${FILES[@]}"; do
        [ ! -f "$OUTPUT_DIR/$file" ] || echo "$file  $(sha256sum "$OUTPUT_DIR/$file" | cut -d' ' -f1)"
    done
    [ ! -f "$OUTPUT_DIR/.env.example" ] || echo ".env.example  $(sha256sum "$OUTPUT_DIR/.env.example" | cut -d' ' -f1)"
} >> "$OUTPUT_DIR/$MANIFIESTO"
echo "  -> $(grep -c '  ' "$OUTPUT_DIR/$MANIFIESTO") archivos inventariados"

echo "[6/6] Comprimiendo y verificando el ZIP..."
construir_zip() {
    local destino="$1"
    ZIP_STAGING="$(mktemp -d)"
    mkdir -p "$ZIP_STAGING/Restaurante_Ejecutable/lib"
    cp -f "$OUTPUT_DIR/Restaurante.jar" "$ZIP_STAGING/Restaurante_Ejecutable/"
    for library in "${BIBLIOTECAS[@]}"; do
        [ ! -f "$OUTPUT_DIR/lib/$library" ] || cp -f "$OUTPUT_DIR/lib/$library" "$ZIP_STAGING/Restaurante_Ejecutable/lib/"
    done
    for file in "${FILES[@]}"; do
        [ ! -f "$OUTPUT_DIR/$file" ] || cp -f "$OUTPUT_DIR/$file" "$ZIP_STAGING/Restaurante_Ejecutable/$file"
    done
    cp -f "$OUTPUT_DIR/.env.example" "$ZIP_STAGING/Restaurante_Ejecutable/.env.example"
    cp -f "$OUTPUT_DIR/$MANIFIESTO" "$ZIP_STAGING/Restaurante_Ejecutable/$MANIFIESTO"
    # Timestamps fijos + sin atributos extra: ZIP reproducible para los mismos insumos.
    TZ=UTC find "$ZIP_STAGING" -exec touch -t 198001010000.00 {} +
    (cd "$ZIP_STAGING" && zip -X -qr "$destino" "Restaurante_Ejecutable")
    rm -rf "$ZIP_STAGING"
    ZIP_STAGING=""
}

TEMP_ZIP="$(mktemp "$DIR/Restaurante_Ejecutable.zip.tmp.XXXXXX")"
rm -f "$TEMP_ZIP"
construir_zip "$TEMP_ZIP"
if unzip -Z1 "$TEMP_ZIP" | rg -n '(^|/)\.env($|\.)|\.(key|pem|p12|jks)$|\.sql\.gz$|\.log$' | rg -v '/\.env\.example$'; then
    echo "[ERROR] El ZIP contiene un archivo sensible; no se reemplazó el archivo anterior." >&2
    exit 1
fi
mv -f "$TEMP_ZIP" "$ZIP_FILE"

if [ "${VERIFICAR_ZIP_REPRODUCIBLE:-SI}" = "SI" ]; then
    TEMP_ZIP_VERIF="$(mktemp "$DIR/Restaurante_Ejecutable.zip.verif.XXXXXX")"
    rm -f "$TEMP_ZIP_VERIF"
    construir_zip "$TEMP_ZIP_VERIF"
    if [ "$(sha256sum "$ZIP_FILE" | cut -d' ' -f1)" != "$(sha256sum "$TEMP_ZIP_VERIF" | cut -d' ' -f1)" ]; then
        echo "[ERROR] El ZIP no es reproducible: dos empaquetados con los mismos insumos difieren." >&2
        exit 1
    fi
    rm -f "$TEMP_ZIP_VERIF"
    TEMP_ZIP_VERIF=""
    echo "  -> ZIP reproducible verificado (mismo SHA-256 en dos empaquetados)"
fi

sha256sum "$ZIP_FILE" > "$ZIP_FILE.sha256"
echo "================================================================================"
echo "  [OK] Restaurante_Ejecutable y Restaurante_Ejecutable.zip generados con éxito."
echo "  SHA-256 JAR: $(sha256sum "$OUTPUT_DIR/Restaurante.jar" | cut -d' ' -f1)"
echo "  SHA-256 ZIP: $(sha256sum "$ZIP_FILE" | cut -d' ' -f1)"
echo "================================================================================"
