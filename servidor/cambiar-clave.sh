#!/bin/bash
# Cambia la clave de la pantalla remota y del panel. Uso: sudo bash cambiar-clave.sh NUEVA_CLAVE (máx. 8 caracteres, límite de VNC)
set -e
N="$1"
[ -n "$N" ] || { echo "falta la clave"; exit 1; }
[ ${#N} -le 8 ] || { echo "VNC solo usa 8 caracteres"; exit 1; }
mkdir -p /etc/chrome-nube && chmod 700 /etc/chrome-nube
printf '%s' "$N" > /etc/chrome-nube/panel.pass && chmod 600 /etc/chrome-nube/panel.pass
sudo -u ubuntu x11vnc -storepasswd "$N" /home/ubuntu/.vnc/passwd >/dev/null 2>&1
systemctl restart vnc
echo "clave cambiada"
