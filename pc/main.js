// LitleJacob Cloud para PC (Windows). Misma función que la app del celular:
// panel del servidor, ver/controlar la pantalla, avisos si se cae y actualizador.
'use strict';
const { app, BrowserWindow, ipcMain, Tray, Menu, Notification, shell, net, safeStorage, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');

const PANTALLA = 'https://pantalla.litlejacob.trade/vnc.html';
const PANEL = 'https://mcp.litlejacob.trade/panel/';
const PRUEBA_INTERNET = 'https://www.google.com/generate_204';
const CADA_REVISION = 5 * 60 * 1000; // en la PC revisamos cada 5 minutos

let win = null, visores = {}, tray = null, saliendo = false;
let updater = null, actualizacion = null;
// Modo prueba (lo usa GitHub): saca capturas del panel y del visor y se cierra.
const CAPTURA = process.env.LJ_CAPTURA || '';

// ---------- Configuración (guardada solo en esta PC) ----------
const CFG = () => path.join(app.getPath('userData'), 'config.json');
let cfg = { aviso: true, inicioWindows: false, estado: {} };
function cargarCfg() {
  try { cfg = Object.assign(cfg, JSON.parse(fs.readFileSync(CFG(), 'utf8'))); } catch (e) { }
}
function guardarCfg() {
  try { fs.writeFileSync(CFG(), JSON.stringify(cfg, null, 2)); } catch (e) { }
}
function leerClave() {
  if (!cfg.clave) return '';
  try {
    const b = Buffer.from(cfg.clave, 'base64');
    return cfg.claveCifrada ? safeStorage.decryptString(b) : b.toString('utf8');
  } catch (e) { return ''; }
}
function guardarClave(c) {
  const cifrar = safeStorage.isEncryptionAvailable();
  cfg.clave = (cifrar ? safeStorage.encryptString(c) : Buffer.from(c, 'utf8')).toString('base64');
  cfg.claveCifrada = cifrar;
  guardarCfg();
}

// ---------- Red ----------
async function codigo(url) {
  try {
    const r = await net.fetch(url, { method: 'GET', cache: 'no-store', signal: AbortSignal.timeout(8000) });
    return r.status;
  } catch (e) { return -1; }
}
async function panel(metodo, ruta) {
  try {
    const r = await net.fetch(PANEL + ruta, {
      method: metodo, cache: 'no-store', headers: { 'X-Clave': leerClave() },
      body: metodo === 'POST' ? '' : undefined, signal: AbortSignal.timeout(15000),
    });
    let datos = null;
    try { datos = await r.json(); } catch (e) { }
    return { codigo: r.status, datos };
  } catch (e) { return { codigo: -1, datos: null }; }
}

// ---------- Ventanas ----------
const ICONO = path.join(__dirname, 'ui', 'icono.png');

function abrirPanel() {
  if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); return; }
  win = new BrowserWindow({
    width: 1100, height: 780, minWidth: 420, minHeight: 560,
    backgroundColor: '#0B1020', icon: ICONO, title: 'LitleJacob Cloud',
    autoHideMenuBar: true, show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true },
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, 'ui', 'index.html'));
  win.once('ready-to-show', () => win.show());
  win.on('close', e => {
    // Con el aviso activado, cerrar la ventana la deja en la bandeja (junto al reloj).
    if (!saliendo && cfg.aviso) {
      e.preventDefault();
      win.hide();
      if (!cfg.yaAvisoBandeja) {
        cfg.yaAvisoBandeja = true; guardarCfg();
        notificar('LitleJacob Cloud sigue vigilando', 'Quedó junto al reloj. Te aviso si el servidor se cae. Para cerrarlo del todo: clic derecho en el ícono → Salir.');
      }
    }
  });
  win.on('closed', () => { win = null; });
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
}

function abrirVisor(controlar) {
  const k = controlar ? 'controlar' : 'ver';
  const v = visores[k];
  if (v && !v.isDestroyed()) { if (v.isMinimized()) v.restore(); v.focus(); return; }
  const w = new BrowserWindow({
    width: 1440, height: 900, backgroundColor: '#000000', icon: ICONO,
    title: controlar ? 'LitleJacob Cloud — Controlar pantalla' : 'LitleJacob Cloud — Ver pantalla',
    autoHideMenuBar: true, show: false,
    webPreferences: { contextIsolation: true, sandbox: true },
  });
  w.removeMenu();
  let url = PANTALLA + '?autoconnect=true&resize=scale&reconnect=true&reconnect_delay=3000&show_dot=true'
    + '&view_only=' + (controlar ? 'false' : 'true');
  const clave = leerClave();
  if (clave) url += '&password=' + encodeURIComponent(clave);
  w.loadURL(url);
  w.once('ready-to-show', () => { w.maximize(); w.show(); });
  // F11 = pantalla completa, Esc = salir de pantalla completa.
  w.webContents.on('before-input-event', (e, i) => {
    if (i.type !== 'keyDown') return;
    if (i.key === 'F11') { w.setFullScreen(!w.isFullScreen()); e.preventDefault(); }
    else if (i.key === 'Escape' && w.isFullScreen()) { w.setFullScreen(false); e.preventDefault(); }
    else if (i.key === 'F5') { w.webContents.reload(); e.preventDefault(); }
  });
  w.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  w.webContents.on('will-navigate', (e, u) => { if (!u.startsWith('https://pantalla.litlejacob.trade/')) e.preventDefault(); });
  // El título de noVNC no reemplaza el nuestro.
  w.on('page-title-updated', e => e.preventDefault());
  visores[k] = w;
}

