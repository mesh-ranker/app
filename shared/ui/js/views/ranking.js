'use strict';
(function () {
  const A = window.AppAPI;
  const App = window.App;

  function subjectKey(id) { return id ? String(id) : 'all'; }
  function fmt(v) { return (v === null || v === undefined || !isFinite(Number(v))) ? '—' : Number(v).toFixed(2).replace(/\.?0+$/, '').replace('.', ','); }
  function dateRu(iso) { try { return new Date(iso).toLocaleDateString('ru-RU'); } catch (e) { return iso; } }
  function gradeColor(v) { if (v == null) return ''; if (v >= 4.5) return 'text-success'; if (v >= 3.5) return 'text-warning'; return 'text-danger'; }
  function schoolYearStart(iso) { const d = new Date(iso); const y = d.getMonth() >= 8 ? d.getFullYear() : d.getFullYear() - 1; return y + '-09-01'; }

  function create(ctx) {
    const state = {
      date: (App && App.date) ? App.date : A.todayIso(),
      subjects: [],
      selected: ['all'],
      boards: {},
      markSummary: null,
      markStatsLoading: false,
      markStatsError: false,
      requestId: 0,
      loading: false,
      error: null,
      onlyMyClass: (A.getSettings().onlyMyClass !== false)
    };
    App.date = App.date || A.todayIso();

    const rootEl = document.createElement('div');
    const controlsHost = document.createElement('div');
    const kpiHost = document.createElement('div');
    const marksStatsHost = document.createElement('div');
    const summaryHost = document.createElement('div');
    const boardsHost = document.createElement('div');
    rootEl.append(controlsHost, kpiHost, marksStatsHost, summaryHost, boardsHost);

    function student() { return ctx.student ? ctx.student() : null; }

    function renderControls() {
      controlsHost.innerHTML = '';
      const chipsWrap = document.createElement('div');
      chipsWrap.className = 'mb-2';
      const label = document.createElement('div');
      label.className = 'small text-secondary mb-1';
      label.textContent = 'Предметы';
      chipsWrap.appendChild(label);
      const chips = document.createElement('div');
      const opts = [{ value: 'all', label: 'Общий рейтинг' }].concat((state.subjects || []).map(s => ({ value: s.id, label: s.name, hint: s.average })));
      opts.forEach(o => {
        const b = document.createElement('button');
        b.className = 'chip' + (state.selected.includes(String(o.value)) ? ' active' : '');
        b.textContent = o.label + (o.hint != null ? ' ' + fmt(o.hint) : '');
        b.onclick = () => {
          const v = String(o.value);
          if (state.selected.includes(v)) {
            if (state.selected.length > 1) state.selected = state.selected.filter(x => x !== v);
          } else {
            state.selected.push(v);
          }
          if (!state.selected.length) state.selected = ['all'];
          load();
        };
        chips.appendChild(b);
      });
      chipsWrap.appendChild(chips);
      controlsHost.appendChild(chipsWrap);

      const row = document.createElement('div');
      row.className = 'd-flex flex-wrap gap-2 align-items-center mb-3';
      const dateLabel = document.createElement('span');
      dateLabel.className = 'small text-secondary';
      dateLabel.textContent = 'Снимок на дату:';
      row.appendChild(dateLabel);
      const dateInput = document.createElement('input');
      dateInput.type = 'date';
      dateInput.className = 'form-control form-control-sm';
      dateInput.style.width = 'auto';
      dateInput.value = state.date;
      dateInput.onchange = () => { state.date = dateInput.value || A.todayIso(); load(); };
      row.appendChild(dateInput);

      const presets = [
        { label: 'Сегодня', value: A.todayIso() },
        { label: '−7 дней', value: A.addDays(A.todayIso(), -7) },
        { label: '−30 дней', value: A.addDays(A.todayIso(), -30) },
        { label: '1 сент', value: schoolYearStart(A.todayIso()) }
      ];
      presets.forEach(p => {
        const b = document.createElement('button');
        b.className = 'btn btn-sm ' + (state.date === p.value ? 'btn-primary' : 'btn-outline-secondary');
        b.textContent = p.label;
        b.onclick = () => { state.date = p.value; load(); };
        row.appendChild(b);
      });

      const onlyMy = document.createElement('div');
      onlyMy.className = 'form-check ms-2';
      onlyMy.innerHTML = `<input class="form-check-input" type="checkbox" id="onlyMyClass" ${state.onlyMyClass ? 'checked' : ''}><label class="form-check-label small" for="onlyMyClass">Только мой класс</label>`;
      onlyMy.querySelector('input').onchange = (e) => {
        state.onlyMyClass = e.target.checked;
        A.setSettings(Object.assign(A.getSettings(), { onlyMyClass: e.target.checked }));
        if (state.onlyMyClass) load(); else render();
      };
      row.appendChild(onlyMy);

      const btnRefresh = document.createElement('button');
      btnRefresh.className = 'btn btn-sm btn-outline-secondary ms-auto';
      btnRefresh.innerHTML = '<i class="bi bi-arrow-clockwise"></i> Обновить';
      btnRefresh.onclick = () => load(true);
      row.appendChild(btnRefresh);

      const btnPrint = document.createElement('button');
      btnPrint.className = 'btn btn-sm btn-outline-secondary';
      btnPrint.innerHTML = '<i class="bi bi-printer"></i> Печать';
      btnPrint.onclick = () => window.print();
      row.appendChild(btnPrint);

      controlsHost.appendChild(row);
    }

    function renderKpis() {
      kpiHost.innerHTML = '';
      const primary = state.boards[subjectKey(state.selected[0])];
      if (!primary || !primary.summary) return;
      const s = primary.summary;
      const mine = s.myRow;
      const items = [
        { label: 'Моё место', value: s.myPlace ? '#' + s.myPlace : '—', sub: s.total ? 'из ' + s.total : '' },
        { label: 'Мой средний балл', value: fmt(s.myAverage), sub: mine && mine.averageDelta != null ? 'Δ ' + fmt(mine.averageDelta) : '', color: gradeColor(s.myAverage) },
        { label: 'Средний по классу', value: fmt(s.classAverage), sub: 'медиана ' + fmt(s.classMedian), color: gradeColor(s.classAverage) },
        { label: 'Лидер класса', value: fmt(s.leader ? s.leader.average : null), sub: s.leader ? s.leader.name : '' },
        { label: 'До лидера', value: s.gapToLeader == null ? '—' : fmt(s.gapToLeader), sub: s.gapToLeader === 0 ? 'вы лидер' : '' },
        { label: 'Впереди / позади', value: (s.aheadOfMe == null ? '—' : s.aheadOfMe) + ' / ' + (s.behindMe == null ? '—' : s.behindMe), sub: s.gapToNext == null ? '' : 'до соседа: ' + fmt(s.gapToNext) }
      ];
      const grid = document.createElement('div');
      grid.className = 'kpi-grid mb-3';
      items.forEach(it => {
        const card = document.createElement('div');
        card.className = 'stat-card';
        card.innerHTML = `<div class="small text-secondary">${it.label}</div><div class="stat-value ${it.color || ''}">${it.value}</div><div class="small text-secondary">${it.sub}</div>`;
        grid.appendChild(card);
      });
      kpiHost.appendChild(grid);
    }

    function renderMarkStatistics() {
      marksStatsHost.innerHTML = '';
      const card = document.createElement('section');
      card.className = 'card mb-3';
      const body = document.createElement('div');
      body.className = 'card-body';
      const title = document.createElement('h5');
      title.className = 'h6 mb-1';
      title.textContent = 'Статистика оценок';
      const selectedIds = state.selected.filter(id => String(id) !== 'all');
      const selectedNames = selectedIds.map(id => {
        const subject = state.subjects.find(item => String(item.id) === String(id));
        return subject ? subject.name : '';
      }).filter(Boolean);
      const scope = state.selected.includes('all') || !selectedIds.length
        ? 'все предметы'
        : selectedNames.length === 1 ? selectedNames[0] : 'выбранные предметы';
      const range = document.createElement('div');
      range.className = 'small text-secondary mb-2';
      range.textContent = 'Последние 30 дней · ' + dateRu(A.addDays(state.date, -29)) + ' — ' + dateRu(state.date) + ' · ' + scope;
      body.append(title, range);

      if (state.markStatsLoading) {
        const loading = document.createElement('div');
        loading.className = 'small text-secondary';
        loading.textContent = 'Загружаем статистику оценок…';
        body.appendChild(loading);
      } else if (state.markStatsError || !state.markSummary) {
        const unavailable = document.createElement('div');
        unavailable.className = 'small text-secondary';
        unavailable.textContent = 'Статистика оценок временно недоступна.';
        body.appendChild(unavailable);
      } else if (!state.markSummary.withValue) {
        const empty = document.createElement('div');
        empty.className = 'small text-secondary';
        empty.textContent = 'За выбранный период оценок нет.';
        body.appendChild(empty);
      } else {
        const summary = state.markSummary;
        const count = summary.withValue || 0;
        const positivePercent = summary.positiveShare == null ? '—' : Math.round(summary.positiveShare * 100) + '%';
        const metrics = [
          { label: 'Средний балл', value: fmt(summary.average), note: 'Среднее арифметическое' },
          { label: 'Взвешенное среднее', value: fmt(summary.weightedAverage), note: 'С учётом веса работы' },
          { label: 'Медиана', value: fmt(summary.median), note: 'Центральное значение' },
          { label: 'Доля положительных', value: positivePercent, note: (summary.positiveCount || 0) + ' из ' + count + ' оценок' },
          { label: 'Размах', value: fmt(summary.range), note: 'Максимум минус минимум' },
          { label: 'Стандартное отклонение', value: fmt(summary.standardDeviation), note: 'Разброс относительно среднего' }
        ];
        const grid = document.createElement('div');
        grid.className = 'kpi-grid rating-marks-kpi-grid';
        metrics.forEach(metric => {
          const item = document.createElement('div');
          item.className = 'stat-card rating-marks-stat-card';
          const label = document.createElement('div');
          label.className = 'small text-secondary';
          label.textContent = metric.label;
          const value = document.createElement('div');
          value.className = 'stat-value';
          value.textContent = metric.value;
          if (metric.label === 'Средний балл') value.classList.add(gradeColor(summary.average));
          const note = document.createElement('div');
          note.className = 'small text-secondary';
          note.textContent = metric.note;
          item.append(label, value, note);
          grid.appendChild(item);
        });
        body.appendChild(grid);
      }
      card.appendChild(body);
      marksStatsHost.appendChild(card);
    }

    function renderComparison() {
      const keys = state.selected.map(subjectKey);
      if (keys.length < 2) return null;
      const rows = keys.map(key => {
        const board = state.boards[key];
        if (!board) return null;
        const subject = (state.subjects || []).find(s => String(s.id) === String(key));
        return {
          name: key === 'all' ? 'Общий рейтинг' : (board.subjectName || (subject ? subject.name : key)),
          place: board.summary.myPlace,
          total: board.summary.total,
          myAvg: board.summary.myAverage,
          classAvg: board.summary.classAverage,
          gap: board.summary.gapToLeader,
          delta: board.summary.myDelta
        };
      }).filter(Boolean);
      const card = document.createElement('div');
      card.className = 'card mb-3';
      const body = document.createElement('div');
      body.className = 'card-body';
      const title = document.createElement('h5');
      title.textContent = 'Сравнение предметов';
      body.appendChild(title);
      const tbl = document.createElement('table');
      tbl.className = 'table table-sm table-hover';
      tbl.innerHTML = `<thead><tr><th>Предмет</th><th>Место</th><th>В классе</th><th>Мой балл</th><th>Класс</th><th>Δ к классу</th><th>До лидера</th><th>Δ за период</th></tr></thead>`;
      const tbody = document.createElement('tbody');
      rows.forEach(r => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${A.escapeHtml(r.name)}</td><td>${r.place ? '#' + r.place : '—'}</td><td>${r.total || '—'}</td><td><span class="${gradeColor(r.myAvg)}">${fmt(r.myAvg)}</span></td><td>${fmt(r.classAvg)}</td><td>${fmt((r.myAvg || 0) - (r.classAvg || 0))}</td><td>${r.gap == null ? '—' : fmt(r.gap)}</td><td>${r.delta == null ? '—' : fmt(r.delta)}</td>`;
        tbody.appendChild(tr);
      });
      tbl.appendChild(tbody);
      body.appendChild(tbl);
      const btn = document.createElement('button');
      btn.className = 'btn btn-sm btn-outline-secondary';
      btn.textContent = 'Закрыть сравнение';
      btn.onclick = () => { state.selected = [state.selected[0]]; render(); load(); };
      body.appendChild(btn);
      card.appendChild(body);
      return card;
    }

    function renderBoard(key, board) {
      const card = document.createElement('div');
      card.className = 'card mb-3';
      const body = document.createElement('div');
      body.className = 'card-body';
      const title = key === 'all' ? 'Общий рейтинг' : (board.subjectName || ('Предмет ' + key));
      const h5 = document.createElement('h5');
      h5.textContent = title;
      body.appendChild(h5);
      const sub = document.createElement('div');
      sub.className = 'small text-secondary mb-2';
      sub.textContent = 'на ' + dateRu(board.date) + ' · ' + (board.rows.length) + ' уч. · средний по классу ' + fmt(board.summary.classAverage);
      body.appendChild(sub);

      if (A.getSettings().showHistogram !== false && board.summary.histogram) {
        const hist = renderHistogram(board.summary.histogram);
        body.appendChild(hist);
      }

      const btns = document.createElement('div');
      btns.className = 'mb-2 d-flex gap-2';
      const btnCsv = document.createElement('button');
      btnCsv.className = 'btn btn-sm btn-outline-secondary';
      btnCsv.innerHTML = '<i class="bi bi-download"></i> Скачать таблицу';
      btnCsv.onclick = () => exportCsv(title, board);
      btns.appendChild(btnCsv);
      const btnCopy = document.createElement('button');
      btnCopy.className = 'btn btn-sm btn-outline-secondary';
      btnCopy.innerHTML = '<i class="bi bi-clipboard"></i> Копировать';
      btnCopy.onclick = () => copyTable(title, board);
      btns.appendChild(btnCopy);
      body.appendChild(btns);

      const tableWrap = document.createElement('div');
      tableWrap.className = 'table-responsive';
      const tbl = document.createElement('table');
      tbl.className = 'table table-sm table-hover';
      tbl.innerHTML = `<thead><tr><th>№</th><th>Ученик</th><th>Средний</th><th>Было</th><th>Δ</th><th>Уровень</th><th>Статус</th></tr></thead>`;
      const tbody = document.createElement('tbody');
      (board.rows || []).forEach(row => {
        const tr = document.createElement('tr');
        if (row.isMe) tr.classList.add('table-primary');
        const placeDelta = row.placeDelta ? (row.placeDelta > 0 ? '▲' + row.placeDelta : '▼' + Math.abs(row.placeDelta)) : '';
        const statusName = row.status === 'TOP' ? 'высокий' : row.status === 'MIDDLE' ? 'средний' : row.status === 'BOTTOM' ? 'ниже среднего' : '—';
        tr.innerHTML = `
          <td>${row.place == null ? '—' : row.place} <small class="text-secondary">${placeDelta}</small></td>
          <td>${row.isMe ? '<span class="badge bg-primary me-1">я</span>' : ''}${A.escapeHtml(row.name)}</td>
          <td><span class="${gradeColor(row.average)}">${fmt(row.average)}</span></td>
          <td class="text-secondary">${fmt(row.previousAverage)}</td>
          <td>${row.averageDelta == null ? '—' : fmt(row.averageDelta)}</td>
          <td><div class="bar" style="width:80px"><div class="bar-fill" style="width:${Math.round(((row.average || 0) / 5) * 100)}%"></div></div></td>
          <td><span class="badge ${row.status === 'TOP' ? 'bg-success' : row.status === 'MIDDLE' ? 'bg-secondary' : 'bg-warning'}">${statusName}</span></td>`;
        tr.style.cursor = 'pointer';
        tr.onclick = () => openStudentModal(row, board, title);
        tbody.appendChild(tr);
      });
      tbl.appendChild(tbody);
      tableWrap.appendChild(tbl);
      body.appendChild(tableWrap);
      card.appendChild(body);
      return card;
    }

    function renderHistogram(buckets) {
      const max = buckets.reduce((a, b) => Math.max(a, b.count || 0), 1);
      const wrap = document.createElement('div');
      wrap.className = 'mb-2';
      const t = document.createElement('div');
      t.className = 'small text-secondary mb-1';
      t.textContent = 'Распределение средних баллов класса';
      wrap.appendChild(t);
      const bars = document.createElement('div');
      bars.className = 'd-flex align-items-end gap-1';
      bars.style.height = '80px';
      buckets.forEach(bk => {
        const col = document.createElement('div');
        col.className = 'text-center';
        col.style.flex = '1';
        const fill = document.createElement('div');
        fill.style.height = Math.round(((bk.count || 0) / max) * 100) + '%';
        fill.style.minHeight = '2px';
        fill.className = 'bar-fill w-100';
        const cnt = document.createElement('div');
        cnt.className = 'small';
        cnt.textContent = bk.count || '';
        col.append(cnt, fill);
        const lbl = document.createElement('div');
        lbl.className = 'small text-secondary';
        lbl.textContent = (bk.label || '').slice(0, 3);
        col.appendChild(lbl);
        bars.appendChild(col);
      });
      wrap.appendChild(bars);
      return wrap;
    }

    function openStudentModal(row, board, title) {
      const summary = board.summary;
      const siblings = (board.rows || []).filter(item => Math.abs((item.place || 0) - (row.place || 0)) <= 2 && item.personId !== row.personId);
      const wrap = document.createElement('div');
      wrap.innerHTML = `
        <div class="modal fade" tabindex="-1">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title">${A.escapeHtml(row.name)}</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
              </div>
              <div class="modal-body">
                <div class="row g-2 mb-3">
                  <div class="col-6"><div class="stat-card"><div class="small text-secondary">Место</div><div class="stat-value">#${row.place || '—'} из ${summary.total || '—'}</div></div></div>
                  <div class="col-6"><div class="stat-card"><div class="small text-secondary">Средний балл</div><div class="stat-value ${gradeColor(row.average)}">${fmt(row.average)}</div></div></div>
                  <div class="col-6"><div class="stat-card"><div class="small text-secondary">Предыдущий</div><div class="stat-value">${fmt(row.previousAverage)}</div></div></div>
                  <div class="col-6"><div class="stat-card"><div class="small text-secondary">Δ балла</div><div class="stat-value">${row.averageDelta == null ? '—' : fmt(row.averageDelta)}</div></div></div>
                </div>
                <div class="small text-secondary mb-1">Ближайшие соседи по рейтингу</div>
                <ul class="list-group mb-2">
                  ${siblings.length ? siblings.map(it => `<li class="list-group-item bg-transparent">#${it.place} ${A.escapeHtml(it.name)} — ${fmt(it.average)}</li>`).join('') : '<li class="list-group-item bg-transparent text-secondary">совсем рядом никого нет</li>'}
                </ul>
                <div class="small text-secondary mb-1">Полоса относительно максимума класса (${fmt(summary.classMax)})</div>
                <div class="bar"><div class="bar-fill" style="width:${Math.round(((row.average || 0) / 5) * 100)}%"></div></div>
              </div>
            </div>
          </div>
        </div>`;
      document.body.appendChild(wrap);
      const modalEl = wrap.querySelector('.modal');
      const modal = new bootstrap.Modal(modalEl);
      modalEl.addEventListener('hidden.bs.modal', () => wrap.remove());
      modal.show();
    }

    function exportCsv(title, board) {
      const cols = ['Место', 'Δ места', 'Ученик', 'Средний', 'Было', 'Δ', 'Статус'];
      const lines = [cols.join(';')];
      (board.rows || []).forEach(r => {
        lines.push([r.place, r.placeDelta == null ? '' : r.placeDelta, '"' + (r.name || '').replace(/"/g, '""') + '"', r.average == null ? '' : String(r.average).replace('.', ','), r.previousAverage == null ? '' : String(r.previousAverage).replace('.', ','), r.averageDelta == null ? '' : String(r.averageDelta).replace('.', ','), r.status || ''].join(';'));
      });
      const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'рейтинг.csv'; a.click();
      URL.revokeObjectURL(url);
    }

    function copyTable(title, board) {
      const lines = (board.rows || []).map(r => `${r.place}\t${r.name}\t${fmt(r.average)}`).join('\n');
      navigator.clipboard.writeText(lines).then(() => A.toast('Скопировано', 'success')).catch(() => A.toast('Не удалось скопировать', 'error'));
    }

    function render() {
      renderControls();
      renderKpis();
      renderMarkStatistics();
      summaryHost.innerHTML = '';
      boardsHost.innerHTML = '';
      if (state.error) {
        boardsHost.innerHTML = `<div class="empty-state">${state.error}</div>`;
        return;
      }
      if (state.loading && Object.keys(state.boards).length === 0) {
        boardsHost.innerHTML = '<div class="text-secondary">Загрузка...</div>';
        return;
      }
      const comp = renderComparison();
      if (comp) summaryHost.appendChild(comp);
      state.selected.forEach(sid => {
        const key = subjectKey(sid);
        const board = state.boards[key];
        if (!board) {
          const sk = document.createElement('div');
          sk.className = 'text-secondary';
          sk.textContent = 'Загрузка...';
          boardsHost.appendChild(sk);
          return;
        }
        boardsHost.appendChild(renderBoard(key, board));
      });
    }

    function load(force) {
      const child = student();
      if (!child) {
        state.requestId++;
        state.markSummary = null;
        state.markStatsLoading = false;
        state.markStatsError = false;
        state.loading = false;
        state.error = 'Не выбран ученик — обновите профиль или войдите заново.';
        render();
        return Promise.resolve();
      }
      const requestId = ++state.requestId;
      state.loading = true;
      state.markSummary = null;
      state.markStatsLoading = true;
      state.markStatsError = false;
      state.error = null;
      render();
      let subjectsPromise = (state.subjects.length && !force)
        ? Promise.resolve()
        : A.api('/api/subjects?studentId=' + encodeURIComponent(child.id) + '&date=' + encodeURIComponent(state.date) + '&personId=' + encodeURIComponent(child.personId))
            .then(res => { if (requestId === state.requestId) state.subjects = res.subjects || []; });

      return subjectsPromise.then(() => {
        if (requestId !== state.requestId) return;
        const reqs = state.selected.map(sid => {
          const key = subjectKey(sid);
          const subject = (state.subjects || []).find(s => String(s.id) === String(sid));
          return A.api('/api/rank?studentId=' + encodeURIComponent(child.id) + '&personId=' + encodeURIComponent(child.personId) + '&date=' + encodeURIComponent(state.date) + (sid === 'all' ? '' : '&subjectId=' + encodeURIComponent(sid) + '&subjectName=' + encodeURIComponent(subject ? subject.name : '')))
            .then(board => { if (requestId === state.requestId) state.boards[key] = board; });
        });
        const from = A.addDays(state.date, -29);
        const marksUrl = '/api/marks?studentId=' + encodeURIComponent(child.id) +
          '&from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(state.date);
        const marksPromise = A.api(marksUrl).then(response => {
          if (requestId !== state.requestId) return;
          const marks = response.marks || [];
          const selectedIds = state.selected.filter(id => String(id) !== 'all').map(String);
          const selectedNames = (state.subjects || [])
            .filter(subject => selectedIds.includes(String(subject.id)))
            .map(subject => subject.name);
          const selectedMarks = state.selected.includes('all') || !selectedIds.length
            ? marks
            : marks.filter(mark => selectedIds.includes(String(mark.subjectId)) || selectedNames.includes(mark.subjectName));
          state.markSummary = window.MeshCore.summarizeMarks(selectedMarks);
          state.markStatsError = false;
        }).catch(() => {
          if (requestId === state.requestId) {
            state.markSummary = null;
            state.markStatsError = true;
          }
        });
        return Promise.all([...reqs, marksPromise]);
      }).catch(e => {
        if (requestId === state.requestId) {
          state.error = e.message || String(e);
          A.toast('Рейтинг не загрузился', 'error');
        }
      }).then(() => {
        if (requestId !== state.requestId) return;
        state.loading = false;
        state.markStatsLoading = false;
        const hasShort = state.selected.some(sid => {
          const b = state.boards[subjectKey(sid)];
          return b && (b.rows || []).some(r => String(r.name || '').indexOf('Ученик ') === 0);
        });
        if (hasShort && !state._nameToastShown) {
          state._nameToastShown = true;
          const err = window.__meshNameError || 'неизвестная ошибка (проверьте консоль)';
          A.toast('Имена не загрузились: ' + err, 'error');
        }
        render();
      });
    }

    const view = {
      el: rootEl,
      show: () => load(),
      hide: () => {},
      refresh: () => load(true),
      setDate: (iso) => { state.date = iso; return load(true); },
      selectSubject: (id) => { state.selected = [id == null || id === '' ? 'all' : String(id)]; return load(true); }
    };
    return view;
  }

  window.App.registerView('ranking', {
    title: 'Рейтинг',
    icon: 'bi-bar-chart-line',
    hint: 'Общий рейтинг класса и рейтинг по предметам',
    create: create
  });
})();