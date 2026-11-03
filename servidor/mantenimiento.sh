#!/bin/bash
# Mantenimiento nocturno: reinicia Chrome (restaura pestañas) y limpia caché y archivos viejos.
H=/home/ubuntu
LOG=/var/log/chrome-nube-vigia.log
touch /run/chrome-nube-mantenimiento
echo "$(date '+%F %T') mantenimiento: inicio" >> "$LOG"
systemctl stop playwright-mcp chrome
sleep 3
for d in Cache "Code Cache" GPUCache DawnGraphiteCache DawnWebGPUCache; do rm -rf "$H/perfil-chrome/Default/$d"; done
rm -rf "$H/perfil-chrome/ShaderCache" "$H/perfil-chrome/GrShaderCache" "$H/perfil-chrome/GraphiteDawnCache"
find "$H/Downloads" -type f -mtime +14 -delete 2>/dev/null
find /tmp -maxdepth 1 -mtime +3 \( -name 'playwright*' -o -name '.org.chromium*' \) -exec rm -rf {} + 2>/dev/null
journalctl --vacuum-size=200M >/dev/null 2>&1
apt-get clean
systemctl start chrome
sleep 8
systemctl start playwright-mcp
rm -f /run/chrome-nube-mantenimiento
echo "$(date '+%F %T') mantenimiento: fin ($(df -h / | awk 'NR==2{print $5}') de disco usado)" >> "$LOG"
