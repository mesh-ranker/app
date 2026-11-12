'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function create(ctx) {
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Настройки'; host.appendChild(title);
      const s = A.getSettings();
      const card = document.createElement('div'); card.className='card mb-3';
      const body = document.createElement('div'); body.className='card-body';

      const themeRow = document.createElement('div'); themeRow.className='mb-3';
      themeRow.innerHTML = '<label class="form-label small">Тема</label>';
      const themeSel = document.createElement('select'); themeSel.className='form-select form-select-sm'; themeSel.style.width='auto';
      [
        { value: 'auto', label: 'Автоматически' },
        { value: 'dark', label: 'Тёмная' },
        { value: 'light', label: 'Светлая' }
      ].forEach(theme => {
        const o = document.createElement('option');
        o.value = theme.value;
        o.textContent = theme.label;
        if (s.theme === theme.value) o.selected = true;
        themeSel.appendChild(o);
      });
      themeSel.onchange = () => { A.setSettings({theme:themeSel.value}); applyTheme(themeSel.value); };
      themeRow.appendChild(themeSel);
      body.appendChild(themeRow);

      function checkbox(label, key) {
        const wrap = document.createElement('div'); wrap.className='form-check mb-2';
        const inp = document.createElement('input'); inp.type='checkbox'; inp.className='form-check-input'; inp.id='set-'+key; inp.checked = !!s[key];
        const lab = document.createElement('label'); lab.className='form-check-label'; lab.htmlFor='set-'+key; lab.textContent=label;
        inp.onchange = () => { A.setSettings({[key]: inp.checked}); };
        wrap.append(inp, lab);
        return wrap;
      }
      body.appendChild(checkbox('Компактные таблицы','denseTables'));
      body.appendChild(checkbox('Показывать гистограмму','showHistogram'));
      body.appendChild(checkbox('Только мой класс','onlyMyClass'));

      const psRow = document.createElement('div'); psRow.className='mb-3';
      psRow.innerHTML = '<label class="form-label small">Строк на странице</label>';
      const psInp = document.createElement('input'); psInp.type='number'; psInp.className='form-control form-control-sm'; psInp.style.width='100px'; psInp.value=s.pageSize||25;
      psInp.onchange = () => A.setSettings({pageSize: Number(psInp.value)||25});
      psRow.appendChild(psInp);
      body.appendChild(psRow);

      const arRow = document.createElement('div'); arRow.className='mb-3';
      arRow.innerHTML = '<label class="form-label small">Автообновление (мин, 0 — выкл)</label>';
      const arInp = document.createElement('input'); arInp.type='number'; arInp.className='form-control form-control-sm'; arInp.style.width='100px'; arInp.value=s.autoRefreshMinutes||0;
      arInp.onchange = () => A.setSettings({autoRefreshMinutes: Number(arInp.value)||0});
      arRow.appendChild(arInp);
      body.appendChild(arRow);

      const apiRow = document.createElement('div'); apiRow.className='mb-3';
      apiRow.innerHTML = '<label class="form-label small">Адрес сервера для публикации интерфейса. Оставьте пустым для прямого подключения.</label>';
      const apiInp = document.createElement('input'); apiInp.type='text'; apiInp.className='form-control form-control-sm'; apiInp.placeholder='https://сервер:8787'; apiInp.value=A.getApiBase();
      apiInp.onchange = () => A.setApiBase(apiInp.value);
      apiRow.appendChild(apiInp);
      body.appendChild(apiRow);

      const resetBtn = document.createElement('button'); resetBtn.className='btn btn-sm btn-outline-danger'; resetBtn.textContent='Сбросить настройки';
      resetBtn.onclick = () => { localStorage.removeItem('mesh.settings.v1'); localStorage.removeItem('mesh.apiBase.v1'); location.reload(); };
      body.appendChild(resetBtn);

      const resetNamesBtn = document.createElement('button'); resetNamesBtn.className='btn btn-sm btn-outline-secondary ms-2'; resetNamesBtn.textContent='Сбросить кэш имён';
      resetNamesBtn.onclick = () => { A.resetNamesCache(); A.toast('Кэш имён сброшен', 'success'); };
      body.appendChild(resetNamesBtn);

      card.appendChild(body); host.appendChild(card);
    }
    function applyTheme(t){
      let dark = t === 'dark';
      if (t === 'auto') dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
    }
    return { el: rootEl, show: () => { render(); return Promise.resolve(); }, hide:()=>{}, refresh:()=>{ render(); return Promise.resolve(); } };
  }
  window.App.registerView('settings', { title:'Настройки', icon:'bi-gear', hint:'Внешний вид и параметры', create });
})();