function abrirClaude() {
  shell.openExternal('https://claude.ai');
}

// ---------- Bandeja y avisos ----------
function notificar(titulo, cuerpo) {
  if (!Notification.isSupported()) return;
  const n = new Notification({ title: titulo, body: cuerpo, icon: ICONO });
  n.on('click', abrirPanel);
  n.show();
}

function crearBandeja() {
  if (tray) return;
  tray = new Tray(nativeImage.createFromPath(ICONO).resize({ width: 16, height: 16 }));
  tray.setToolTip('LitleJacob Cloud');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Abrir panel', click: abrirPanel },
    { label: 'Ver pantalla', click: () => abrirVisor(false) },
    { label: 'Controlar pantalla', click: () => abrirVisor(true) },
    { type: 'separator' },
    { label: 'Salir', click: () => { saliendo = true; app.quit(); } },
  ]));
  tray.on('click', abrirPanel);
}

let timerVigia = null;
function programarVigia() {
  clearInterval(timerVigia);
  if (!cfg.aviso) return;
  timerVigia = setInterval(vigilar, CADA_REVISION);
  setTimeout(vigilar, 20000);
}

async function vigilar() {
  // ¿El servidor responde? (si la PC no tiene internet, no avisamos nada)
  let c = await codigo(PANTALLA);
  if (c !== 200) {
    await new Promise(r => setTimeout(r, 20000));
    c = await codigo(PANTALLA);
  }
  const hayInternet = c === 200 || (await codigo(PRUEBA_INTERNET)) === 204;
  if (!hayInternet) return;
  const arriba = c === 200;
  const e = cfg.estado;
  const antes = e.servidorOk !== false;
  if (antes && !arriba) notificar('Tu servidor no responde', 'La pantalla de LitleJacob Cloud está caída. Revisa el servidor en Oracle Cloud.');
  else if (!antes && arriba) notificar('Servidor en línea de nuevo', 'Ya puedes volver a usar LitleJacob Cloud.');
  e.servidorOk = arriba;

  if (arriba && leerClave()) {
    const r = await panel('GET', 'estado');
    if (r.codigo === 200 && r.datos) {
      const j = r.datos;
      const bien = j.conector && j.chrome;
      const fallosAntes = e.conFallos || 0;
      const fallos = bien ? 0 : fallosAntes + 1;
      if (fallos === 2) notificar('Claude no puede usar tu Chrome nube', 'El conector o Chrome no responden desde hace un rato. Abre LitleJacob Cloud y usa Reiniciar Chrome.');
      else if (bien && fallosAntes >= 2) notificar('Chrome nube funcionando de nuevo', 'El conector volvió a responder.');
      e.conFallos = fallos;
      e.sesiones = e.sesiones || {};
      for (const s of j.sesiones || []) {
        if (e.sesiones[s.id] === true && !s.activa) {
          notificar('Se cerró la sesión de ' + s.nombre, 'Entra con "Controlar pantalla" para volver a iniciarla.');
        }
        e.sesiones[s.id] = !!s.activa;
      }
    }
  }
  guardarCfg();
}

// ---------- Actualizaciones ----------
function enviarUpd() {
  if (win && !win.isDestroyed()) win.webContents.send('upd', actualizacion);
}
function iniciarActualizador() {
  if (!app.isPackaged) return;
  try {
    updater = require('electron-updater').autoUpdater;
    updater.autoDownload = true;
    updater.autoInstallOnAppQuit = true;
    updater.on('update-available', i => { actualizacion = { estado: 'descargando', version: i.version }; enviarUpd(); });
    updater.on('download-progress', p => { actualizacion = Object.assign({}, actualizacion, { progreso: Math.round(p.percent) }); enviarUpd(); });
    updater.on('update-downloaded', i => { actualizacion = { estado: 'lista', version: i.version }; enviarUpd(); });
    updater.on('update-not-available', () => { if (actualizacion && actualizacion.manual) { actualizacion = { estado: 'al-dia' }; enviarUpd(); } });
    updater.on('error', () => { if (actualizacion && actualizacion.manual) { actualizacion = { estado: 'error' }; enviarUpd(); } });
    updater.checkForUpdates().catch(() => { });
    setInterval(() => updater.checkForUpdates().catch(() => { }), 3 * 60 * 60 * 1000);
  } catch (e) { updater = null; }
}

