'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('lj', {
  estado: () => ipcRenderer.invoke('estado'),
  accion: ruta => ipcRenderer.invoke('accion', ruta),
  tieneClave: () => ipcRenderer.invoke('tiene-clave'),
  guardarClave: c => ipcRenderer.invoke('guardar-clave', c),
  visor: controlar => ipcRenderer.invoke('visor', controlar),
  claude: () => ipcRenderer.invoke('claude'),
  ajustes: () => ipcRenderer.invoke('ajustes'),
  ajuste: (k, v) => ipcRenderer.invoke('ajuste', k, v),
  buscarActualizacion: () => ipcRenderer.invoke('buscar-actualizacion'),
  updEstado: () => ipcRenderer.invoke('upd-estado'),
  instalarActualizacion: () => ipcRenderer.invoke('instalar-actualizacion'),
  alActualizar: f => ipcRenderer.on('upd', (e, d) => f(d)),
});
