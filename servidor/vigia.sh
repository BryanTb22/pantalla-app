#!/bin/bash
# Vigilante del Chrome nube: revisa cada 2 minutos y reinicia lo que se haya colgado.
LOG=/var/log/chrome-nube-vigia.log
cod() { curl -s -o /dev/null -m 8 -w '%{http_code}' "$1"; }
anota() { echo "$(date '+%F %T') $*" >> "$LOG"; }

# No tocar nada durante el mantenimiento nocturno.
[ -f /run/chrome-nube-mantenimiento ] && exit 0

if ! systemctl is-active -q pantalla; then
  anota "pantalla virtual caída -> reinicio completo"
  systemctl restart pantalla; sleep 3; systemctl restart ventanas chrome vnc novnc; sleep 8; systemctl restart playwright-mcp
  exit 0
fi

# Chrome: dos intentos antes de reiniciar
if [ "$(cod http://127.0.0.1:9222/json/version)" != "200" ]; then
  sleep 15
  if [ "$(cod http://127.0.0.1:9222/json/version)" != "200" ]; then
    anota "Chrome no responde -> reinicio Chrome y conector"
    systemctl restart chrome; sleep 8; systemctl restart playwright-mcp
    exit 0
  fi
fi

# Conector (Playwright MCP): cualquier respuesta HTTP vale
if [ "$(cod http://127.0.0.1:8931/)" = "000" ]; then
  sleep 10
  if [ "$(cod http://127.0.0.1:8931/)" = "000" ]; then
    anota "conector no responde -> reinicio conector"
    systemctl restart playwright-mcp
  fi
fi

# Pantalla remota
if [ "$(cod http://127.0.0.1:6080/vnc.html)" != "200" ]; then
  anota "pantalla remota no responde -> reinicio vnc y novnc"
  systemctl restart vnc novnc
fi

# Panel, nginx y túnel
systemctl is-active -q chrome-nube-panel || { anota "panel caído"; systemctl restart chrome-nube-panel; }
systemctl is-active -q nginx || { anota "nginx caído"; systemctl restart nginx; }
systemctl is-active -q cloudflared || { anota "túnel caído"; systemctl restart cloudflared; }

# Mantener el log chico
[ -f "$LOG" ] && [ "$(wc -l < "$LOG")" -gt 2000 ] && tail -n 1000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
exit 0