// ---------- Puente con el panel ----------
ipcMain.handle('estado', async () => {
  const r = await panel('GET', 'estado');
  const codPantalla = r.codigo === 200 && r.datos ? 0 : await codigo(PANTALLA);
  return { codPanel: r.codigo, datos: r.codigo === 200 ? r.datos : null, codPantalla };
});
ipcMain.handle('accion', async (e, ruta) => {
  if (!['reiniciar-chrome', 'limpiar'].includes(ruta)) return { codigo: 400 };
  const r = await panel('POST', ruta);
  return { codigo: r.codigo };
});
ipcMain.handle('tiene-clave', () => !!leerClave());
ipcMain.handle('guardar-clave', (e, c) => { guardarClave(String(c || '').trim()); return true; });
ipcMain.handle('visor', (e, controlar) => {
  if (!leerClave()) return false;
  abrirVisor(!!controlar);
  return true;
});
ipcMain.handle('claude', () => abrirClaude());
ipcMain.handle('ajustes', () => ({ aviso: cfg.aviso, inicioWindows: cfg.inicioWindows, version: app.getVersion(), captura: !!CAPTURA }));
ipcMain.handle('ajuste', (e, k, v) => {
  if (k === 'aviso') { cfg.aviso = !!v; programarVigia(); }
  if (k === 'inicioWindows') {
    cfg.inicioWindows = !!v;
    app.setLoginItemSettings({ openAtLogin: !!v, args: ['--oculto'] });
  }
  guardarCfg();
  return true;
});
ipcMain.handle('buscar-actualizacion', async () => {
  if (!updater) return { estado: 'dev' };
  if (actualizacion && (actualizacion.estado === 'lista' || actualizacion.estado === 'descargando')) return actualizacion;
  actualizacion = { estado: 'buscando', manual: true };
  updater.checkForUpdates().catch(() => { actualizacion = { estado: 'error' }; enviarUpd(); });
  return actualizacion;
});
ipcMain.handle('upd-estado', () => actualizacion);
ipcMain.handle('instalar-actualizacion', () => {
  if (updater && actualizacion && actualizacion.estado === 'lista') { saliendo = true; updater.quitAndInstall(true, true); }
});

async function capturar() {
  const espera = ms => new Promise(r => setTimeout(r, ms));
  fs.mkdirSync(CAPTURA, { recursive: true });
  const log = t => fs.appendFileSync(path.join(CAPTURA, 'prueba-pc-log.txt'), new Date().toISOString() + ' ' + t + '\n');
  try {
    log('version ' + app.getVersion());
    abrirPanel();
    win.webContents.on('console-message', (e, nivel, msg, linea, fuente) => log('consola: ' + msg + ' (' + fuente + ':' + linea + ')'));
    win.webContents.on('preload-error', (e, p, err) => log('preload-error: ' + err));
    await espera(25000);
    fs.writeFileSync(path.join(CAPTURA, 'prueba-pc-panel.png'), (await win.webContents.capturePage()).toPNG());
    log('panel: ' + await win.webContents.executeJavaScript("document.getElementById('estado').textContent + ' | ' + document.getElementById('detalle').textContent + ' | aviso=' + document.getElementById('swAviso').checked + ' | ' + document.getElementById('version').textContent + ' | lj=' + typeof window.lj"));
    abrirVisor(false);
    await espera(15000);
    fs.writeFileSync(path.join(CAPTURA, 'prueba-pc-visor.png'), (await visores.ver.webContents.capturePage()).toPNG());
    log('visor: ' + visores.ver.webContents.getURL().replace(/password=[^&]*/, 'password=***'));
    log('OK');
  } catch (e) { log('ERROR ' + (e && e.stack || e)); }
  saliendo = true;
  app.exit(0);
}

// ---------- Arranque ----------
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', abrirPanel);
  app.setAppUserModelId('trade.litlejacob.cloud');
  app.whenReady().then(() => {
    cargarCfg();
    crearBandeja();
    if (CAPTURA) { capturar(); return; }
    if (!process.argv.includes('--oculto')) abrirPanel();
    programarVigia();
    iniciarActualizador();
  });
  app.on('before-quit', () => { saliendo = true; });
  app.on('window-all-closed', () => {
    if (!cfg.aviso) app.quit(); // con el aviso apagado se cierra del todo
  });
}
