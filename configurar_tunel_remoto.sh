#!/bin/bash
set -euo pipefail

# ==============================================================================
# CONFIGURADOR DE TÚNEL INVERSO SSH ZERO-TRUST — RESTAURANTE 2026
# Genera par de claves Ed25519 y directivas de confinamiento estricto para el VPS
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR_CLAVES="${SCRIPT_DIR}/capip_claves_maestras"
CLAVE_PRIVADA="${DIR_CLAVES}/tunel_ed25519"
CLAVE_PUBLICA="${DIR_CLAVES}/tunel_ed25519.pub"

echo "================================================================================"
echo "    CONFIGURACIÓN DE TÚNEL INVERSO SSH ZERO-TRUST (MONITOREO REMOTO)           "
echo "================================================================================"

mkdir -p "${DIR_CLAVES}"
chmod 700 "${DIR_CLAVES}"

if [ ! -f "${CLAVE_PRIVADA}" ]; then
    echo "[1/3] Generando par de claves Ed25519 para telemetría remota..."
    ssh-keygen -t ed25519 -f "${CLAVE_PRIVADA}" -N "" -C "restaurante-telemetria-remota" > /dev/null
    chmod 600 "${CLAVE_PRIVADA}"
    chmod 644 "${CLAVE_PUBLICA}"
    echo "  -> Clave privada generada: ${CLAVE_PRIVADA}"
    echo "  -> Clave pública generada: ${CLAVE_PUBLICA}"
else
    echo "[1/3] Clave Ed25519 existente detectada:"
    echo "  -> ${CLAVE_PRIVADA}"
fi

CONTENIDO_PUB="$(cat "${CLAVE_PUBLICA}")"

echo ""
echo "================================================================================"
echo " [PASO OBLIGATORIO EN TU SERVIDOR BASTIÓN EN LA NUBE (VPS)]"
echo "================================================================================"
echo "En tu servidor VPS (Ubuntu/Debian), ejecuta los siguientes comandos como root:"
echo ""
echo "  1. Crear el usuario del túnel sin privilegios ni terminal interactiva:"
echo "     useradd -m -s /bin/false telemetria"
echo "     mkdir -p /home/telemetria/.ssh"
echo "     chmod 700 /home/telemetria/.ssh"
echo ""
echo "  2. Agregar EXACTAMENTE la siguiente línea confinada en:"
echo "     /home/telemetria/.ssh/authorized_keys"
echo ""
echo "--------------------------------------------------------------------------------"
echo "command=\"/bin/false\",no-agent-forwarding,no-user-rc,no-X11-forwarding,no-pty,permitopen=\"127.0.0.1:8080\" ${CONTENIDO_PUB}"
echo "--------------------------------------------------------------------------------"
echo ""
echo "  3. Ajustar permisos en el VPS:"
echo "     chmod 600 /home/telemetria/.ssh/authorized_keys"
echo "     chown -R telemetria:telemetria /home/telemetria/.ssh"
echo ""
echo "================================================================================"
echo " [PASO LOCAL EN ESTE RESTAURANTE]"
echo "================================================================================"
echo "Configura en tu archivo .env las siguientes variables:"
echo ""
echo "  TUNEL_SSH_HABILITADO=true"
echo "  TUNEL_SSH_HOST=<IP_O_DOMINIO_DE_TU_VPS>"
echo "  TUNEL_SSH_PUERTO=22"
echo "  TUNEL_SSH_USUARIO=telemetria"
echo "  TUNEL_SSH_PUERTO_REMOTO=8443"
echo "  TUNEL_SSH_CLAVE_PRIVADA=${CLAVE_PRIVADA}"
echo "  TUNEL_SSH_TOKEN_ADMIN=secreto_gerencial_123"
echo ""
echo "Al reiniciar la aplicación, el túnel se conectará automáticamente y el dueño"
echo "podrá ver sus ventas en vivo en su celular abriendo:"
echo "  http://<IP_DE_TU_VPS>:8443/gerencia"
echo "================================================================================"
