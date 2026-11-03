'use strict';
const $ = id => document.getElementById(id);
const lj = window.lj;

function el(tag, clase, texto) {
  const e = document.createElement(tag);
  if (clase) e.className = clase;
  if (texto != null) e.textContent = texto;
  return e;
}

// ---------- Avisos y diálogos ----------
let tToast;
function toast(msg, largo) {
  const t = $('toast');
  t.textContent = msg; t.hidden = false;
  clearTimeout(tToast);
  tToast = setTimeout(() => { t.hidden = true; }, largo ? 4500 : 2500);
}

function dialogo({ titulo, mensaje, si = 'Sí', no = 'Cancelar', pedir = false, valor = '' }) {
  return new Promise(resolve => {
    $('dTitulo').textContent = titulo;
    $('dMensaje').textContent = mensaje;
    $('dSi').textContent = si; $('dNo').textContent = no;
    const inp = $('dInput');
    inp.hidden = !pedir; inp.value = valor;
    $('velo').hidden = false;
    (pedir ? inp : $('dSi')).focus();
    const fin = r => {
      $('velo').hidden = true;
      $('dSi').onclick = $('dNo').onclick = inp.onkeydown = null;
      document.onkeydown = null;
      resolve(r);
    };
    $('dSi').onclick = () => fin(pedir ? inp.value : true);
    $('dNo').onclick = () => fin(pedir ? null : false);
    inp.onkeydown = e => { if (e.key === 'Enter') fin(inp.value); };
    document.onkeydown = e => { if (e.key === 'Escape') fin(pedir ? null : false); };
  });
}

async function pedirContrasena() {
  const v = await dialogo({
    titulo: 'Contraseña de la pantalla',
    mensaje: 'Escríbela una sola vez. Se guarda cifrada solo en esta PC y el programa la pondrá solo cada vez. Es la misma clave para la pantalla y el panel.',
    si: 'Guardar', pedir: true,
  });
  if (v == null || !v.trim()) return false;
  await lj.guardarClave(v);
  toast('Contraseña guardada');
  revisar();
  return true;
}

