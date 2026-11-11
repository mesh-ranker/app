'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function fmt(v){ return (v===null||v===undefined)?'—':Number(v).toFixed(2).replace(/\.?0+$/,''); }
  function gradeColor(v){ if(v==null) return ''; if(v>=4.5) return 'text-success'; if(v>=3.5) return 'text-warning'; return 'text-danger'; }
  function create(ctx) {
    const state = { date: A.todayIso(), subjects: [], loading:false, error:null };
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function student(){ return ctx.student?ctx.student():null; }
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Предметы'; host.appendChild(title);
      if (state.error){ host.innerHTML += `<div class="empty-state">${state.error}</div>`; return; }
      if (state.loading){ host.innerHTML += '<div class="text-secondary">Загрузка...</div>'; return; }
      const tbl = document.createElement('table'); tbl.className='table table-sm table-hover';
      tbl.innerHTML = '<thead><tr><th>Предмет</th><th>Средний балл</th><th>Место</th><th>Экзамен</th></tr></thead>';
      const tbody = document.createElement('tbody');
      state.subjects.forEach(s => {
        const tr = document.createElement('tr');
        tr.style.cursor='pointer';
        tr.innerHTML = `<td>${A.escapeHtml(s.name)}</td><td><span class="${gradeColor(s.average)}">${fmt(s.average)}</span></td><td>${s.place==null?'—':s.place}</td><td>${s.isExam?'да':'—'}</td>`;
        tr.onclick = () => { App.go('ranking'); const inst=App.instances.ranking; if(inst && inst.selectSubject) inst.selectSubject(s.id); };
        tbody.appendChild(tr);
      });
      tbl.appendChild(tbody);
      host.appendChild(tbl);
    }
    function load(force){
      const child = student();
      if (!child){ state.error='Не выбран ученик'; render(); return Promise.resolve(); }
      state.loading=true; state.error=null; render();
      return A.api('/api/subjects?studentId='+encodeURIComponent(child.id)+'&date='+encodeURIComponent(state.date)+'&personId='+encodeURIComponent(child.personId))
        .then(res => { state.subjects = res.subjects || []; })
        .catch(e => { state.error = e.message; A.toast('Предметы не загрузились','error'); })
        .then(() => { state.loading=false; render(); });
    }
    return { el: rootEl, show: () => load(), hide:()=>{}, refresh:()=>load(true) };
  }
  window.App.registerView('subjects', { title:'Предметы', icon:'bi-book', hint:'Средние баллы и места по предметам', create });
})();