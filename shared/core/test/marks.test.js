'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const MeshCore = require('../mesh-core.js');

test('учебный период берётся из границ, полученных от МЭШ', () => {
  const subjects = MeshCore.normalizeSubjectMarks([
    {
      subject_id: 21,
      subject_name: 'Алгебра',
      periods: [{
        title: 'Первая четверть',
        start: '01.09',
        end: '23.10',
        marks: [{ id: 10, value: 5, date: '2026-09-05', control_form_name: 'Тест' }]
      }]
    }
  ]);
  const mark = MeshCore.normalizeMark({
    id: 11,
    value: 4,
    subject_id: 21,
    subject_name: 'Алгебра',
    date: '2026-10-20'
  });

  assert.deepEqual(MeshCore.resolveMarkPeriod(mark, subjects), {
    name: 'Первая четверть',
    approximate: false
  });
});

test('период можно сопоставить по дате и отметке, даже если идентификаторы различаются', () => {
  const subjects = MeshCore.normalizeSubjectMarks([
    {
      subject_id: 21,
      subject_name: 'Алгебра',
      periods: [{
        title: 'Первый триместр',
        marks: [{ id: 10, value: 4, date: '2026-09-12', control_form_name: 'Тест' }]
      }]
    }
  ]);
  const mark = MeshCore.normalizeMark({
    id: 99,
    value: 4,
    subject_id: 21,
    subject_name: 'Алгебра',
    date: '2026-09-12',
    control_form_name: 'Тест'
  });

  assert.deepEqual(MeshCore.resolveMarkPeriod(mark, subjects), {
    name: 'Первый триместр',
    approximate: false
  });
});

test('если границы периода не пришли, подпись помечается как приблизительная', () => {
  const mark = MeshCore.normalizeMark({ value: 5, date: '2026-11-12' });
  assert.deepEqual(MeshCore.resolveMarkPeriod(mark, []), {
    name: '2-я четверть (ориентировочно)',
    approximate: true
  });
});

test('сводка содержит среднее, медиану, долю положительных оценок и распределение', () => {
  const marks = [
    MeshCore.normalizeMark({ value: 5, weight: 1, subject_id: 1, subject_name: 'Алгебра', date: '2026-10-01' }),
    MeshCore.normalizeMark({ value: 4, weight: 3, subject_id: 1, subject_name: 'Алгебра', date: '2026-10-02' }),
    MeshCore.normalizeMark({ value: 3, weight: 1, subject_id: 2, subject_name: 'Физика', date: '2026-10-03' })
  ];
  const summary = MeshCore.summarizeMarks(marks);

  assert.equal(summary.count, 3);
  assert.equal(summary.withValue, 3);
  assert.equal(summary.average, 4);
  assert.equal(summary.weightedAverage, 4);
  assert.equal(summary.median, 4);
  assert.equal(summary.positiveCount, 3);
  assert.equal(summary.positiveShare, 1);
  assert.equal(summary.fivesCount, 1);
  assert.equal(summary.range, 2);
  assert.equal(summary.bySubject.length, 2);
});