// ---------- Estado del servidor ----------
function indicador(id, nombre, ok) {
  const e = $(id);
  e.textContent = '● ' + nombre;
  e.className = 'ind' + (ok == null ? '' : ok ? ' ok' : ' mal');
}
function punto(c) { $('punto').className = 'punto ' + c; }
const gb = b => (b / 1073741824).toLocaleString('es', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
function duracion(s) {
  const d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
  return d > 0 ? `${d} d ${h} h` : h > 0 ? `${h} h ${m} min` : `${m} min`;
}
function vaciar(id, texto) {
  const c = $(id); c.replaceChildren(el('p', 'nota', texto));
}

let revisando = false;
async function revisar() {
  if (revisando) return;
  revisando = true;
  $('estado').textContent = 'Revisando servidor…'; punto('ambar');
  try { pintar(await lj.estado()); } finally { revisando = false; }
}

function pintar({ codPanel, datos: j, codPantalla }) {
  if (j) {
    indicador('indPantalla', 'Pantalla', j.pantalla);
    indicador('indConector', 'Conector', j.conector);
    indicador('indChrome', 'Chrome', j.chrome);
    const todo = j.pantalla && j.conector && j.chrome;
    punto(todo ? 'verde' : 'rojo');
    $('estado').textContent = todo ? 'Todo funcionando' : 'Hay un problema';
    $('detalle').textContent = todo ? 'Clic aquí para revisar de nuevo'
      : !j.chrome ? 'Chrome no responde. El vigilante lo reinicia solo; si sigue así, usa Reiniciar Chrome.'
      : !j.conector ? 'El conector no responde: Claude no puede usar tu Chrome. Prueba Reiniciar Chrome.'
      : 'La pantalla remota no responde. El vigilante la reinicia sola en unos minutos.';
    const s = j.sistema;
    if (s) {
      let t = '';
      if (s.ram) t += `RAM ${gb(s.ram.usada)} / ${gb(s.ram.total)} GB`;
      if (s.disco) t += `  ·  Disco ${gb(s.disco.usado)} / ${gb(s.disco.total)} GB`;
      t += `\n${s.nucleos} núcleos  ·  encendido hace ${duracion(s.encendidoSeg || 0)}`;
      $('recursos').textContent = t; $('recursos').hidden = false;
    }
    pintarPestanas(j.pestanas); pintarSesiones(j.sesiones); pintarEventos(j.eventos);
    return;
  }
  indicador('indConector', 'Conector', null);
  indicador('indChrome', 'Chrome', null);
  $('recursos').hidden = true;
  if (codPanel === 401) {
    punto('ambar'); $('estado').textContent = 'Clave incorrecta';
    $('detalle').textContent = 'Actualiza la contraseña en Ajustes para ver el panel completo';
    indicador('indPantalla', 'Pantalla', null);
  } else if (codPantalla === 200) {
    punto('ambar'); $('estado').textContent = 'Pantalla en línea';
    $('detalle').textContent = `No se pudo leer el resto del servidor (código ${codPanel})`;
    indicador('indPantalla', 'Pantalla', true);
  } else if (codPantalla === -1) {
    punto('ambar'); $('estado').textContent = 'Sin internet en la PC';
    $('detalle').textContent = 'Revisa tu conexión y haz clic para reintentar';
    indicador('indPantalla', 'Pantalla', null);
  } else {
    punto('rojo'); $('estado').textContent = 'Servidor sin respuesta';
    $('detalle').textContent = `Código ${codPantalla} · puede estar apagado o reiniciándose`;
    indicador('indPantalla', 'Pantalla', false);
  }
  const sin = codPanel === 401 ? 'Necesita la clave correcta' : 'Sin datos del servidor';
  $('tituloPestanas').textContent = 'Pestañas abiertas';
  vaciar('pestanas', sin); vaciar('sesiones', sin); vaciar('eventos', sin);
}

function pintarPestanas(a) {
  if (!a) { vaciar('pestanas', 'No se pudo leer Chrome'); return; }
  $('tituloPestanas').textContent = `Pestañas abiertas (${a.length})`;
  if (!a.length) { vaciar('pestanas', 'Ninguna pestaña abierta'); return; }
  const c = $('pestanas'); c.replaceChildren();
  a.forEach((t, i) => {
    if (i) c.append(el('hr'));
    let host = '';
    try { host = new URL(t.url).host; } catch (e) { }
    const f = el('div', 'tab');
    f.append(el('div', 't', t.titulo || '(sin título)'), el('div', 'h', host || t.url));
    f.title = t.url;
    f.onclick = () => abrirVisor(false);
    c.append(f);
  });
}

function pintarSesiones(a) {
  if (!a) { vaciar('sesiones', 'No se pudo leer Chrome'); return; }
  const c = $('sesiones'); c.replaceChildren();
  a.forEach((s, i) => {
    if (i) c.append(el('hr'));
    const f = el('div', 'ses' + (s.activa ? '' : ' cerrada'));
    const e = el('span', 'e', s.activa ? '● Activa' : '● Cerrada');
    e.style.color = s.activa ? 'var(--verde)' : 'var(--rojo)';
    f.append(el('span', 'n', s.nombre), e);
    if (!s.activa) { f.title = 'Entrar con Controlar pantalla'; f.onclick = () => abrirVisor(true); }
    c.append(f);
  });
  c.append(el('p', 'pie', 'Si una sesión está cerrada, haz clic en ella para entrar con Controlar pantalla.'));
}

function pintarEventos(a) {
  if (!a || !a.length) { vaciar('eventos', 'Sin novedades: todo estable.'); return; }
  const box = el('div', 'eventos');
  a.slice(0, 6).forEach(x => box.append(el('div', null, x)));
  $('eventos').replaceChildren(box);
}

// ---------- Acciones ----------
async function abrirVisor(controlar) {
  if (!(await lj.tieneClave()) && !(await pedirContrasena())) return;
  await lj.visor(controlar);
}

async function accion(ruta, titulo, mensaje, hecho) {
  if (!(await dialogo({ titulo, mensaje }))) return;
  toast(hecho);
  const r = await lj.accion(ruta);
  if (r.codigo === 200) { toast('Listo. En unos segundos vuelve a estar disponible.', true); setTimeout(revisar, 20000); }
  else if (r.codigo === 401) toast('Clave incorrecta: actualízala en Ajustes', true);
  else toast(`No se pudo (código ${r.codigo})`, true);
}

// ---------- Actualizaciones ----------
let updManual = false;
function pintarUpd(u) {
  if (!u) return;
  const b = $('banner');
  if (u.estado === 'descargando') {
    b.hidden = false;
    $('txtUpd').textContent = `Descargando la versión ${u.version}…` + (u.progreso != null ? ` ${u.progreso}%` : '');
    $('btnUpd').hidden = true;
  } else if (u.estado === 'lista') {
    b.hidden = false;
    $('txtUpd').textContent = `Actualización lista (versión ${u.version})`;
    $('btnUpd').hidden = false;
  } else if (updManual) {
    if (u.estado === 'al-dia') toast('Ya tienes la última versión');
    else if (u.estado === 'error') toast('No se pudo revisar ahora');
    else if (u.estado === 'dev') toast('Las actualizaciones funcionan en la versión instalada');
    if (u.estado !== 'buscando') updManual = false;
  }
}

// ---------- Inicio ----------
$('estadoCard').onclick = revisar;
$('btnVer').onclick = () => abrirVisor(false);
$('btnControlar').onclick = () => abrirVisor(true);
$('btnClaude').onclick = () => lj.claude();
$('btnReiniciar').onclick = () => accion('reiniciar-chrome', '¿Reiniciar Chrome?',
  'Chrome se cerrará unos segundos y volverá con tus pestañas. Si Claude está trabajando, se interrumpe.', 'Reiniciando Chrome…');
$('btnLimpiar').onclick = () => accion('limpiar', '¿Limpiar caché?',
  'Se borra la caché y las descargas de más de 14 días. Tus sesiones NO se cierran. Chrome se reinicia unos segundos.', 'Limpiando…');
$('btnClave').onclick = pedirContrasena;
$('btnBuscar').onclick = async () => { updManual = true; toast('Buscando actualización…'); pintarUpd(await lj.buscarActualizacion()); };
$('btnUpd').onclick = async () => {
  if (await dialogo({ titulo: 'Actualizar LitleJacob Cloud', mensaje: 'El programa se cerrará, se instalará la versión nueva y se volverá a abrir solo.', si: 'Actualizar', no: 'Después' }))
    lj.instalarActualizacion();
};
lj.alActualizar(pintarUpd);

(async () => {
  const a = await lj.ajustes();
  $('swAviso').checked = a.aviso; $('swInicio').checked = a.inicioWindows;
  $('version').textContent = 'Versión ' + a.version + ' para PC';
  $('swAviso').onchange = e => {
    lj.ajuste('aviso', e.target.checked);
    if (e.target.checked) toast('Te avisaré si el servidor deja de responder');
  };
  $('swInicio').onchange = e => lj.ajuste('inicioWindows', e.target.checked);
  pintarUpd(await lj.updEstado());
  if (!a.captura && !(await lj.tieneClave())) await pedirContrasena();
  revisar();
  setInterval(() => { if (!document.hidden) revisar(); }, 30000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) revisar(); });
})();
