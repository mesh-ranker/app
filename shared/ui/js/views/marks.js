'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function fmt(v){ return (v===null||v===undefined)?'—':Number(v).toFixed(2).replace(/\.?0+$/,''); }
  function gradeColor(v){ if(v==null) return ''; if(v>=4.5) return 'text-success'; if(v>=3.5) return 'text-warning'; return 'text-danger'; }
  function create(ctx) {
    const state = { from: A.addDays(A.todayIso(), -30), to: A.todayIso(), marks: [], subject: '', onlyMarked: false, loading:false, error:null };
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function student(){ return ctx.student?ctx.student():null; }
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Оценки'; host.appendChild(title);
      const row = document.createElement('div'); row.className='d-flex flex-wrap gap-2 align-items-center mb-3';
      const fromI = document.createElement('input'); fromI.type='date'; fromI.className='form-control form-control-sm'; fromI.style.width='auto'; fromI.value=state.from; fromI.onchange=()=>{state.from=fromI.value;load();};
      const toI = document.createElement('input'); toI.type='date'; toI.className='form-control form-control-sm'; toI.style.width='auto'; toI.value=state.to; toI.onchange=()=>{state.to=toI.value;load();};
      row.append(fromI, toI);
      const subjSel = document.createElement('select'); subjSel.className='form-select form-select-sm'; subjSel.style.width='auto';
      const subjects = [...new Set(state.marks.map(m=>m.subjectName).filter(Boolean))];
      subjSel.innerHTML = '<option value="">Все предметы</option>' + subjects.map(s=>`<option value="${A.escapeHtml(s)}">${A.escapeHtml(s)}</option>`).join('');
      subjSel.value = state.subject;
      subjSel.onchange = () => { state.subject = subjSel.value; renderTable(); };
      row.appendChild(subjSel);
      const chk = document.createElement('div'); chk.className='form-check';
      chk.innerHTML = `<input class="form-check-input" type="checkbox" id="onlyMarked" ${state.onlyMarked?'checked':''}><label class="form-check-label small" for="onlyMarked">Только с оценкой</label>`;
      chk.querySelector('input').onchange = e => { state.onlyMarked = e.target.checked; renderTable(); };
      row.appendChild(chk);
      host.appendChild(row);

      const bySubj = {};
      state.marks.forEach(m => {
        if (m.value == null) return;
        const key = m.subjectName || '—';
        if (!bySubj[key]) bySubj[key] = [];
        bySubj[key].push(m.value);
      });
      const summaryKeys = Object.keys(bySubj);
      if (summaryKeys.length) {
        const sTitle = document.createElement('h6'); sTitle.textContent='Итоги по предметам'; host.appendChild(sTitle);
        const stbl = document.createElement('table'); stbl.className='table table-sm';
        stbl.innerHTML = '<thead><tr><th>Предмет</th><th>Оценок</th><th>Средний</th></tr></thead>';
        const stbody = document.createElement('tbody');
        summaryKeys.forEach(k => {
          const arr = bySubj[k];
          const avg = arr.reduce((a,b)=>a+b,0)/arr.length;
          const tr = document.createElement('tr');
          tr.innerHTML = `<td>${A.escapeHtml(k)}</td><td>${arr.length}</td><td><span class="${gradeColor(avg)}">${fmt(avg)}</span></td>`;
          stbody.appendChild(tr);
        });
        stbl.appendChild(stbody);
        host.appendChild(stbl);
      }

      const tblWrap = document.createElement('div'); tblWrap.className='table-responsive';
      const tbl = document.createElement('table'); tbl.className='table table-sm table-hover'; tbl.id='marks-table';
      tbl.innerHTML = '<thead><tr><th>Дата</th><th>Предмет</th><th>Тип</th><th>Оценка</th><th>Комментарий</th></tr></thead><tbody></tbody>';
      tblWrap.appendChild(tbl);
      host.appendChild(tblWrap);
      renderTable();
    }

    function renderTable(){
      const tbody = document.querySelector('#marks-table tbody');
      if (!tbody) return;
      tbody.innerHTML = '';
      let rows = state.marks.slice();
      if (state.subject) rows = rows.filter(m => m.subjectName === state.subject);
      if (state.onlyMarked) rows = rows.filter(m => m.value != null);
      rows.forEach(m => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${m.date || '—'}</td><td>${A.escapeHtml(m.subjectName || '—')}</td><td>${A.escapeHtml(m.controlForm || '')}</td><td><span class="${gradeColor(Number(m.value))}">${m.value == null ? '—' : m.value}</span></td><td class="small text-secondary">${A.escapeHtml(m.comment || '')}</td>`;
        tbody.appendChild(tr);
      });
    }

    function load(force){
      const child = student();
      if (!child){ state.error='Не выбран ученик'; render(); return Promise.resolve(); }
      state.loading=true; state.error=null; render();
      return A.api('/api/marks?studentId='+encodeURIComponent(child.id)+'&from='+encodeURIComponent(state.from)+'&to='+encodeURIComponent(state.to))
        .then(res => { state.marks = res.marks || []; })
        .catch(e => { state.error = e.message; A.toast('Оценки не загрузились','error'); })
        .then(() => { state.loading=false; render(); });
    }
    return { el: rootEl, show: () => load(), hide:()=>{}, refresh:()=>load(true) };
  }
  window.App.registerView('marks', { title:'Оценки', icon:'bi-journal-check', hint:'Журнал оценок за период', create });
})();