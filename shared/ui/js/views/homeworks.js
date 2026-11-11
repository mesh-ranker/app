'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function create(ctx) {
    const state = { from: A.addDays(A.todayIso(), -7), to: A.todayIso(), homeworks: [], loading:false, error:null };
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function student(){ return ctx.student?ctx.student():null; }
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Домашние задания'; host.appendChild(title);
      const row = document.createElement('div'); row.className='d-flex gap-2 mb-3';
      const fromI = document.createElement('input'); fromI.type='date'; fromI.className='form-control form-control-sm'; fromI.style.width='auto'; fromI.value=state.from; fromI.onchange=()=>{state.from=fromI.value;load();};
      const toI = document.createElement('input'); toI.type='date'; toI.className='form-control form-control-sm'; toI.style.width='auto'; toI.value=state.to; toI.onchange=()=>{state.to=toI.value;load();};
      row.append(fromI, toI);
      host.appendChild(row);
      if (state.error){ host.innerHTML += `<div class="empty-state">${state.error}</div>`; return; }
      if (state.loading){ host.innerHTML += '<div class="text-secondary">Загрузка...</div>'; return; }
      const tbl = document.createElement('table'); tbl.className='table table-sm table-hover';
      tbl.innerHTML = '<thead><tr><th>Срок</th><th>Предмет</th><th>Задание</th><th>Статус</th></tr></thead>';
      const tbody = document.createElement('tbody');
      state.homeworks.forEach(h => {
        const tr = document.createElement('tr');
        tr.style.cursor='pointer';
        tr.innerHTML = `<td>${h.dueDate||h.assignedOn||'—'}</td><td>${A.escapeHtml(h.subjectName||'—')}</td><td>${A.escapeHtml((h.description||'').slice(0,80))}</td><td>${h.isDone?'выполнено':'не выполнено'}</td>`;
        tr.onclick = () => showHw(h);
        tbody.appendChild(tr);
      });
      tbl.appendChild(tbody);
      host.appendChild(tbl);
      if (!state.homeworks.length) host.innerHTML += '<div class="empty-state">Нет заданий</div>';
    }
    function showHw(h){
      const wrap = document.createElement('div');
      wrap.innerHTML = `<div class="modal fade" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="modal-header"><h5 class="modal-title">${A.escapeHtml(h.subjectName||'Задание')}</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><p>${A.escapeHtml(h.description||'')}</p><div class="small text-secondary">Срок: ${h.dueDate||h.assignedOn||'—'}</div></div></div></div></div>`;
      document.body.appendChild(wrap);
      const m = new bootstrap.Modal(wrap.querySelector('.modal'));
      wrap.querySelector('.modal').addEventListener('hidden.bs.modal', ()=>wrap.remove());
      m.show();
    }
    function load(force){
      const child = student();
      if (!child){ state.error='Не выбран ученик'; render(); return Promise.resolve(); }
      state.loading=true; state.error=null; render();
      return A.api('/api/homeworks?studentId='+encodeURIComponent(child.id)+'&from='+encodeURIComponent(state.from)+'&to='+encodeURIComponent(state.to))
        .then(res => { state.homeworks = res.homeworks || []; })
        .catch(e => { state.error = e.message; A.toast('ДЗ не загрузились','error'); })
        .then(() => { state.loading=false; render(); });
    }
    return { el: rootEl, show: () => load(), hide:()=>{}, refresh:()=>load(true) };
  }
  window.App.registerView('homeworks', { title:'Домашние задания', icon:'bi-journal-text', hint:'Задания за период', create });
})();