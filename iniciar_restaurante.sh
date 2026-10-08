#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "========================================================"
echo " Iniciando Sistema de Restaurante 2026"
echo "========================================================"

if ! command -v java &> /dev/null; then
    echo "[ERROR] No se encontró Java instalado o en el PATH."
    echo "Instale Java 21 LTS (o superior) para ejecutar la aplicación."
    exit 1
fi

java -jar Restaurante.jar
