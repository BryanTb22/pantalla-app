// Agente del panel de LitleJacob Cloud.
// Escucha solo en 127.0.0.1:8090; nginx lo publica en https://mcp.litlejacob.trade/panel/
// Cada pedido debe traer la cabecera X-Clave con la clave guardada en /etc/chrome-nube/panel.pass
'use strict';
const http = require('http');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const { exec, execSync } = require('child_process');

const CLAVE_ARCHIVO = '/etc/chrome-nube/panel.pass';
const CDP = 'http://127.0.0.1:9222';

// ---------- Seguridad: clave + bloqueo por intentos fallidos ----------
let fallos = [];
let bloqueadoHasta = 0;

function claveGuardada() {
  try { return fs.readFileSync(CLAVE_ARCHIVO, 'utf8').trim(); } catch (e) { return ''; }
}

function claveCorrecta(req) {
  const ahora = Date.now();
  if (ahora < bloqueadoHasta) return false;
  const buena = claveGuardada();
  const dada = String(req.headers['x-clave'] || '');
  const ok = buena.length > 0 && dada.length === buena.length &&
    crypto.timingSafeEqual(Buffer.from(dada), Buffer.from(buena));
  if (!ok) {
    fallos = fallos.filter(t => ahora - t < 10 * 60 * 1000);
    fallos.push(ahora);
    if (fallos.length >= 10) { bloqueadoHasta = ahora + 15 * 60 * 1000; fallos = []; }
  }
  return ok;
}

// ---------- Utilidades ----------
async function codigo(url) {
  try { const r = await fetch(url, { signal: AbortSignal.timeout(5000) }); return r.status; }
  catch (e) { return 0; }
}

function activo(servicio) {
  try { return execSync('systemctl is-active ' + servicio, { encoding: 'utf8' }).trim() === 'active'; }
  catch (e) { return false; }
}

async function pestanas() {
  try {
    const r = await fetch(CDP + '/json/list', { signal: AbortSignal.timeout(5000) });
    const lista = await r.json();
    return lista.filter(t => t.type === 'page')
      .map(t => ({ titulo: t.title || '(sin título)', url: t.url }));
  } catch (e) { return null; }
}

async function cookies() {
  try {
    const v = await (await fetch(CDP + '/json/version', { signal: AbortSignal.timeout(5000) })).json();
    const ws = new WebSocket(v.webSocketDebuggerUrl);
    return await new Promise(resolve => {
      const t = setTimeout(() => { try { ws.close(); } catch (e) {} resolve(null); }, 6000);
      ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: 'Storage.getCookies' }));
      ws.onmessage = m => {
        const d = JSON.parse(m.data);
        if (d.id === 1) { clearTimeout(t); try { ws.close(); } catch (e) {} resolve(d.result ? d.result.cookies : null); }
      };
      ws.onerror = () => { clearTimeout(t); resolve(null); };
    });
  } catch (e) { return null; }
}

// Cookie que indica sesión iniciada en cada sitio
const SITIOS = [
  { id: 'github', nombre: 'GitHub', dominio: 'github.com', cookie: 'logged_in', valor: 'yes' },
  { id: 'google', nombre: 'Google / Gmail', dominio: 'google.com', cookie: 'SID' },
  { id: 'cloudflare', nombre: 'Cloudflare', dominio: 'cloudflare.com', cookie: 'vses2' },
  { id: 'oracle', nombre: 'Oracle Cloud', dominio: 'oraclecloud.com', cookie: 'ORA_OCIS_2' },
  { id: 'tiktok', nombre: 'TikTok', dominio: 'tiktok.com', cookie: 'sessionid' },
  { id: 'meta', nombre: 'Facebook / Meta', dominio: 'facebook.com', cookie: 'c_user' },
];

function sesiones(lista) {
  if (!lista) return null;
  const ahora = Date.now() / 1000;
  return SITIOS.map(s => {
    const c = lista.find(c => c.name === s.cookie &&
      (c.domain === s.dominio || c.domain.endsWith('.' + s.dominio)) &&
      (c.expires <= 0 || c.expires > ahora) &&
      (!s.valor || c.value === s.valor));
    return { id: s.id, nombre: s.nombre, activa: !!c };
  });
}

function sistema() {
  const total = os.totalmem();
  let disponible = os.freemem();
  try {
    const m = fs.readFileSync('/proc/meminfo', 'utf8').match(/MemAvailable:\s+(\d+)/);
    if (m) disponible = Number(m[1]) * 1024;
  } catch (e) {}
  let disco = null;
  try {
    const o = execSync('df -B1 --output=size,used / | tail -1', { encoding: 'utf8' }).trim().split(/\s+/).map(Number);
    disco = { total: o[0], usado: o[1] };
  } catch (e) {}
  return {
    ram: { total, usada: total - disponible },
    disco,
    nucleos: os.cpus().length,
    carga: os.loadavg()[0],
    encendidoSeg: Math.round(os.uptime()),
  };
}

function ultimosEventos() {
  try {
    return fs.readFileSync('/var/log/chrome-nube-vigia.log', 'utf8').trim().split('\n').slice(-8).reverse();
  } catch (e) { return []; }
}

// ---------- Servidor ----------
http.createServer(async (req, res) => {
  const enviar = (c, o) => {
    res.writeHead(c, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    res.end(JSON.stringify(o));
  };
  try {
    if (!claveCorrecta(req)) return enviar(401, { error: 'clave incorrecta' });
    const ruta = req.url.split('?')[0];

    if (req.method === 'GET' && ruta === '/estado') {
      const [cdp, mcp, vnc, tabs, cks] = await Promise.all([
        codigo(CDP + '/json/version'), codigo('http://127.0.0.1:8931/'),
        codigo('http://127.0.0.1:6080/vnc.html'), pestanas(), cookies()]);
      return enviar(200, {
        chrome: cdp === 200,
        conector: mcp > 0,
        pantalla: vnc === 200,
        tunel: activo('cloudflared'),
        sistema: sistema(),
        pestanas: tabs,
        sesiones: sesiones(cks),
        eventos: ultimosEventos(),
        hora: new Date().toISOString(),
      });
    }

    if (req.method === 'POST' && ruta === '/reiniciar-chrome') {
      exec('echo "$(date \'+%F %T\') reinicio de Chrome pedido desde la app" >> /var/log/chrome-nube-vigia.log; ' +
        'systemctl restart chrome && sleep 8 && systemctl restart playwright-mcp');
      return enviar(200, { ok: true });
    }

    if (req.method === 'POST' && ruta === '/limpiar') {
      exec('/usr/local/bin/chrome-nube-mantenimiento');
      return enviar(200, { ok: true });
    }

    enviar(404, { error: 'no existe' });
  } catch (e) {
    enviar(500, { error: String(e && e.message || e) });
  }
}).listen(8090, '127.0.0.1');
