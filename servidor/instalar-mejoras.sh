#!/bin/bash
# Mejoras del Chrome nube (idempotente: se puede correr varias veces).
# Uso: sudo bash instalar-mejoras.sh   (los archivos panel.js, vigia.sh y mantenimiento.sh deben estar en la misma carpeta)
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
U=ubuntu; H=/home/$U
LOG=/var/log/chrome-nube-mejoras.log
exec > >(tee -a "$LOG") 2>&1
echo "=== Mejoras Chrome nube $(date) ==="
export DEBIAN_FRONTEND=noninteractive

# 1. Zona horaria de Lima
timedatectl set-timezone America/Lima || true

# 2. Memoria de intercambio (4 GB) para que Chrome no se caiga si falta RAM
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
sysctl -w vm.swappiness=10 >/dev/null; echo 'vm.swappiness=10' > /etc/sysctl.d/90-chrome-nube.conf

# 3. Límite de registros del sistema
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=300M\n' > /etc/systemd/journald.conf.d/chrome-nube.conf
systemctl restart systemd-journald

# 4. Actualizaciones de seguridad automáticas (reinicia a las 4:00 solo si hace falta)
apt-get install -y unattended-upgrades
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'X'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
X
cat > /etc/apt/apt.conf.d/52chrome-nube <<'X'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:00";
Unattended-Upgrade::Remove-Unused-Kernel-Packages "true";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
X

# 5. Servicios: nunca rendirse al reiniciar, y Chrome restaura sus pestañas
for s in pantalla ventanas chrome vnc novnc playwright-mcp cloudflared nginx; do
  mkdir -p /etc/systemd/system/$s.service.d
  printf '[Unit]\nStartLimitIntervalSec=0\n[Service]\nRestart=always\nRestartSec=5\n' > /etc/systemd/system/$s.service.d/10-siempre.conf
done
printf '[Unit]\nPartOf=chrome.service\n' > /etc/systemd/system/playwright-mcp.service.d/20-con-chrome.conf
printf '[Service]\nTimeoutStopSec=20\n' > /etc/systemd/system/chrome.service.d/20-cierre.conf
if grep -q ' https://github.com$' /etc/systemd/system/chrome.service; then
  sed -i 's# https://github.com$# --restore-last-session --hide-crash-restore-bubble#' /etc/systemd/system/chrome.service
fi
mkdir -p $H/Downloads && chown $U:$U $H/Downloads

# 6. Programas: vigilante, mantenimiento y agente del panel
install -m 755 "$DIR/vigia.sh" /usr/local/bin/chrome-nube-vigia
install -m 755 "$DIR/mantenimiento.sh" /usr/local/bin/chrome-nube-mantenimiento
mkdir -p /opt/chrome-nube /etc/chrome-nube
install -m 644 "$DIR/panel.js" /opt/chrome-nube/panel.js
chmod 700 /etc/chrome-nube
touch /var/log/chrome-nube-vigia.log

cat > /etc/systemd/system/chrome-nube-panel.service <<'X'
[Unit]
Description=Agente del panel de LitleJacob Cloud
After=network.target chrome.service
StartLimitIntervalSec=0
[Service]
ExecStart=/usr/bin/node /opt/chrome-nube/panel.js
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
X
cat > /etc/systemd/system/chrome-nube-vigia.service <<'X'
[Unit]
Description=Vigilante del Chrome nube
[Service]
Type=oneshot
ExecStart=/usr/local/bin/chrome-nube-vigia
X
cat > /etc/systemd/system/chrome-nube-vigia.timer <<'X'
[Unit]
Description=Revisar el Chrome nube cada 2 minutos
[Timer]
OnBootSec=3min
OnUnitActiveSec=2min
[Install]
WantedBy=timers.target
X
cat > /etc/systemd/system/chrome-nube-mantenimiento.service <<'X'
[Unit]
Description=Mantenimiento nocturno del Chrome nube
[Service]
Type=oneshot
ExecStart=/usr/local/bin/chrome-nube-mantenimiento
X
cat > /etc/systemd/system/chrome-nube-mantenimiento.timer <<'X'
[Unit]
Description=Mantenimiento nocturno del Chrome nube (4:30 a. m. Lima)
[Timer]
OnCalendar=*-*-* 04:30:00
[Install]
WantedBy=timers.target
X

# 7. nginx: publicar el panel en /panel/ (protegido por la clave del agente)
NG=/etc/nginx/sites-available/default
if ! grep -q 'location /panel/' $NG; then
  sed -i 's#  location / { return 404; }#  location /panel/ {\n    proxy_pass http://127.0.0.1:8090/;\n    proxy_read_timeout 30s;\n  }\n  location / { return 404; }#' $NG
fi
nginx -t

systemctl daemon-reload
systemctl enable --now chrome-nube-panel chrome-nube-vigia.timer chrome-nube-mantenimiento.timer
systemctl restart chrome-nube-panel
systemctl reload nginx
echo "panel: $(systemctl is-active chrome-nube-panel) | vigia: $(systemctl is-active chrome-nube-vigia.timer) | mantenimiento: $(systemctl is-active chrome-nube-mantenimiento.timer)"
echo "=== LISTO $(date) ==="
