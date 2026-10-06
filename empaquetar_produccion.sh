#!/usr/bin/env bash
# ==============================================================================
# Script para Generar el Archivo ZIP de Producción - Sistema de Restaurante 2026
# ==============================================================================
set -e

DIR_ACTUAL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR_ACTUAL"

ARCHIVO_ZIP="../Restaurante_2026_Produccion.zip"

echo "============================================================"
echo "  EMPAQUETANDO SISTEMA DE RESTAURANTE 2026 PARA PRODUCCIÓN  "
echo "============================================================"
echo ""

# 1. Cargar Java 21 si se usa SDKMAN
if [ -d "$HOME/.sdkman" ]; then
    export SDKMAN_DIR="$HOME/.sdkman"
    [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# 2. Reconstruir el JAR distribuible limpio
echo "[1/3] Compilando y empaquetando dist/Restaurante.jar con Java 21..."
if command -v ant >/dev/null 2>&1; then
    ant clean jar
else
    echo "[AVISO] Apache Ant no encontrado en el PATH; usando dist/ existente."
fi

# 3. Eliminar ZIP previo si existe
if [ -f "$ARCHIVO_ZIP" ]; then
    echo "[2/3] Eliminando versión anterior del ZIP..."
    rm -f "$ARCHIVO_ZIP"
fi

# 4. Generar el archivo ZIP excluyendo temporales y control de versiones
echo "[3/3] Comprimiendo archivos limpios..."
zip -r "$ARCHIVO_ZIP" . \
    -x "*.git*" \
    -x "build/*" \
    -x ".DS_Store" \
    -x "*.log" \
    -x "logs/*" \
    -x "empaquetar_produccion.sh"

echo ""
echo "============================================================"
echo "  ✅ ARCHIVO ZIP GENERADO EXITOSAMENTE"
echo "============================================================"
ls -lh "$ARCHIVO_ZIP"
echo "Ubicación: $(realpath "$ARCHIVO_ZIP")"
echo "============================================================"
