'use strict';
const TOKEN_KEY = 'mesh.token.v1';
const SETTINGS_KEY = 'mesh.settings.v1';
const ACTIVE_STUDENT_KEY = 'mesh.activeStudent.v1';
const PROFILE_KEY = 'mesh.profile.v1';
const API_BASE_KEY = 'mesh.apiBase.v1';

function getToken() { try { return localStorage.getItem(TOKEN_KEY); } catch (e) { return null; } }
function setToken(t) { try { if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY); } catch (e) {} }
function clearToken() { setToken(null); }

function getSettings() {
  try { return Object.assign({ theme: 'dark', pageSize: 25, defaultView: 'ranking', denseTables: false, autoRefreshMinutes: 0, apiBase: '' }, JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}')); }
  catch (e) { return { theme: 'dark', pageSize: 25, defaultView: 'ranking', denseTables: false, autoRefreshMinutes: 0, apiBase: '' }; }
}
function setSettings(patch) {
  const cur = getSettings();
  const next = Object.assign({}, cur, patch || {});
  try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(next)); } catch (e) {}
  return next;
}

function getApiBase() {
  try { return (localStorage.getItem(API_BASE_KEY) || getSettings().apiBase || '').replace(/\/+$/, ''); } catch (e) { return ''; }
}
function setApiBase(v) {
  try {
    const val = (v || '').trim().replace(/\/+$/, '');
    if (val) localStorage.setItem(API_BASE_KEY, val); else localStorage.removeItem(API_BASE_KEY);
  } catch (e) {}
  return getApiBase();
}

function getActiveStudentId() { try { return localStorage.getItem(ACTIVE_STUDENT_KEY); } catch (e) { return null; } }
function setActiveStudentId(id) { try { if (id != null) localStorage.setItem(ACTIVE_STUDENT_KEY, String(id)); else localStorage.removeItem(ACTIVE_STUDENT_KEY); } catch (e) {} }

function getProfile() { try { return JSON.parse(localStorage.getItem(PROFILE_KEY) || 'null'); } catch (e) { return null; } }
function setProfile(p) { try { if (p) localStorage.setItem(PROFILE_KEY, JSON.stringify(p)); else localStorage.removeItem(PROFILE_KEY); } catch (e) {} }
function resetNamesCache() {
  try { localStorage.removeItem(NAMES_CACHE_KEY); } catch (e) {}
  if (_meshClient && _meshClient.nameCache) _meshClient.nameCache = {};
}

let _meshClient = null;
function getMeshClient(token, profileId) {
  if (typeof MeshCore === 'undefined') throw new Error('Ядро МЭШ не загружено');
  if (!_meshClient) {
    _meshClient = new MeshCore.MeshClient({
      token: token || '',
      profileId: profileId || null,
      baseUrl: MeshCore.HOST,
      timeout: 20000,
      onToken: (t) => { setToken(t); }
    });
  } else {
    _meshClient.setToken(token || _meshClient.token);
    _meshClient.setProfileId(profileId || _meshClient.profileId);
  }
  const origHeaders = _meshClient.headers.bind(_meshClient);
  _meshClient.headers = function (extra) {
    const h = origHeaders(extra);
    if (h && h['X-Mes-RoleId']) delete h['X-Mes-RoleId'];
    return h;
  };
  return _meshClient;
}

async function localApi(path, options) {
  options = options || {};
  if (typeof MeshCore === 'undefined') throw new Error('Ядро МЭШ не загружено');
  const url = new URL(path, location.origin);
  const pathname = url.pathname;
  const q = url.searchParams;
  let body = {};
  if (options.body) {
    try { body = JSON.parse(options.body); } catch (e) { body = { raw: options.body }; }
  }
  const utilMod = (typeof MeshLib !== 'undefined' && MeshLib.util) ? MeshLib.util : null;
  const today = () => (utilMod ? utilMod.todayIso() : new Date().toISOString().slice(0,10));

  if (pathname === '/api/health') return { server: 'mesh-ranker-web', time: Date.now() };

  if (pathname === '/api/sign-in' && (options.method || 'GET').toUpperCase() === 'POST') {
    const raw = body.token || body.raw || '';
    const client = getMeshClient('', null);
    const result = await client.signIn(raw);
    setToken(result.token);
    setProfile(result.profile);
    setActiveStudentId(result.child ? result.child.id : null);
    return { profile: result.profile, children: result.profile.children, activeStudentId: result.child ? result.child.id : null, token: MeshCore.describeToken(result.token) };
  }

  const token = getToken();
  if (!token) throw new Error('Требуется вход в МЭШ');
  const client = getMeshClient(token, getActiveStudentId());

  const studentId = q.get('studentId') || null;

  if (pathname === '/api/sign-out') { _meshClient = null; clearToken(); setProfile(null); setActiveStudentId(null); return { ok: true }; }
  if (pathname === '/api/profile') { const p = await client.fetchProfile(); setProfile(p); return p; }
  if (pathname === '/api/subjects') {
    const date = q.get('date') || today();
    const personId = q.get('personId') || studentId;
    const subjects = await client.getSubjects(personId, date);
    return { date: date, personId: personId, subjects: subjects };
  }
  if (pathname === '/api/subject-marks') {
    const sid = q.get('studentId') || studentId;
    return { marks: MeshCore.normalizeSubjectMarks(await client.getSubjectMarks(sid)) };
  }
  if (pathname === '/api/rank') {
    const board = await client.getRankBoard({
      personId: q.get('personId') || studentId,
      date: q.get('date') || today(),
      subjectId: q.get('subjectId') || null,
      subjectName: q.get('subjectName') || null
    });
    return board;
  }
  if (pathname === '/api/marks') {
    const to = q.get('to') || today();
    const from = q.get('from') || (utilMod ? utilMod.addDays(to, -30) : to);
    const sid = q.get('studentId') || studentId;
    const marks = await client.getMarks(sid, from, to);
    return { from: from, to: to, marks: marks, summary: MeshCore.summarizeMarks(marks) };
  }
  if (pathname === '/api/homeworks') {
    const to = q.get('to') || today();
    const from = q.get('from') || (utilMod ? utilMod.addDays(to, -7) : to);
    const sid = q.get('studentId') || studentId;
    const homeworks = await client.getHomeworks(sid, from, to);
    return { from: from, to: to, homeworks: homeworks };
  }
  if (pathname === '/api/schedule') {
    const date = q.get('date') || today();
    const sid = q.get('studentId') || studentId;
    const lessons = await client.getSchedule(sid, date);
    return { date: date, lessons: lessons };
  }
  if (pathname === '/api/names') {
    const ids = (q.get('personIds') || '').split(',').map(s => s.trim()).filter(Boolean);
    return { names: await client.getNames(ids) };
  }
  if (pathname === '/api/refresh-token' && (options.method || 'GET').toUpperCase() === 'POST') {
    const t = await client.refreshToken();
    if (t) setToken(t);
    return {};
  }
  if (pathname === '/api/student' && (options.method || 'GET').toUpperCase() === 'POST') {
    setActiveStudentId(body.studentId);
    if (_meshClient) _meshClient.setProfileId(body.studentId);
    return { studentId: body.studentId };
  }
  throw new Error('Неизвестный метод: ' + pathname);
}

