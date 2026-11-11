'use strict';
const A = window.AppAPI;
const VIEWS = {};
let instances = {};
let currentView = null;
let appState = null;

function student() {
  if (!appState) return null;
  const children = appState.children || [];
  if (!children.length) return null;
  const active = children.find(c => String(c.id) === String(appState.activeStudentId));
  return active || children[0];
}

function registerView(id, def) { VIEWS[id] = def; }

function renderNav() {
  const nav = document.getElementById('nav');
  nav.innerHTML = '';
  Object.keys(VIEWS).forEach((id) => {
    const v = VIEWS[id];
    const li = document.createElement('li');
    li.className = 'nav-item';
    const a = document.createElement('a');
    a.className = 'nav-link' + (id === currentView ? ' active' : '');
    a.href = '#';
    a.innerHTML = '<i class="bi ' + v.icon + ' me-1"></i>' + v.title;
    a.onclick = (e) => { e.preventDefault(); go(id); };
    li.appendChild(a);
    nav.appendChild(li);
  });
}

function renderTopbar() {
  const wrap = document.getElementById('student-select-wrap');
  wrap.innerHTML = '';
  const child = student();
  if (child) {
    const children = appState.children || [];
    if (children.length > 1) {
      const sel = document.createElement('select');
      sel.className = 'form-select form-select-sm';
      sel.style.width = 'auto';
      children.forEach(c => {
        const o = document.createElement('option');
        o.value = c.id;
        o.textContent = c.shortName || c.name;
        if (String(c.id) === String(appState.activeStudentId)) o.selected = true;
        sel.appendChild(o);
      });
      sel.onchange = () => switchStudent(sel.value);
      wrap.appendChild(sel);
    }
    const info = document.createElement('span');
    info.className = 'small text-secondary ms-2 d-none d-md-inline';
    info.textContent = [child.className, child.schoolName].filter(Boolean).join(' · ');
    wrap.appendChild(info);
  }
  const ts = document.getElementById('token-status');
  const token = appState && appState.token ? appState.token : null;
  ts.textContent = token ? ('токен ' + (token.mask || '')) : 'нет токена';
  ts.className = 'small ' + (token && token.expired ? 'text-warning' : 'text-secondary');
}

async function switchStudent(studentId) {
  A.setActiveStudentId(studentId);
  if (appState) appState.activeStudentId = studentId;
  Object.keys(instances).forEach(k => delete instances[k]);
  renderTopbar();
  go(currentView || 'ranking');
  A.toast('Ученик переключён', 'success');
}

function go(viewId) {
  const def = VIEWS[viewId];
  if (!def) return;
  const prev = currentView;
  if (prev && instances[prev] && instances[prev].hide) instances[prev].hide();
  const host = document.getElementById('view-host');
  host.innerHTML = '';
  let inst = instances[viewId];
  if (!inst) {
    inst = def.create(context());
    instances[viewId] = inst;
  }
  currentView = viewId;
  host.appendChild(inst.el);
  renderNav();
  if (inst.show) {
    const r = inst.show();
    if (r && r.catch) r.catch(e => A.toast(e.message || String(e), 'error'));
  }
  const s = A.getSettings();
  A.setSettings({ lastView: viewId });
}

function context() {
  return {
    state: () => appState,
    student: student,
    go: go,
    reloadState: async () => { await loadState(); renderTopbar(); },
    onSettingsChanged: (patch) => { if (patch && patch.autoRefreshMinutes !== undefined) setupAutoRefresh(); }
  };
}

async function loadState() {
  const token = A.getToken();
  if (!token) { appState = null; return null; }
  try {
    const profile = await A.api('/api/profile');
    const children = (profile && profile.children) ? profile.children : [];
    let active = A.getActiveStudentId();
    if (!active && profile.defaultChild) active = profile.defaultChild.id;
    if (!children.find(c => String(c.id) === String(active)) && children.length) active = children[0].id;
    A.setActiveStudentId(active);
    A.setProfile(profile);
    appState = { authenticated: true, token: { mask: token.slice(0,8) + '…' }, profile, children, activeStudentId: active };
    return appState;
  } catch (e) {
    A.clearToken();
    appState = null;
    throw e;
  }
}

function setupAutoRefresh() {}

function showLogin() {
  document.getElementById('app').classList.add('d-none');
  document.getElementById('login-root').classList.remove('d-none');
  if (window.Views && window.Views.login && window.Views.login.show) window.Views.login.show({
    onSuccess: async (state) => {
      A.setToken(state.token);
      A.setActiveStudentId(state.activeStudentId);
      A.setProfile(state.profile);
      document.getElementById('login-root').classList.add('d-none');
      document.getElementById('app').classList.remove('d-none');
      await loadState();
      startShell();
    }
  });
}

async function startShell() {
  document.getElementById('app').classList.remove('d-none');
  document.getElementById('login-root').classList.add('d-none');
  renderTopbar();
  const s = A.getSettings();
  const initial = (s.lastView && VIEWS[s.lastView]) ? s.lastView : (s.defaultView && VIEWS[s.defaultView] ? s.defaultView : 'ranking');
  go(initial);
  document.getElementById('btn-refresh').onclick = () => { const inst = instances[currentView]; if (inst && inst.refresh) inst.refresh(); };
  document.getElementById('btn-logout').onclick = () => {
    if (!confirm('Выйти из МЭШ и удалить токен?')) return;
    A.clearToken(); A.setProfile(null); A.setActiveStudentId(null);
    appState = null; Object.keys(instances).forEach(k => delete instances[k]);
    document.getElementById('view-host').innerHTML = '';
    document.getElementById('nav').innerHTML = '';
    showLogin();
  };
  document.getElementById('btn-login').onclick = () => showLogin();
  document.getElementById('btn-theme').onclick = () => {
    const s = A.getSettings();
    const next = (document.documentElement.getAttribute('data-bs-theme') === 'dark') ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', next);
    A.setSettings({ theme: next });
  };
  applyTheme();
}

function applyTheme() {
  const t = A.getSettings().theme || 'dark';
  let dark = t === 'dark';
  if (t === 'auto') dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
}

async function boot() {
  window.App.date = A.todayIso();
  applyTheme();
  const token = A.getToken();
  if (token) {
    try {
      await loadState();
      if (appState && (appState.children || []).length) { startShell(); return; }
    } catch (e) { A.toast('Не удалось получить состояние: ' + e.message, 'error'); }
  }
  showLogin();
}

window.App = { registerView, go, context, loadState, startShell, showLogin, switchStudent, appState: () => appState, setAppState: (s) => { appState = s; }, student, VIEWS, instances, currentView: () => currentView };
Object.defineProperty(window.App, 'state', { get: () => appState, set: (v) => { appState = v; } });

document.addEventListener('DOMContentLoaded', boot);
window.Views = window.Views || {};