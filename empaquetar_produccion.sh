#!/usr/bin/env bash
# ==============================================================================
# Script para Generar el Archivo ZIP de Producción - Sistema de Restaurante 2026
# ==============================================================================
set -euo pipefail

DIR_ACTUAL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR_ACTUAL"

ARCHIVO_ZIP="${ARCHIVO_ZIP:-../Restaurante_2026_Produccion.zip}"
case "$ARCHIVO_ZIP" in
    /*) ;;
    *) ARCHIVO_ZIP="$DIR_ACTUAL/$ARCHIVO_ZIP" ;;
esac

echo "============================================================"
echo "  EMPAQUETANDO SISTEMA DE RESTAURANTE 2026 PARA PRODUCCIÓN  "
echo "============================================================"
echo ""

# 1. Cargar Java 21 si se usa SDKMAN
if [ -d "$HOME/.sdkman" ]; then
    export SDKMAN_DIR="$HOME/.sdkman"
    if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
        set +u
        source "$HOME/.sdkman/bin/sdkman-init.sh"
        set -u
    fi
fi

# 2. Reconstruir el JAR distribuible limpio
echo "[1/3] Compilando y empaquetando dist/Restaurante.jar con Java 21..."
if command -v ant >/dev/null 2>&1; then
    ant clean jar
else
    echo "[AVISO] Apache Ant no encontrado en el PATH; usando dist/ existente."
fi

# 3. Crear staging desde una lista blanca. Nunca se comprime el árbol del repositorio.
echo "[2/3] Preparando staging desde archivos permitidos..."
if [ ! -d "Restaurante_Ejecutable" ]; then
    echo "[ERROR] Falta Restaurante_Ejecutable para tomar los recursos de runtime." >&2
    exit 1
fi
STAGING="$(mktemp -d)"
mkdir -p "$(dirname "$ARCHIVO_ZIP")"
TEMP_ZIP="$(mktemp "${ARCHIVO_ZIP}.tmp.XXXXXX")"
rm -f "$TEMP_ZIP"
cleanup() { rm -rf "$STAGING"; rm -f "$TEMP_ZIP"; }
trap cleanup EXIT
mkdir -p "$STAGING/Restaurante_Ejecutable/lib"

cp -f "dist/Restaurante.jar" "$STAGING/Restaurante_Ejecutable/Restaurante.jar"
for library in AbsoluteLayout.jar itextpdf-5.5.1.jar mysql-connector-j-8.0.31.jar pdfbox-2.0.31.jar fontbox-2.0.31.jar commons-logging-1.2.jar; do
    if [ -f "Restaurante_Ejecutable/lib/$library" ]; then
        cp -f "Restaurante_Ejecutable/lib/$library" "$STAGING/Restaurante_Ejecutable/lib/"
    fi
done
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
for file in "${FILES[@]}"; do
    if [ -f "$DIR_ACTUAL/$file" ]; then
        cp -f "$DIR_ACTUAL/$file" "$STAGING/Restaurante_Ejecutable/$file"
    elif [ -f "Restaurante_Ejecutable/$file" ]; then
        cp -f "Restaurante_Ejecutable/$file" "$STAGING/Restaurante_Ejecutable/$file"
    fi
done
if [ -f ".env.example" ]; then
    cp -f ".env.example" "$STAGING/Restaurante_Ejecutable/.env.example"
elif [ -f "Restaurante_Ejecutable/.env.example" ]; then
    cp -f "Restaurante_Ejecutable/.env.example" "$STAGING/Restaurante_Ejecutable/.env.example"
else
    echo "[ERROR] Falta .env.example en el paquete." >&2
    exit 1
fi
for required in BD.sql actualizar_bd.sql Restaurante.jar .env.example; do
    [ -f "$STAGING/Restaurante_Ejecutable/$required" ] || {
        echo "[ERROR] Falta archivo requerido en el paquete: $required" >&2
        exit 1
    }
done

echo "[3/3] Comprimiendo staging limpio..."
(cd "$STAGING" && zip -qr "$TEMP_ZIP" "Restaurante_Ejecutable")
if unzip -Z1 "$TEMP_ZIP" | rg -n '(^|/)\.env($|\.)|\.(key|pem|p12|jks)$|\.sql\.gz$|\.log$' | rg -v '/\.env\.example$'; then
    echo "[ERROR] El ZIP contiene un archivo prohibido; se cancela la publicación." >&2
    exit 1
fi
mv -f "$TEMP_ZIP" "$ARCHIVO_ZIP"

echo ""
echo "============================================================"
echo "  ✅ ARCHIVO ZIP GENERADO EXITOSAMENTE"
echo "============================================================"
ls -lh "$ARCHIVO_ZIP"
echo "Ubicación: $(realpath "$ARCHIVO_ZIP")"
echo "============================================================"