async function api(path, options) {
  options = options || {};
  const base = getApiBase();
  if (base) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const t = getToken();
    if (t) headers['X-Mesh-Token'] = t;
    const sid = getActiveStudentId();
    if (sid) headers['X-Mesh-Profile-Id'] = sid;
    const url = base + (path.startsWith('/') ? path : '/' + path);
    const res = await fetch(url, Object.assign({}, options, { headers }));
    let json;
    try { json = await res.json(); } catch (e) { throw new Error('Сервер вернул данные в неожиданном формате'); }
    if (!json.ok) throw new Error((json.error && json.error.message) ? json.error.message : 'Ошибка');
    if (json.token) setToken(json.token);
    return json.data;
  }
  return await localApi(path, options);
}

function toast(message, type) {
  const host = document.getElementById('toasts');
  if (!host) return;
  const el = document.createElement('div');
  el.className = 'toast show align-items-center text-bg-' + (type === 'error' ? 'danger' : type === 'success' ? 'success' : 'secondary') + ' border-0 mb-2';
  el.setAttribute('role', 'alert');
  el.innerHTML = '<div class="d-flex"><div class="toast-body">' + escapeHtml(message) + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
  host.appendChild(el);
  setTimeout(() => { el.classList.remove('show'); el.remove(); }, 4000);
}

function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

function todayIso() { return new Date().toISOString().slice(0,10); }
function addDays(iso, n) { const d = new Date(iso); d.setDate(d.getDate()+n); return d.toISOString().slice(0,10); }

// Персистентный кэш имён (как users_cache.json в goproj), чтобы не делать
// по 20 запросов к /api/gamification/v1/profiles при каждом открытии рейтинга.
const NAMES_CACHE_KEY = 'mesh.names.v1';
function loadNamesCache() { try { return JSON.parse(localStorage.getItem(NAMES_CACHE_KEY) || '{}'); } catch (e) { return {}; } }
function saveNamesCache(obj) { try { localStorage.setItem(NAMES_CACHE_KEY, JSON.stringify(obj)); } catch (e) {} }

if (window.MeshCore && window.MeshCore.MeshClient && window.MeshCore.MeshClient.prototype) {
  window.MeshCore.MeshClient.prototype.getName = async function (personId) {
    const key = String(personId || '');
    if (!key) return 'Неизвестный ученик';
    const cache = loadNamesCache();
    if (cache[key]) {
      if (this.nameCache) this.nameCache[key] = cache[key];
      return cache[key];
    }
    if (this.nameCache && this.nameCache[key]) delete this.nameCache[key];
    try {
      const data = await this.request(MeshCore.PATHS.profileById, { params: { personId: key } });
      const first = (data && (data.firstName || data.first_name || data.first || '')) || '';
      const last = (data && (data.lastName || data.last_name || data.last || '')) || '';
      let name = (first + ' ' + last).trim();
      if (!name) name = (data && (data.name || data.fio || '')) || '';
      if (!name) name = 'Ученик ' + key.slice(0, 8);
      if (name.indexOf('Ученик ') !== 0) {
        cache[key] = name;
        saveNamesCache(cache);
        if (this.nameCache) this.nameCache[key] = name;
      }
      return name;
    } catch (e) {
      console.error('getName failed for', key, e);
      window.__meshNameError = (e && e.message) ? e.message : String(e);
      const fallback = 'Ученик ' + key.slice(0, 8);
      return fallback;
    }
  };
}

window.AppAPI = { getToken, setToken, clearToken, getSettings, setSettings, getApiBase, setApiBase, getActiveStudentId, setActiveStudentId, getProfile, setProfile, resetNamesCache, api, toast, escapeHtml, todayIso, addDays };