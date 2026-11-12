'use strict';
(function () {
  const A = window.AppAPI;
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const GRADE_COLORS = {
    5: '#34d399',
    4: '#fbbf24',
    3: '#fb923c',
    2: '#f87171',
    1: '#ef4444'
  };

  function fmt(value) {
    if (value === null || value === undefined || value === '' || !isFinite(Number(value))) return '—';
    return Number(value).toFixed(2).replace(/\.?0+$/, '').replace('.', ',');
  }

  function gradeColor(value) {
    if (value == null || !isFinite(Number(value))) return '';
    if (Number(value) >= 4.5) return 'text-success';
    if (Number(value) >= 3.5) return 'text-warning';
    return 'text-danger';
  }

  function create(ctx) {
    const state = {
      from: A.addDays(A.todayIso(), -29),
      to: A.todayIso(),
      marks: [],
      periodSubjects: [],
      subject: '',
      period: '',
      onlyMarked: false,
      showCharts: true,
      loading: false,
      error: null,
      requestId: 0
    };
    const rootEl = document.createElement('div');
    const host = document.createElement('div');
    rootEl.appendChild(host);

    function student() { return ctx.student ? ctx.student() : null; }

    function selectedMarks() {
      return state.marks.filter(mark => {
        if (state.subject && mark.subjectName !== state.subject) return false;
        if (state.period && mark.periodName !== state.period) return false;
        if (state.onlyMarked && (mark.value === null || mark.value === undefined)) return false;
        return true;
      });
    }

    function summarize(marks) {
      if (window.MeshCore && window.MeshCore.summarizeMarks) return window.MeshCore.summarizeMarks(marks);
      return { count: marks.length, withValue: marks.filter(m => m.value != null).length, bySubject: [], byValue: [] };
    }

    function makeSelect(label, values, selected, onChange) {
      const wrap = document.createElement('label');
      wrap.className = 'marks-filter';
      const caption = document.createElement('span');
      caption.className = 'small text-secondary';
      caption.textContent = label;
      const select = document.createElement('select');
      select.className = 'form-select form-select-sm';
      values.forEach(item => {
        const option = document.createElement('option');
        option.value = item.value;
        option.textContent = item.label;
        select.appendChild(option);
      });
      select.value = selected;
      select.onchange = () => onChange(select.value);
      wrap.append(caption, select);
      return wrap;
    }

    function makeDateFilter(label, value, onChange) {
      const wrap = document.createElement('label');
      wrap.className = 'marks-filter marks-date-filter';
      const caption = document.createElement('span');
      caption.className = 'small text-secondary';
      caption.textContent = label;
      const input = document.createElement('input');
      input.type = 'date';
      input.className = 'form-control form-control-sm';
      input.value = value;
      input.onchange = () => onChange(input.value);
      wrap.append(caption, input);
      return wrap;
    }

    function render() {
      host.innerHTML = '';
      const title = document.createElement('h4');
      title.className = 'view-title';
      title.textContent = 'Оценки';
      host.appendChild(title);

      const subjects = [...new Set(state.marks.map(mark => mark.subjectName).filter(Boolean))]
        .sort((a, b) => a.localeCompare(b, 'ru'));
      const periods = [...new Set(state.marks.map(mark => mark.periodName).filter(Boolean))]
        .sort((a, b) => a.localeCompare(b, 'ru'));
      const filters = document.createElement('div');
      filters.className = 'marks-filters mb-3';
      filters.append(
        makeDateFilter('С даты', state.from, value => { state.from = value; load(); }),
        makeDateFilter('По дату', state.to, value => { state.to = value; load(); }),
        makeSelect('Предмет', [{ value: '', label: 'Все предметы' }, ...subjects.map(value => ({ value, label: value }))], state.subject, value => {
          state.subject = value;
          render();
        }),
        makeSelect('Учебный период', [{ value: '', label: 'Все периоды' }, ...periods.map(value => ({ value, label: value }))], state.period, value => {
          state.period = value;
          render();
        })
      );

      const onlyMarked = document.createElement('label');
      onlyMarked.className = 'form-check marks-checkbox';
      const check = document.createElement('input');
      check.className = 'form-check-input';
      check.type = 'checkbox';
      check.checked = state.onlyMarked;
      check.onchange = () => { state.onlyMarked = check.checked; render(); };
      const checkText = document.createElement('span');
      checkText.className = 'form-check-label small';
      checkText.textContent = 'Только записи с оценкой';
      onlyMarked.append(check, checkText);
      filters.appendChild(onlyMarked);

      const chartToggle = document.createElement('button');
      chartToggle.type = 'button';
      chartToggle.className = 'btn btn-sm ' + (state.showCharts ? 'btn-primary' : 'btn-outline-secondary');
      chartToggle.setAttribute('aria-expanded', String(state.showCharts));
      chartToggle.textContent = state.showCharts ? 'Скрыть графики' : 'Показать графики';
      chartToggle.onclick = () => { state.showCharts = !state.showCharts; render(); };
      filters.appendChild(chartToggle);
      host.appendChild(filters);

      if (state.loading) {
        const message = document.createElement('div');
        message.className = 'empty-state';
        message.textContent = 'Загружаем оценки…';
        host.appendChild(message);
        return;
      }
      if (state.error) {
        const message = document.createElement('div');
        message.className = 'empty-state text-danger';
        message.textContent = state.error;
        host.appendChild(message);
        return;
      }

      const marks = selectedMarks();
      const summary = summarize(marks);
      renderStatistics(summary, host);
      renderSubjectSummary(marks, host);
      if (state.showCharts) renderCharts(marks, summary, host);
      renderTable(marks, host);
      if (!state.marks.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-state';
        empty.textContent = 'За выбранный период оценок нет.';
        host.appendChild(empty);
      }
    }

    function renderStatistics(summary, target) {
      const positivePercent = summary.positiveShare == null ? '—' : Math.round(summary.positiveShare * 100) + '%';
      const items = [
        { label: 'С оценкой', value: summary.withValue || 0, note: summary.withoutValue ? 'Без оценки: ' + summary.withoutValue : 'За выбранный период' },
        { label: 'Средний балл', value: fmt(summary.average), note: 'Взвешенный: ' + fmt(summary.weightedAverage), color: gradeColor(summary.average) },
        { label: 'Медиана', value: fmt(summary.median), note: 'Середина всех оценок' },
        { label: 'Положительных', value: (summary.positiveCount || 0) + ' · ' + positivePercent, note: 'Оценки от 3 и выше' },
        { label: 'Пятёрок', value: summary.fivesCount || 0, note: 'Из ' + (summary.withValue || 0) + ' оценок' },
        { label: 'Максимум / минимум', value: fmt(summary.best) + ' / ' + fmt(summary.worst), note: 'Размах: ' + fmt(summary.range) + ' · стандартное отклонение: ' + fmt(summary.standardDeviation) }
      ];
      const grid = document.createElement('div');
      grid.className = 'kpi-grid marks-kpi-grid mb-3';
      items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'stat-card marks-stat-card';
        const label = document.createElement('div');
        label.className = 'small text-secondary';
        label.textContent = item.label;
        const value = document.createElement('div');
        value.className = 'stat-value ' + (item.color || '');
        value.textContent = item.value;
        const note = document.createElement('div');
        note.className = 'small text-secondary';
        note.textContent = item.note;
        card.append(label, value, note);
        grid.appendChild(card);
      });
      target.appendChild(grid);
    }

    function renderSubjectSummary(marks, target) {
      const summary = summarize(marks);
      const groups = summary.bySubject || [];
      if (!groups.length) return;
      const card = document.createElement('section');
      card.className = 'card mb-3';
      const body = document.createElement('div');
      body.className = 'card-body';
      const heading = document.createElement('h5');
      heading.className = 'h6';
      heading.textContent = 'Статистика по предметам';
      body.appendChild(heading);
      const wrap = document.createElement('div');
      wrap.className = 'table-responsive';
      const table = document.createElement('table');
      table.className = 'table table-sm table-hover mb-0';
      table.innerHTML = '<thead><tr><th>Предмет</th><th>Оценок</th><th>Средний балл</th><th>Медиана</th><th>Лучшая / худшая</th></tr></thead>';
      const tbody = document.createElement('tbody');
      groups.forEach(group => {
        const row = document.createElement('tr');
        const cells = [
          group.subjectName || '—',
          String(group.count || 0),
          fmt(group.average),
          fmt(group.median),
          fmt(group.best) + ' / ' + fmt(group.worst)
        ];
        cells.forEach((text, index) => {
          const cell = document.createElement('td');
          cell.textContent = text;
          if (index === 2) cell.className = gradeColor(group.average);
          row.appendChild(cell);
        });
        tbody.appendChild(row);
      });
      table.appendChild(tbody);
      wrap.appendChild(table);
      body.appendChild(wrap);
      card.appendChild(body);
      target.appendChild(card);
    }

    function svgElement(tag, attributes) {
      const element = document.createElementNS(SVG_NS, tag);
      Object.keys(attributes || {}).forEach(key => element.setAttribute(key, attributes[key]));
      return element;
    }

    function svgText(svg, text, x, y, attributes) {
      const node = svgElement('text', Object.assign({ x, y }, attributes || {}));
      node.textContent = text;
      svg.appendChild(node);
      return node;
    }

    function chartCard(titleText, description, chart) {
      const card = document.createElement('section');
      card.className = 'card marks-chart-card';
      const body = document.createElement('div');
      body.className = 'card-body';
      const title = document.createElement('h5');
      title.className = 'h6 mb-1';
      title.textContent = titleText;
      const note = document.createElement('p');
      note.className = 'small text-secondary mb-2';
      note.textContent = description;
      body.append(title, note, chart);
      card.appendChild(body);
      return card;
    }

    function emptyChart(text) {
      const empty = document.createElement('div');
      empty.className = 'marks-chart-empty';
      empty.textContent = text;
      return empty;
    }

    function renderCharts(marks, summary, target) {
      const section = document.createElement('section');
      section.className = 'marks-charts mb-3';
      const heading = document.createElement('h5');
      heading.className = 'h6 mb-2';
      heading.textContent = 'Оценки на графиках';
      section.appendChild(heading);
      const grid = document.createElement('div');
      grid.className = 'marks-chart-grid';
      grid.append(
        chartCard('Распределение оценок', 'Сколько раз встречалась каждая оценка.', distributionChart(summary)),
        chartCard('Динамика по неделям', 'Средний балл за каждую неделю выбранного периода.', trendChart(marks)),
        chartCard('Средний балл по предметам', 'Показаны до восьми предметов с наибольшим средним баллом.', subjectChart(summary))
      );
      section.appendChild(grid);
      target.appendChild(section);
    }

    function distributionChart(summary) {
      const values = summary.byValue || [];
      const total = values.reduce((sum, item) => sum + (Number(item.count) || 0), 0);
      if (!total) return emptyChart('Недостаточно данных для построения графика.');
      const svg = svgElement('svg', { viewBox: '0 0 560 190', role: 'img', 'aria-label': 'Распределение оценок по количеству' });
      const baseline = 148;
      const chartHeight = 105;
      const max = Math.max(1, ...values.map(item => Number(item.count) || 0));
      svg.appendChild(svgElement('line', { x1: 34, y1: baseline, x2: 540, y2: baseline, class: 'marks-chart-axis' }));
      values.forEach((item, index) => {
        const x = 68 + index * 96;
        const count = Number(item.count) || 0;
        const height = count ? Math.max(3, chartHeight * count / max) : 0;
        const bar = svgElement('rect', {
          x: x - 22,
          y: baseline - height,
          width: 44,
          height: height,
          rx: 7,
          fill: GRADE_COLORS[item.value] || '#94a3b8',
          class: 'marks-chart-bar'
        });
        svg.appendChild(bar);
        svgText(svg, String(count), x, baseline - height - 8, { 'text-anchor': 'middle', class: 'marks-chart-count' });
        svgText(svg, String(item.value), x, baseline + 24, { 'text-anchor': 'middle', class: 'marks-chart-label' });
      });
      return svg;
    }

    function weekStart(iso) {
      const date = new Date(iso + 'T00:00:00Z');
      if (!isFinite(date.getTime())) return null;
      const day = (date.getUTCDay() + 6) % 7;
      date.setUTCDate(date.getUTCDate() - day);
      return date.toISOString().slice(0, 10);
    }

    function trendPoints(marks) {
      const weeks = new Map();
      marks.forEach(mark => {
        if (mark.value === null || mark.value === undefined || !mark.date) return;
        const key = weekStart(mark.date);
        if (!key) return;
        if (!weeks.has(key)) weeks.set(key, []);
        weeks.get(key).push(Number(mark.value));
      });
      return [...weeks.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([date, values]) => ({
        date,
        average: values.reduce((sum, value) => sum + value, 0) / values.length
      })).slice(-10);
    }

    function shortDate(iso) {
      return iso.slice(8, 10) + '.' + iso.slice(5, 7);
    }

    function trendChart(marks) {
      const points = trendPoints(marks);
      if (!points.length) return emptyChart('За выбранный период нет оценок для графика.');
      const svg = svgElement('svg', { viewBox: '0 0 600 230', role: 'img', 'aria-label': 'Динамика среднего балла по неделям' });
      const left = 48;
      const right = 584;
      const top = 16;
      const bottom = 176;
      const xAt = index => points.length === 1 ? (left + right) / 2 : left + index * (right - left) / (points.length - 1);
      const yAt = value => bottom - (Math.max(2, Math.min(5, value)) - 2) / 3 * (bottom - top);
      [2, 3, 4, 5].forEach(value => {
        const y = yAt(value);
        svg.appendChild(svgElement('line', { x1: left, y1: y, x2: right, y2: y, class: 'marks-chart-gridline' }));
        svgText(svg, String(value), left - 14, y + 4, { 'text-anchor': 'end', class: 'marks-chart-tick' });
      });
      svg.appendChild(svgElement('line', { x1: left, y1: bottom, x2: right, y2: bottom, class: 'marks-chart-axis' }));
      const path = points.map((point, index) => (index ? 'L' : 'M') + xAt(index) + ' ' + yAt(point.average)).join(' ');
      svg.appendChild(svgElement('path', { d: path, class: 'marks-trend-line' }));
      points.forEach((point, index) => {
        const circle = svgElement('circle', { cx: xAt(index), cy: yAt(point.average), r: 5, class: 'marks-trend-point' });
        const title = svgElement('title');
        title.textContent = shortDate(point.date) + ': ' + fmt(point.average);
        circle.appendChild(title);
        svg.appendChild(circle);
        if (points.length <= 6 || index === 0 || index === points.length - 1 || index % Math.ceil(points.length / 5) === 0) {
          svgText(svg, shortDate(point.date), xAt(index), bottom + 23, { 'text-anchor': 'middle', class: 'marks-chart-tick' });
        }
        svgText(svg, fmt(point.average), xAt(index), yAt(point.average) - 10, { 'text-anchor': 'middle', class: 'marks-chart-value' });
      });
      return svg;
    }

    function subjectChart(summary) {
      const groups = (summary.bySubject || []).filter(group => group.average != null).slice(0, 8);
      if (!groups.length) return emptyChart('Недостаточно данных по предметам.');
      const rowHeight = 34;
      const height = Math.max(130, 30 + groups.length * rowHeight);
      const svg = svgElement('svg', { viewBox: '0 0 660 ' + height, role: 'img', 'aria-label': 'Средний балл по предметам' });
      const barLeft = 210;
      const barWidth = 360;
      groups.forEach((group, index) => {
        const y = 17 + index * rowHeight;
        const average = Number(group.average) || 0;
        const name = String(group.subjectName || 'Предмет');
        const label = svgText(svg, name.length > 25 ? name.slice(0, 24) + '…' : name, barLeft - 12, y + 14, {
          'text-anchor': 'end',
          class: 'marks-chart-label'
        });
        const title = svgElement('title');
        title.textContent = name;
        label.appendChild(title);
        svg.appendChild(svgElement('rect', { x: barLeft, y, width: barWidth, height: 19, rx: 6, class: 'marks-subject-track' }));
        svg.appendChild(svgElement('rect', {
          x: barLeft,
          y,
          width: Math.max(2, barWidth * Math.max(0, Math.min(5, average)) / 5),
          height: 19,
          rx: 6,
          fill: average >= 4.5 ? GRADE_COLORS[5] : average >= 3.5 ? GRADE_COLORS[4] : average >= 2.5 ? GRADE_COLORS[3] : GRADE_COLORS[2]
        }));
        svgText(svg, fmt(average), barLeft + barWidth + 14, y + 14, { class: 'marks-chart-value' });
      });
      return svg;
    }

    function renderTable(marks, target) {
      const wrap = document.createElement('div');
      wrap.className = 'table-responsive';
      const table = document.createElement('table');
      table.className = 'table table-sm table-hover marks-table';
      table.innerHTML = '<thead><tr><th>Дата</th><th>Предмет</th><th>Вид работы</th><th>Оценка</th><th>Учебный период</th><th>Комментарий</th></tr></thead>';
      const tbody = document.createElement('tbody');
      const ordered = marks.slice().sort((a, b) => (b.date || '').localeCompare(a.date || ''));
      if (!ordered.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 6;
        cell.className = 'text-center text-secondary py-4';
        cell.textContent = 'Нет оценок, подходящих под выбранные фильтры.';
        row.appendChild(cell);
        tbody.appendChild(row);
      }
      ordered.forEach(mark => {
        const row = document.createElement('tr');
        const date = document.createElement('td');
        date.textContent = formatDate(mark.date);
        const subject = document.createElement('td');
        subject.textContent = mark.subjectName || '—';
        const kind = document.createElement('td');
        kind.textContent = mark.controlForm && mark.controlForm !== '—' ? mark.controlForm : '—';
        const grade = document.createElement('td');
        grade.className = gradeColor(mark.value);
        grade.textContent = mark.value == null ? '—' : fmt(mark.value);
        const term = document.createElement('td');
        term.textContent = mark.periodName || 'Период не определён';
        if (mark.periodApproximate) {
          term.classList.add('marks-period-approximate');
          term.title = mark.periodName && mark.periodName.indexOf('ориентировочно') !== -1
            ? 'Период рассчитан приблизительно по дате: точные границы школы не получены.'
            : 'Точный учебный период не удалось определить по данным МЭШ.';
        }
        const comment = document.createElement('td');
        comment.className = 'small text-secondary';
        comment.textContent = mark.comment || '—';
        row.append(date, subject, kind, grade, term, comment);
        tbody.appendChild(row);
      });
      table.appendChild(tbody);
      wrap.appendChild(table);
      target.appendChild(wrap);
    }

    function formatDate(value) {
      if (!value) return '—';
      const date = new Date(value + 'T00:00:00');
      return isFinite(date.getTime()) ? date.toLocaleDateString('ru-RU') : value;
    }

    function load() {
      const child = student();
      if (!child) {
        state.error = 'Не выбран ученик';
        state.loading = false;
        render();
        return Promise.resolve();
      }
      const requestId = ++state.requestId;
      state.loading = true;
      state.error = null;
      render();
      const marksUrl = '/api/marks?studentId=' + encodeURIComponent(child.id) +
        '&from=' + encodeURIComponent(state.from) + '&to=' + encodeURIComponent(state.to);
      const periodsUrl = '/api/subject-marks?studentId=' + encodeURIComponent(child.id);
      return Promise.all([
        A.api(marksUrl),
        A.api(periodsUrl).catch(() => ({ marks: [] }))
      ]).then(([markResponse, periodResponse]) => {
        if (requestId !== state.requestId) return;
        state.periodSubjects = periodResponse.marks || [];
        const resolver = window.MeshCore && window.MeshCore.resolveMarkPeriod;
        state.marks = (markResponse.marks || []).map(mark => {
          if (!resolver) return Object.assign({ periodName: 'Период не определён', periodApproximate: false }, mark);
          const resolved = resolver(mark, state.periodSubjects);
          return Object.assign({}, mark, {
            periodName: resolved.name,
            periodApproximate: resolved.approximate
          });
        });
        if (state.subject && !state.marks.some(mark => mark.subjectName === state.subject)) state.subject = '';
        if (state.period && !state.marks.some(mark => mark.periodName === state.period)) state.period = '';
      }).catch(error => {
        if (requestId === state.requestId) {
          state.error = error.message || 'Не удалось загрузить оценки.';
          A.toast('Не удалось загрузить оценки', 'error');
        }
      }).then(() => {
        if (requestId === state.requestId) {
          state.loading = false;
          render();
        }
      });
    }

    return { el: rootEl, show: () => load(), hide: () => {}, refresh: () => load() };
  }

  window.App.registerView('marks', {
    title: 'Оценки',
    icon: 'bi-journal-check',
    hint: 'Статистика и учебные периоды оценок',
    create
  });
})();
