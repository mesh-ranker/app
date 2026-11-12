'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function create(ctx) {
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function student(){ return ctx.student?ctx.student():null; }
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Профиль'; host.appendChild(title);
      const profile = A.getProfile();
      const child = student();
      if (profile && (profile.children||[]).length > 1) {
        const card = document.createElement('div'); card.className='card mb-3';
        const body = document.createElement('div'); body.className='card-body';
        body.innerHTML = '<h6>Ученик</h6>';
        const sel = document.createElement('select'); sel.className='form-select form-select-sm'; sel.style.width='auto';
        (profile.children||[]).forEach(c => {
          const o = document.createElement('option'); o.value=c.id; o.textContent=c.name; if (String(c.id)===String(App.state?App.state.activeStudentId:null)) o.selected=true; sel.appendChild(o);
        });
        sel.onchange = () => { if (App.switchStudent) App.switchStudent(sel.value); else { A.setActiveStudentId(sel.value); location.reload(); } };
        body.appendChild(sel);
        card.appendChild(body); host.appendChild(card);
      }
      const card2 = document.createElement('div'); card2.className='card mb-3';
      const body2 = document.createElement('div'); body2.className='card-body';
      body2.innerHTML = '<h6>Токен МЭШ</h6>';
      const t = A.getToken();
      body2.innerHTML += `<div class="small text-secondary mb-2">Ключ доступа хранится локально в данных браузера. Маска: ${A.escapeHtml(t ? t.slice(0,8)+'…' : '—')}</div>`;
      const btnRefresh = document.createElement('button'); btnRefresh.className='btn btn-sm btn-outline-secondary me-2'; btnRefresh.textContent='Обновить токен'; btnRefresh.onclick = async () => {
        try { await A.api('/api/refresh-token', { method:'POST' }); A.toast('Токен обновлён','success'); }
        catch(e){ A.toast(e.message,'error'); }
      };
      const btnExport = document.createElement('button'); btnExport.className='btn btn-sm btn-outline-secondary me-2'; btnExport.textContent='Скачать данные профиля';
      btnExport.onclick = () => {
        const blob = new Blob([JSON.stringify(profile||{},null,2)], {type:'application/json'});
        const url = URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download='профиль.json'; a.click(); URL.revokeObjectURL(url);
      };
      const btnLogout = document.createElement('button'); btnLogout.className='btn btn-sm btn-outline-danger'; btnLogout.textContent='Выйти';
      btnLogout.onclick = () => {
        if (!confirm('Выйти из МЭШ?')) return;
        A.clearToken(); A.setProfile(null); A.setActiveStudentId(null);
        App.state = null; location.reload();
      };
      body2.append(btnRefresh, btnExport, btnLogout);
      card2.appendChild(body2); host.appendChild(card2);

      if (child) {
        const card3 = document.createElement('div'); card3.className='card mb-3';
        const body3 = document.createElement('div'); body3.className='card-body';
        body3.innerHTML = `<h6>Ученик</h6><div>${A.escapeHtml(child.name||'')}</div><div class="small text-secondary">${A.escapeHtml(child.className||'')} · ${A.escapeHtml(child.schoolName||'')}</div>`;
        card3.appendChild(body3); host.appendChild(card3);
      }
    }
    return { el: rootEl, show: () => { render(); return Promise.resolve(); }, hide:()=>{}, refresh:()=>{ render(); return Promise.resolve(); } };
  }
  window.App.registerView('profile', { title:'Профиль', icon:'bi-person', hint:'Профиль и токен', create });
})();