'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;
  function create(ctx) {
    const state = { date: A.todayIso(), mode: 'day', lessons: [], week: [], loading:false, error:null };
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);
    function student(){ return ctx.student?ctx.student():null; }
    function fmtDate(iso){ try { return new Date(iso).toLocaleDateString('ru-RU',{weekday:'short',day:'numeric',month:'short'}); } catch(e){ return iso; } }
    function timeOf(l){ return (l.begin||'—') + (l.end ? '–'+l.end : ''); }
    function render(){
      host.innerHTML = '';
      const title = document.createElement('h4'); title.className='view-title'; title.textContent='Расписание'; host.appendChild(title);
      const row = document.createElement('div'); row.className='d-flex flex-wrap gap-2 align-items-center mb-3';
      const dateI = document.createElement('input'); dateI.type='date'; dateI.className='form-control form-control-sm'; dateI.style.width='auto'; dateI.value=state.date; dateI.onchange=()=>{state.date=dateI.value;load();};
      row.appendChild(dateI);
      const dayBtn = document.createElement('button'); dayBtn.className='btn btn-sm '+(state.mode==='day'?'btn-primary':'btn-outline-secondary'); dayBtn.textContent='День'; dayBtn.onclick=()=>{state.mode='day';render();load();};
      const weekBtn = document.createElement('button'); weekBtn.className='btn btn-sm '+(state.mode==='week'?'btn-primary':'btn-outline-secondary'); weekBtn.textContent='Неделя'; weekBtn.onclick=()=>{state.mode='week';render();load();};
      row.append(dayBtn, weekBtn);
      const prev = document.createElement('button'); prev.className='btn btn-sm btn-outline-secondary'; prev.textContent='‹'; prev.onclick=()=>{ state.date=A.addDays(state.date,-1); load(); };
      const next = document.createElement('button'); next.className='btn btn-sm btn-outline-secondary'; next.textContent='›'; next.onclick=()=>{ state.date=A.addDays(state.date,1); load(); };
      row.append(prev,next);
      host.appendChild(row);
      if (state.error){ host.innerHTML += `<div class="empty-state">${state.error}</div>`; return; }
      if (state.loading){ host.innerHTML += '<div class="text-secondary">Загрузка...</div>'; return; }
      if (state.mode === 'day') {
        const tbl = document.createElement('table'); tbl.className='table table-sm';
        tbl.innerHTML = '<thead><tr><th>Время</th><th>Предмет</th><th>Кабинет</th><th>Учитель</th></tr></thead>';
        const tbody = document.createElement('tbody');
        (state.lessons || []).forEach(l => {
          const tr = document.createElement('tr');
          tr.innerHTML = `<td>${timeOf(l)}</td><td>${A.escapeHtml(l.subject||'—')}</td><td>${A.escapeHtml(l.room||'')}</td><td>${A.escapeHtml(l.teacher||'')}</td>`;
          tbody.appendChild(tr);
        });
        tbl.appendChild(tbody);
        host.appendChild(tbl);
        if (!(state.lessons||[]).length) host.innerHTML += '<div class="empty-state">Нет уроков</div>';
      } else {
        const grid = document.createElement('div'); grid.className='table-responsive';
        const tbl = document.createElement('table'); tbl.className='table table-sm';
        const thead = document.createElement('thead');
        const hr = document.createElement('tr');
        hr.innerHTML = '<th>Время</th>';
        (state.week || []).forEach(d => { const th = document.createElement('th'); th.textContent = fmtDate(d.date); hr.appendChild(th); });
        thead.appendChild(hr); tbl.appendChild(thead);
        const tbody = document.createElement('tbody');
        const times = [];
        (state.week||[]).forEach(d => (d.lessons||[]).forEach(l => { const t = l.begin||l.time; if (t && !times.includes(t)) times.push(t); }));
        times.sort();
        times.forEach(t => {
          const tr = document.createElement('tr');
          const td0 = document.createElement('td'); td0.textContent = t || '—'; tr.appendChild(td0);
          (state.week||[]).forEach(d => {
            const td = document.createElement('td');
            const lesson = (d.lessons||[]).find(l => (l.begin||l.time) === t);
            td.textContent = lesson ? (lesson.subject||'') : '';
            tr.appendChild(td);
          });
          tbody.appendChild(tr);
        });
        tbl.appendChild(tbody);
        grid.appendChild(tbl);
        host.appendChild(grid);
      }
    }
    function loadDay(){
      const child = student();
      if (!child){ state.error='Не выбран ученик'; render(); return Promise.resolve(); }
      return A.api('/api/schedule?studentId='+encodeURIComponent(child.id)+'&date='+encodeURIComponent(state.date))
        .then(res => { state.lessons = res.lessons || []; })
        .catch(e => { state.error = e.message; });
    }
    function loadWeek(){
      const child = student();
      if (!child){ state.error='Не выбран ученик'; render(); return Promise.resolve(); }
      const monday = getMonday(state.date);
      const days = [0,1,2,3,4].map(i => A.addDays(monday, i));
      return Promise.all(days.map(d => A.api('/api/schedule?studentId='+encodeURIComponent(child.id)+'&date='+encodeURIComponent(d)).then(res => ({ date: d, lessons: res.lessons || [] })).catch(() => ({ date: d, lessons: [] }))))
        .then(res => { state.week = res; });
    }
    function load(force){
      state.loading=true; state.error=null; render();
      const p = state.mode === 'week' ? loadWeek() : loadDay();
      return p.then(() => { state.loading=false; render(); }).catch(e => { state.error = e.message; state.loading=false; render(); });
    }
    function getMonday(iso){
      const d = new Date(iso);
      const day = d.getDay() || 7;
      d.setDate(d.getDate() - (day - 1));
      return d.toISOString().slice(0,10);
    }
    return { el: rootEl, show: () => load(), hide:()=>{}, refresh:()=>load(true) };
  }
  window.App.registerView('schedule', { title:'Расписание', icon:'bi-calendar3', hint:'День и неделя', create });
})();